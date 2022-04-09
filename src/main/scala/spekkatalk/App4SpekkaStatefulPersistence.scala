package spekkatalk

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.stream.javadsl.FlowWithContext
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import io.circe.generic._
import spekka.codec.Codec
import spekka.codec.Decoder
import spekka.codec.Encoder
import spekka.context.FlowWithExtendedContext
import spekka.context.Partition
import spekka.context.PartitionTree
import spekka.context.PartitionTree.PartitionControl.DynamicControl
import spekka.context.PartitionTree.PartitionControl.StaticControl
import spekka.stateful.AkkaPersistenceStatefulFlowBackend
import spekka.stateful.InMemoryStatefulFlowBackend
import spekka.stateful.StatefulFlowBuilder
import spekka.stateful.StatefulFlowControl
import spekka.stateful.StatefulFlowLogic
import spekka.stateful.StatefulFlowLogic.EventBased.ProcessingResult
import spekka.stateful.StatefulFlowRegistry
import spekkatalk.EntranceCounterReading

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

case class App4Env(
    registry: StatefulFlowRegistry,
    byDeploymentBuilder: StatefulFlowBuilder[
      EntranceCounterReading,
      App4SpekkaStatefulPersistence.CounterIncremented,
      App4SpekkaStatefulPersistence.GetCounter
    ],
    byEntranceBuilder: StatefulFlowBuilder[
      EntranceCounterReading,
      App4SpekkaStatefulPersistence.CounterIncremented,
      App4SpekkaStatefulPersistence.GetCounter
    ]
)

object App4SpekkaStatefulPersistence extends AppSkeleton[App4Env] {

  type M = NotUsed

  val consumerGroup = "stateful-persistence"

  case class State(counter: Long)
  implicit val stateJsonCodec = io.circe.generic.semiauto.deriveCodec[State]
  implicit val stateSpekkaCodec = Utils.deriveSpekkaCodec[State]

  case class CounterIncremented(n: Int)
  implicit val counterIncrementedCodec =
    io.circe.generic.semiauto.deriveCodec[CounterIncremented]
  implicit val counterIncrementedSpekkaCodec =
    Utils.deriveSpekkaCodec[CounterIncremented]

  case class GetCounter(replyTo: ActorRef[StatusReply[Long]])

  override def init()(implicit system: ActorSystem): App4Env = {
    val registry = StatefulFlowRegistry(30.second)

    def logic(prefixF: EntranceCounterReading => String) =
      StatefulFlowLogic.EventBased[
        State,
        CounterIncremented,
        EntranceCounterReading,
        GetCounter
      ](
        () => State(0),
        (state, in) => {
          ProcessingResult
            .withEvent(CounterIncremented(in.counter))
            .withBeforeUpdateSideEffect { () =>
              println(
                s"${Utils.prettyPrintTimestamp(in.timestamp)} - ${prefixF(in)} total entrances: ${state.counter + in.counter}"
              )
              Future.successful(())
            }
        },
        (state, ev) => State(state.counter + ev.n),
        (state, command) => {
          command.replyTo ! StatusReply.success(state.counter)
          ProcessingResult.empty
        }
      )

    val backend = AkkaPersistenceStatefulFlowBackend
      .EventBased[State, CounterIncremented](
        AkkaPersistenceStatefulFlowBackend.EventBased.PersistencePlugin.CassandraStoragePlugin
      )
      .withEventCodec
      .withSnapshotCodec

    val byDeploymentProps =
      logic(e => s"deployment ${e.deploymentId}").propsForBackend(backend)
    val byEntranceProps = logic(e =>
      s"deployment ${e.deploymentId} entrance ${e.entranceId}"
    ).propsForBackend(backend)

    val byDeploymentBuilder =
      registry.registerStatefulFlowSync("byDeployment", byDeploymentProps)
    val byEntranceBuilder =
      registry.registerStatefulFlowSync("byEntrance", byEntranceProps)

    App4Env(registry, byDeploymentBuilder, byEntranceBuilder)
  }

  override def processingFlow[Offset](env: App4Env) = {
    import spekka.context.PartitionTree._

    val byDeploymentFlow = Partition
      .treeBuilder[EntranceCounterReading, Offset]
      .dynamicAuto(_.deploymentId)
      .build { case deploymentId :@: KNil =>
        env.byDeploymentBuilder.flowWithExtendedContext(deploymentId)
      }

    val byEntranceFlow = Partition
      .treeBuilder[EntranceCounterReading, Offset]
      .dynamicAuto(_.deploymentId)
      .dynamicAuto(_.entranceId)
      .build { case entranceId :@: deploymentId :@: KNil =>
        env.byEntranceBuilder.flowWithExtendedContext(
          s"${deploymentId}:${entranceId}"
        )
      }

    val combinedFlow = Partition
      .treeBuilder[EntranceCounterReading, Offset]
      .staticMulticast[String](
        { case (_, keys) => keys },
        Set("byDeployment", "byEntrance")
      )
      .build { case branch :@: KNil =>
        branch match {
          case "byDeployment" => byDeploymentFlow
          case "byEntrance"   => byEntranceFlow
        }
      }
      .ordered()

    Flow[(EntranceCounterReading, Offset)]
      .via(combinedFlow)
      .map(_._2)
  }

  override def route(env: App4Env, materializedValue: NotUsed): Route = {
    import akka.http.scaladsl.server.Directives._

    def getCounterForDeployment(d: String): Future[Option[Long]] = {
      for {
        maybeC <- env.byDeploymentBuilder.control(d)
        v <- maybeC match {
          case Some(c) => c.commandWithResult(GetCounter).map(Some(_))
          case None    => Future.successful(None)
        }
      } yield v
    }

    def getCounterForEntrance(d: String, e: Int): Future[Option[Long]] = {
      for {
        maybeC <- env.byEntranceBuilder.control(s"$d:$e")
        v <- maybeC match {
          case Some(c) => c.commandWithResult(GetCounter).map(Some(_))
          case None    => Future.successful(None)
        }
      } yield v
    }

    path("deployment" / Segment) { d =>
      onSuccess(getCounterForDeployment(d)) {
        case Some(counter) => complete(StatusCodes.OK -> counter.toString())
        case None =>
          complete(StatusCodes.NotFound -> s"Deployment $d not found")
      }
    } ~ path("deployment" / Segment / "entrance" / IntNumber) { (d, e) =>
      onSuccess(getCounterForEntrance(d, e)) {
        case Some(counter) => complete(StatusCodes.OK -> counter.toString())
        case None =>
          complete(
            StatusCodes.NotFound -> s"Deployment $d entrance $e not found"
          )
      }
    }
  }

}
