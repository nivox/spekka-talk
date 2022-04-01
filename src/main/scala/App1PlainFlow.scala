import spekkatalk.AppSkeleton

import akka.NotUsed
import akka.stream.scaladsl.Flow
import spekkatalk.EntranceCounterReading
import akka.http.scaladsl.server.Route

object App1PlainFlow extends AppSkeleton[NotUsed] {

  override def processingFlow[Offset] = Flow[(EntranceCounterReading, Offset)].map { case (r, offset) => 
    println(r)
    offset
  }

  override def route[M](materializedValue: M): Route = {
      import akka.http.scaladsl.server.Directives._

      reject
  }


}