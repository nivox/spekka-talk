package spekkatalk

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.NotUsed

import scala.concurrent.duration._
import scala.util.Random
import akka.actor.Cancellable
import akka.stream.scaladsl.Sink
import scala.concurrent.Await
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import akka.kafka.scaladsl.Producer
import akka.kafka.ProducerSettings
import org.apache.kafka.common.serialization.StringSerializer
import akka.stream.scaladsl.Flow
import akka.kafka.ProducerMessage
import org.apache.kafka.clients.producer.ProducerRecord
import akka.stream.scaladsl.Keep

case class DeploymentSpec(id: String, entrancesNr: Int, freq: Double)

object Pusher {

  lazy val kafkaServer = Option(System.getenv("KAFKA_SERVER"))
    .getOrElse(throw new IllegalArgumentException("KAFKA_SERVER not specified"))

  lazy val kafkaTopic = Option(System.getenv("KAFKA_OUTPUT_TOPIC"))
    .getOrElse("readings")

  lazy val duration = Option(System.getenv("DURATION_SEC"))
    .map { s =>
      s.toInt.second
    }
    .getOrElse(Duration.Inf)

  val deploymentSpecRe = """([^:]+):(\d+)@(\d+(?:\.\d+)?)""".r
  lazy val deploymentSpecMap: Map[String, DeploymentSpec] =
    Option(System.getenv("DEPLOYMENTS"))
      .map { specString =>
        specString
          .split(""",\s*""")
          .map { deploymentSpec =>
            deploymentSpec match {
              case deploymentSpecRe(deploymentId, entrancesNr, freq) =>
                DeploymentSpec(deploymentId, entrancesNr.toInt, freq.toDouble)
              case _ =>
                throw new IllegalArgumentException(
                  s"Deployment spec ${deploymentSpec} is invalid"
                )
            }
          }
          .map(s => s.id -> s)
          .toMap
      }
      .getOrElse(throw new IllegalArgumentException("DEPLOYMENTS not defined"))

  def generateReadingSource(
      spec: DeploymentSpec
  ): Source[EntranceCounterReading, Cancellable] = {
    Source
      .tick(0.seconds, 1.seconds, ())
      .statefulMapConcat(() => {
        var entrances: Double = 0
        var lastEntrance: Int = 0

        _ => {
          entrances += spec.freq

          if (entrances >= 1) {
            val toEmit = math.floor(entrances).toInt
            entrances -= toEmit
            val startEntrance = lastEntrance
            lastEntrance = (lastEntrance + toEmit) % spec.entrancesNr
            val timestamp = System.currentTimeMillis()

            Iterator
              .from(startEntrance)
              .map(e => (e % spec.entrancesNr))
              .take(toEmit)
              .toList
              .groupBy(identity)
              .view
              .mapValues(_.size)
              .map { case (entrance, n) =>
                EntranceCounterReading(timestamp, spec.id, entrance, n)
              }
              .toList
          } else Nil
        }
      })
  }

  def generateReadingSource(
      specs: Map[String, DeploymentSpec]
  ): Source[EntranceCounterReading, NotUsed] = {
    specs.valuesIterator.foldLeft(Source.never[EntranceCounterReading]) {
      case (agg, spec) =>
        agg.merge(generateReadingSource(spec))
    }
  }

  def main(args: Array[String]): Unit = {

    try {
      implicit val system = ActorSystem("pusher")

      println("Star pushing for deployments:")
      deploymentSpecMap.valuesIterator.foreach(s =>
        println(s" - ${s.id}: entrances=${s.entrancesNr} freq=${s.freq}Hz")
      )

      val producerSettings = ProducerSettings[String, String](
        system,
        new StringSerializer,
        new StringSerializer
      )
        .withBootstrapServers(kafkaServer)

      val kafkaProduceSink = Flow[EntranceCounterReading]
        .map { r =>
          import EntranceCounterReadingJsonCodecs.entranceCounterReadingCodec
          new ProducerRecord(
            kafkaTopic,
            r.deploymentId,
            entranceCounterReadingCodec(r).noSpaces
          )
        }
        .toMat(Producer.plainSink(producerSettings))(Keep.right)

      val done = generateReadingSource(deploymentSpecMap)
        .map { reading =>
          val ts = Instant.ofEpochMilli(reading.timestamp)
          val tsString = ts
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
          println(
            s"${tsString} - ${reading.deploymentId}: entrance=${reading.entranceId} count=${reading.counter}"
          )
          reading
        }
        .runWith(kafkaProduceSink)

      Await.result(done, duration)
    } catch {
      case e: Exception =>
        println("PUSHER FAILED!")
        e.printStackTrace()
    } finally {
      System.exit(1)
    }
  }

}
