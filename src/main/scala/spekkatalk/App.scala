package spekkatalk

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.stream.javadsl.FlowWithContext
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.circe.generic._
import spekka.codec.Codec
import spekka.codec.Decoder
import spekka.codec.Encoder
import spekka.context.FlowWithExtendedContext
import spekka.context.Partition
import spekka.context.PartitionTree
import spekka.context.PartitionTree._
import spekka.context.PartitionTree.PartitionControl.DynamicControl
import spekka.context.PartitionTree.PartitionControl.StaticControl
import spekka.stateful.AkkaPersistenceStatefulFlowBackend
import spekka.stateful.InMemoryStatefulFlowBackend
import spekka.stateful.ShardedStatefulFlowRegistry
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


case class AppEnv()

object App extends AppSkeleton[AppEnv] {

  type M = NotUsed

  override val config: Config = ConfigFactory.load()

  override val consumerGroup: String = "talk-consumer"

  override def init()(implicit system: ActorSystem): AppEnv = AppEnv()

  override def processingFlow[Offset](env: AppEnv): Flow[(EntranceCounterReading, Offset),Offset,M] = {
    Flow[(EntranceCounterReading, Offset)]
    .map { case (reading, offset) => 
      println(s"Received: $reading")
      offset 
    }
  }

  override def route(env: AppEnv, materializedValue: M): Route = {
    import akka.http.scaladsl.server.Directives._

    reject
  }
}