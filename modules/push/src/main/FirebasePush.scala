package lidraughts.push

import akka.actor.ActorSystem
import com.google.auth.oauth2.{ GoogleCredentials, AccessToken }
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.duration._
import scala.concurrent.{ blocking, Future }

import lidraughts.user.User

private final class FirebasePush(
    credentialsOpt: Option[GoogleCredentials],
    deviceApi: DeviceApi,
    url: String
)(implicit system: ActorSystem) {

  private val sequencer = new lidraughts.hub.DuctSequencer(
    maxSize = 512,
    timeout = 10 seconds,
    name = "firebasePush"
  )

  def apply(userId: User.ID)(data: => PushApi.Data): Funit =
    credentialsOpt ?? { creds =>
      deviceApi.findLastManyByUserId("firebase", 3)(userId) flatMap {
        case Nil => funit
        // access token has 1h lifetime and is requested only if expired
        case devices => sequencer {
          Future {
            blocking {
              creds.refreshIfExpired()
              creds.getAccessToken()
            }
          }
        }.chronometer.mon(_.push.googleTokenTime).result flatMap { token =>
          // TODO http batch request is possible using a multipart/mixed content
          // unfortuntely it doesn't seem easily doable with play WS
          devices.map(send(token, _, data)).sequenceFu.void
        }
      }
    }

  private def send(token: AccessToken, device: Device, data: => PushApi.Data): Funit =
    WS.url(url)
      .withHeaders(
        "Authorization" -> s"Bearer ${token.getTokenValue}",
        "Accept" -> "application/json",
        "Content-type" -> "application/json; UTF-8"
      )
      .post(Json.obj(
        "message" -> Json.obj(
          "token" -> device._id,
          // firebase doesn't support nested data object and we only use what is
          // inside userData
          "data" -> (data.payload \ "userData").asOpt[JsObject].map(transform(_)),
          "notification" -> Json.obj(
            "body" -> data.body,
            "title" -> data.title
          )
        ).add(
            "apns" -> data.iosBadge.map(number =>
              Json.obj(
                "payload" -> Json.obj(
                  "aps" -> Json.obj("badge" -> number)
                )
              ))
          )
      )) flatMap {
        case res if res.status == 200 => funit
        case res if res.status == 404 =>
          logger.info(s"Delete missing firebase device ${device}")
          deviceApi delete device
        case res => fufail(s"[push] firebase: ${res.status} ${res.body}")
      }

  // filter out any non string value, otherwise Firebase API silently rejects
  // the request
  private def transform(obj: JsObject): JsObject =
    JsObject(obj.fields.collect {
      case (k, v: JsString) => s"lidraughts.$k" -> v
      case (k, v: JsNumber) => s"lidraughts.$k" -> JsString(v.toString)
    })
}
