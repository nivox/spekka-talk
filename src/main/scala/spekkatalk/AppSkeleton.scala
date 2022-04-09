package spekkatalk

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import akka.kafka.scaladsl.Consumer
import akka.kafka.Subscription
import akka.kafka.Subscriptions
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.kafka.scaladsl.Committer
import akka.kafka.CommitterSettings
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.util.Success
import scala.util.Failure
import org.apache.kafka.clients.consumer.ConsumerConfig
import akka.kafka.ConsumerMessage
import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import akka.kafka.scaladsl.PartitionAssignmentHandler
import org.apache.kafka.common.TopicPartition
import akka.kafka.RestrictedConsumer


trait AppSkeleton[E] {
  type M

  val kafkaServer = Option(System.getenv("KAFKA_SERVER"))
    .getOrElse(throw new IllegalArgumentException("KAFKA_SERVER not specified"))

  val kafkaTopic = Option(System.getenv("KAFKA_INPUT_TOPIC"))
    .getOrElse("readings")

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val config: Config = ConfigFactory.load()

  val consumerGroup: String

  def init()(implicit system: ActorSystem): E

  def processingFlow[Offset](env: E)
      : Flow[(EntranceCounterReading, Offset), Offset, M]

  def route(env: E, materializedValue: M): Route

  def main(args: Array[String]): Unit = {
    try {
      implicit val system = ActorSystem("app", config)

      val env = init()

      val consumerSettings = ConsumerSettings[String, String](
        system,
        new StringDeserializer,
        new StringDeserializer
      ).withBootstrapServers(kafkaServer)
        .withGroupId(consumerGroup)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

      val assignmentHandler = new PartitionAssignmentHandler {
        override def onRevoke(revokedTps: Set[TopicPartition], consumer: RestrictedConsumer): Unit =
          println(s"Kafka partition revoked: ${revokedTps.map(_.partition()).toList.sorted.mkString(", ")}")
        override def onAssign(assignedTps: Set[TopicPartition], consumer: RestrictedConsumer): Unit =
          println(s"Kafka partition assigned: ${assignedTps.map(_.partition()).toList.sorted.mkString(", ")}")
        override def onLost(lostTps: Set[TopicPartition], consumer: RestrictedConsumer): Unit =
          println(s"Kafka partition lost: ${lostTps.map(_.partition()).toList.sorted.mkString(", ")}")
        override def onStop(currentTps: Set[TopicPartition], consumer: RestrictedConsumer): Unit = 
        println(s"Kafka partition stop: ${currentTps.map(_.partition()).toList.sorted.mkString(", ")}")
      }

      val committerSettings = CommitterSettings(system)

      val (appM, done) = Consumer
        .committableSource(
          consumerSettings, 
          Subscriptions.topics(Set(kafkaTopic))
            .withPartitionAssignmentHandler(assignmentHandler)
        )
        .mapConcat { msg =>
          import io.circe._

          (for {
            json <- parser
              .parse(msg.record.value())
              .left
              .map(e => s"Invalid json data: ${e.message}")
            reading <- EntranceCounterReadingJsonCodecs
              .entranceCounterReadingCodec(json.hcursor)
              .left
              .map(e => s"Invalid reading json: ${e.message}")
          } yield reading) match {
            case Right(r) => List(r -> msg.committableOffset)
            case Left(err) =>
              println(
                s"Invalid reading at offset ${msg.committableOffset.partitionOffset}: $err"
              )
              Nil
          }
        }
        .viaMat(processingFlow(env))(Keep.right)
        .statefulMapConcat{ () =>
          val offsetMap = scala.collection.mutable.Map[ConsumerMessage.GroupTopicPartition, Long]()

          { 
            offset => 
              offsetMap.get(offset.partitionOffset.key) match {
                case Some(lastOffset) if offset.partitionOffset.offset > lastOffset + 1 => 
                  println(s"WARN Unordered offset commit detected (Kafka rebalance?)! Trying to commit offset ${offset.partitionOffset.offset} (previously committed ${lastOffset}) for ${offset.partitionOffset.key}")

                case Some(lastOffset) if offset.partitionOffset.offset <= lastOffset => 
                  println(s"WARN Duplicated offset detected (Kafka rebalance?)! Trying to commit offset ${offset.partitionOffset.offset} (previously committed ${lastOffset}) for ${offset.partitionOffset.key}")

                case _ =>
              }
              offsetMap += offset.partitionOffset.key -> offset.partitionOffset.offset
              
              List(offset)
          }
        }
        .toMat(Committer.sink(committerSettings))(Keep.both)
        .run()

      val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(route(env, appM))
      bindingFuture.onComplete {
        case Success(b) => 
          println(s"HTTP Server started on ${b.localAddress}")
        case Failure(e) =>
          println("ERROR starting HTTP Server")
          e.printStackTrace()
          System.exit(1)
      }


      Await.result(done, Duration.Inf)
    } catch {
      case e: Exception =>
        println("ERROR!")
        e.printStackTrace()
    } finally {
      System.exit(1)
    }

  }

}
