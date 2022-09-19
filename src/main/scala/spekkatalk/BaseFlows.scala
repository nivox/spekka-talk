package spekkatalk

import spekka.context.FlowWithExtendedContext
import spekka.context.Partition

object BaseFlows {
  import Model._

  import spekka.context.PartitionTree._

  val moviesDurationFlow = FlowWithExtendedContext[Movie, Ctx]
    .statefulMap(() => {
      var totalDuration: Int = 0

      (movie) =>
        totalDuration += movie.durationMin
        totalDuration
    })

  val moviesDurationByUniverseFlow =
    Partition
      .treeBuilder[Movie, Ctx]
      .dynamicAuto(_.universe)
      .build { case universe :@: KNil =>
        moviesDurationFlow.map(universe -> _)
      }

  val moviesDurationByActorAndUniverseFlow =
    Partition
      .treeBuilder[Movie, Ctx]
      .dynamicAutoMulticast[String]( {case (movie, _) => movie.actors.toSet} )
      .dynamicAuto(_.universe)
      .build { case universe :@: actor :@: KNil =>
        moviesDurationFlow.map((actor, universe) -> _)
      }
}
