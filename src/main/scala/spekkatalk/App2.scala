package spekkatalk

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import spekka.context.Partition
import spekka.context.FlowWithExtendedContext
import akka.NotUsed

object App2 extends AppBase {
  import Model._
  import Data._
  import BaseFlows._

  type Out = (SuperheroUniverse, Int)

  override def flow: FlowWithExtendedContext[Movie,Out,Ctx,_] = 
    moviesDurationByUniverseFlow
}
