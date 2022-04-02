package spekkatalk

import akka.NotUsed
import akka.stream.scaladsl.Flow
import spekkatalk.EntranceCounterReading
import akka.http.scaladsl.server.Route
import spekka.context.FlowWithExtendedContext
import java.util.concurrent.atomic.AtomicLong
import akka.stream.javadsl.FlowWithContext
import spekka.context.Partition
import spekka.context.PartitionTree
import spekka.context.PartitionTree.PartitionControl.DynamicControl
import spekka.context.PartitionTree.PartitionControl.StaticControl
import akka.stream.scaladsl.Keep
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem

object App2Model {
  sealed trait CombinedM
  case class ByDeployment(m: DynamicControl[String,() => Long]) extends CombinedM
  case class ByEntrance(m: DynamicControl[String,DynamicControl[Int,() => Long]]) extends CombinedM
}

object App2SpekkaContext extends AppSkeleton[NotUsed, StaticControl[String, App2Model.CombinedM]] {
  import App2Model._

  val consumerGroup = "context"

  override def init()(implicit system: ActorSystem): NotUsed = NotUsed

  def baseFlow[Offset](prefix: String) = {
    val unsafeCounterContainer = new AtomicLong(0)
    def getCounter(): Long = unsafeCounterContainer.get()

    FlowWithExtendedContext[EntranceCounterReading, Offset]
      .statefulMap { () =>
        var entranceCounter = 0

        { case e =>
          entranceCounter += e.counter
          unsafeCounterContainer.set(entranceCounter)
          println(
            s"${Utils.prettyPrintTimestamp(e.timestamp)} - $prefix total entrances: ${entranceCounter}"
          )
          
          ()
        }
      }
      .mapMaterializedValue(_ => () => getCounter())
  }

  

  override def processingFlow[Offset](env: NotUsed) = {
    import spekka.context.PartitionTree._

    val byDeploymentFlow = Partition.treeBuilder[EntranceCounterReading, Offset]
      .dynamicAuto(_.deploymentId)
      .build { case deploymentId :@: KNil => 
        baseFlow(s"deployment ${deploymentId}")
      }.mapMaterializedValue[CombinedM](ByDeployment(_))

    val byEntranceFlow = Partition.treeBuilder[EntranceCounterReading, Offset]
      .dynamicAuto(_.deploymentId)
      .dynamicAuto(_.entranceId)
      .build { case entranceId :@: deploymentId :@: KNil => 
        baseFlow(s"deployment $deploymentId entrance $entranceId")
      }.mapMaterializedValue[CombinedM](ByEntrance)

    val combinedFlow = Partition.treeBuilder[EntranceCounterReading, Offset]
    .staticMulticast[String]( { case (_, keys) => keys }, Set("byDeployment", "byEntrance"))
    .build { case branch :@: KNil =>
      branch match {
        case "byDeployment" => byDeploymentFlow
        case "byEntrance" => byEntranceFlow
      }
    }.ordered()

    Flow[(EntranceCounterReading, Offset)]
    .viaMat(combinedFlow)(Keep.right)
    .map(_._2)
  }

  override def route(env: NotUsed, materializedValue: StaticControl[String, CombinedM]): Route = {
      import akka.http.scaladsl.server.Directives._

      def getCounterForDeployment(d: String): Future[Option[Long]] = {
        (for {
          dC <- materializedValue.atKeyNarrowed[ByDeployment]("byDeployment")
          counterF <- dC.get.m.atKey(d)
        } yield counterF()).run
      }

      def getCounterForEntrance(d: String, e: Int): Future[Option[Long]] = {
        (for {
          eC <- materializedValue.atKeyNarrowed[ByEntrance]("byEntrance")
          entranceC <- eC.get.m.atKey(d)
          counterF <- entranceC.atKey(e)
        } yield counterF()).run
      }

      path("deployment" / Segment ) { d =>
        onSuccess(getCounterForDeployment(d)) {
          case Some(counter) => complete(StatusCodes.OK -> counter.toString())
          case None => complete(StatusCodes.NotFound -> s"Deployment $d not found")
        }
      } ~ path("deployment" / Segment / "entrance" / IntNumber) { (d, e) =>
        onSuccess(getCounterForEntrance(d, e)) {
          case Some(counter) => complete(StatusCodes.OK -> counter.toString())
          case None => complete(StatusCodes.NotFound -> s"Deployment $d entrance $e not found")
        }
      }
  }


}