package spekkatalk

import spekka.context.PartitionTree

case class EntranceCounterReading(
    timestamp: Long,
    deploymentId: String,
    entranceId: Int,
    counter: Int
)

object EntranceCounterReadingJsonCodecs {
    import io.circe._
    import io.circe.generic.semiauto._

    implicit val entranceCounterReadingCodec = deriveCodec[EntranceCounterReading]
}

sealed trait CombinedMaterialization
object CombinedMaterialization {
  case class ByDeployment(m: PartitionTree.PartitionControl.DynamicControl[String,() => Long]) extends CombinedMaterialization
  case class ByEntrance(m: PartitionTree.PartitionControl.DynamicControl[String,PartitionTree.PartitionControl.DynamicControl[Int,() => Long]]) extends CombinedMaterialization
}