package spekkatalk

import akka.NotUsed
import akka.stream.scaladsl.Flow
import spekkatalk.EntranceCounterReading
import akka.http.scaladsl.server.Route
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem

object App1PlainFlow extends AppSkeleton[NotUsed, NotUsed] {

  val consumerGroup = "plain"

  override def init()(implicit system: ActorSystem): NotUsed = NotUsed

  def baseFlow[Offset] = {
    val unsafeCounterContainer = new AtomicLong(0)
    def getCounter(): Long = unsafeCounterContainer.get()

    Flow[(EntranceCounterReading, Offset)]
      .statefulMapConcat { () =>
        var entranceCounter = 0

        { case (e, offset) =>
          entranceCounter += e.counter
          unsafeCounterContainer.set(entranceCounter)
          println(
            s"${Utils.prettyPrintTimestamp(e.timestamp)} - deployment ${e.deploymentId} total entrances: ${entranceCounter}"
          )
          List(offset)
        }
      }
      .mapMaterializedValue(_ => () => getCounter())
  }

  override def processingFlow[Offset](env: NotUsed) = {
    Flow[(EntranceCounterReading, Offset)]
      .groupBy(Int.MaxValue, _._1.deploymentId)
      .via(baseFlow)
      .mergeSubstreams
  }

  override def route(env: NotUsed, materializedValue: NotUsed): Route = {
    import akka.http.scaladsl.server.Directives._

    reject
  }
}
