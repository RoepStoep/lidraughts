package lidraughts.common

import play.api.http.HeaderNames
import play.api.mvc.RequestHeader

object ResponseHeaders {

  def headersForApiOrApp(req: RequestHeader) = {
    val appOrigin = HTTPRequest.appOrigin(req)
    List(
      "Access-Control-Allow-Origin" -> appOrigin.getOrElse("*"),
      "Access-Control-Allow-Methods" -> allowMethods,
      "Access-Control-Allow-Headers" -> {
        List(
          "Origin", "Authorization", "If-Modified-Since", "Cache-Control", "Content-Type"
        ) ::: appOrigin.isDefined.??(List("X-Requested-With", "sessionId"))
      }.mkString(", "),
      "Vary" -> "Origin"
    ) ::: appOrigin.isDefined.??(List(
        "Access-Control-Allow-Credentials" -> "true"
      ))
  }

  val allowMethods = List("OPTIONS", "GET", "POST", "PUT", "DELETE") mkString ", "
}
