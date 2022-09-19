
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

import akka.actor.typed.scaladsl.adapter._
import spekka.stateful.ShardedStatefulFlowRegistry
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

object App6 extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem()

  val registry = StatefulFlowRegistry(30.seconds)

  val clusterSharding = ClusterSharding(system.toTyped)
  val shardedRegistry =
    ShardedStatefulFlowRegistry(registry, clusterSharding, 30.seconds)
}