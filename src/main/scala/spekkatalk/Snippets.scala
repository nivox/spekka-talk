import spekka.context.FlowWithExtendedContext
import akka.NotUsed
import spekka.context.Partition
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import spekka.context.ExtendedContext
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow
import spekka.context.PartitionTree
import akka.stream.scaladsl.FlowWithContext
import scala.collection.immutable
import spekka.stateful.StatefulFlowLogic
import spekka.stateful.AkkaPersistenceStatefulFlowBackend

object Snippets {
  trait Ctx
  trait In
  trait Ev
  trait State
  trait Out
  trait A
  trait B
  trait C
  trait M1
  trait M2
  trait M3
  trait Command

  val flow1: FlowWithExtendedContext[In, A, Ctx, M1] = ???
  val flow2: FlowWithExtendedContext[In, immutable.Iterable[B], Ctx, M2] = ???
  val flow3: FlowWithExtendedContext[B, C, Ctx, M3] = ???

  flow1.ordered() // Ensures ordering of outputs (with backpressure)

  FlowWithExtendedContext.zip(flow1, flow2) // Zips multiple flows
  // => FlowWithExtendedContext[In, (A, List[B]), Ctx, (M1, M2)]

  flow2.viaMultiplexed(flow3) // Multiplexes flow3 on the output elements of flow2
  // => FlowWithExtendedContext[In,immutable.Iterable[C],Ctx,M2]

  // Interacts nicely with standard Akka Source/Flow/Sink
  val source: Source[(In, Ctx), NotUsed] = ???
  val source1: Source[(A, Ctx), NotUsed] = source
    .map { case (in, ctx) => in -> ExtendedContext(ctx) }
    .via(flow1)
    .map { case (in, ectx) => in -> ectx.innerContext }

  val partitionsNr: Int = ???

  val eventLogic: StatefulFlowLogic.EventBased[State, Ev, In, Command] = ???
  eventLogic.propsForBackend(
    AkkaPersistenceStatefulFlowBackend
      .EventBased(
        AkkaPersistenceStatefulFlowBackend.EventBased.PersistencePlugin.CassandraStoragePlugin
      )
      .withEventAdapter(???)
      .withSnapshotAdapter(???)
      .withEventsPartitions(???)
  )

  val durableLogic: StatefulFlowLogic.DurableState[State, In, Out, Command] = ???
  durableLogic.propsForBackend(
    AkkaPersistenceStatefulFlowBackend.DurableState(
      AkkaPersistenceStatefulFlowBackend.DurableState.PersistencePlugin.JdbcStoragePlugin
    ).withSnapshotAdapter(???)
  )
}
