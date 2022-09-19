package spekkatalk

import spekka.context.FlowWithExtendedContext
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import scala.concurrent.Future

trait AppBase extends App {
  import Model._
  import Data._

  type Out
  def flow: FlowWithExtendedContext[Movie, Out, Ctx, _]

  def post: Future[Unit] = Future.successful( () )

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem("app")

  val done =
    Source(allMovies).zipWithIndex
      .via(
        FlowWithExtendedContext.zip(
          FlowWithExtendedContext[Movie, Ctx].map(_.title),
          flow
        )
      )
      .runWith(
        Sink.foreach { case (title -> data, ctx) =>
          println(f"ctx=${ctx} movie=${title}%-20s${data}")
        }
      )

  val allDone = for {
    _ <- done
    _ <- post
  } yield ()

  allDone.onComplete {
    case _ => system.terminate()
  }
}
