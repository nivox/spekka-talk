package spekkatalk

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import spekka.context.Partition
import spekka.context.FlowWithExtendedContext
import spekka.stateful.StatefulFlowRegistry

import scala.concurrent.duration._
import spekka.stateful.StatefulFlowBackend
import spekka.stateful.InMemoryStatefulFlowBackend
import scala.concurrent.Future

object App4 extends AppBase {
  import Model._
  import Data._
  import BaseFlows._

  import scala.concurrent.ExecutionContext.Implicits.global

  type Out = scala.collection.immutable.Iterable[((String, SuperheroUniverse), Int)]

  lazy val registry = StatefulFlowRegistry(30.seconds)

  lazy val flowProps = StatefulFlow.moviesDurationBaseLogic.propsForBackend(InMemoryStatefulFlowBackend.DurableState())
  lazy val flowBuilder = registry.registerStatefulFlowSync("by-actor-universe", flowProps)

  lazy val control = flowBuilder.lazyEntityControl(s"${henryCavill.hashCode()}-${DC}")


  override def flow: FlowWithExtendedContext[Movie, Out, Ctx, _] =
    StatefulFlow.moviesDurationByActorAndUniverseFlow(flowBuilder)
}
