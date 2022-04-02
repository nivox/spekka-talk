package spekkatalk

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import io.circe.Decoder

object Utils {
  def prettyPrintTimestamp(timestamp: Long): String = {
    val ts = Instant.ofEpochMilli(timestamp)
    ts
      .atZone(ZoneOffset.UTC)
      .format(DateTimeFormatter.RFC_1123_DATE_TIME)
  }

  def decodeJsonString[T](str: String)(implicit decoder: Decoder[T]): Either[Exception, T] = {
    for {
      json <- io.circe.parser.parse(str)
      state <- decoder(json.hcursor)
    } yield state
  }

  def deriveSpekkaCodec[T](implicit codec: io.circe.Codec[T]): spekka.codec.Codec[T] = {
    val encoder = spekka.codec.Encoder[T] { state =>
      codec(state).noSpaces.getBytes("UTF8")
    }

    val decoder = spekka.codec.Decoder[T] { bytes =>
      val str = new String(bytes, "UTF8")
      Utils.decodeJsonString[T](str).toTry
    }

    spekka.codec.Codec(encoder, decoder)
  }
}
