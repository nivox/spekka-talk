package spekkatalk

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