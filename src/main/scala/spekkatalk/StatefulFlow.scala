package spekkatalk

import spekka.stateful.StatefulFlowLogic
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.Done
import scala.concurrent.Future
import spekka.stateful.StatefulFlowBuilder
import spekka.context.Partition

object StatefulFlow {
  import Model._
  import spekka.context.PartitionTree._

  sealed trait Command
  object Command {
    case class GetCurrentDuration(replyTo: ActorRef[StatusReply[Int]]) extends Command
  }

  val moviesDurationBaseLogic = StatefulFlowLogic.DurableState[Int, Movie, Int](
    () => 0,
    (state, in) => {
      val updatedDuration = state + in.durationMin
      StatefulFlowLogic.DurableState.ProcessingResult(updatedDuration, updatedDuration)
        .withBeforeUpdateSideEffect(() => ??? /* at-least-once semantics */)
        .withAfterUpdateSideEffect(() => ??? /* at-most-once semantics */)

    }
  )

  val moviesDurationLogic = StatefulFlowLogic.DurableState[Int, Movie, Int, Command](
    () => 0,
    (state, in) => {
      val updatedDuration = state + in.durationMin
      StatefulFlowLogic.DurableState.ProcessingResult(updatedDuration, updatedDuration)
    },
    (state, command) =>
      command match {
        case Command.GetCurrentDuration(replyTo) =>
          replyTo.tell(StatusReply.success(state))
          StatefulFlowLogic.DurableState.ProcessingResult(state)
      }
  )

  def moviesDurationByActorAndUniverseFlow[C](moviesDurationFlowBuilder: StatefulFlowBuilder[Movie, Int, C]) =
    Partition
      .treeBuilder[Movie, Ctx]
      .dynamicAutoMulticast[String]({ case (movie, _) => movie.actors.toSet })
      .dynamicAuto(_.universe)
      .build { case universe :@: actor :@: KNil =>
        moviesDurationFlowBuilder
          .flowWithExtendedContext[Ctx](s"${actor.hashCode()}-${universe}")
          .map(_.map((actor, universe) -> _))
      }
      .map(_.flatten)
}
