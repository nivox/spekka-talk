package spekkatalk

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import spekka.context.FlowWithExtendedContext
import akka.NotUsed

object App1 extends AppBase {
  import BaseFlows._

  type Out = Int

  override def flow = moviesDurationFlow

}
