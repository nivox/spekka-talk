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


trait AppSkeleton[M] {
  val kafkaServer = Option(System.getenv("KAFKA_SERVER"))
    .getOrElse(throw new IllegalArgumentException("KAFKA_SERVER not specified"))

  val kafkaTopic = Option(System.getenv("KAFKA_INPUT_TOPIC"))
    .getOrElse("readings")

  def processingFlow[Offset]
      : Flow[(EntranceCounterReading, Offset), Offset, M]

  def route[M](materializedValue: M): Route

  def main(args: Array[String]): Unit = {
    try {
      implicit val system = ActorSystem("app")
      import scala.concurrent.ExecutionContext.Implicits.global

      val consumerSettings = ConsumerSettings[String, String](
        system,
        new StringDeserializer,
        new StringDeserializer
      ).withBootstrapServers(kafkaServer)
        .withGroupId("app")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

      val committerSettings = CommitterSettings(system)

      val (appM, done) = Consumer
        .committableSource(consumerSettings, Subscriptions.topics(Set(kafkaTopic)))
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
        .viaMat(processingFlow)(Keep.right)
        .toMat(Committer.sink(committerSettings))(Keep.both)
        .run()

      val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(route(appM))
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
