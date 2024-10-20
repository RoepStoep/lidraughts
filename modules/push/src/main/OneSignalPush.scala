package lidraughts.push

import play.api.libs.json._
import play.api.libs.ws.{ WS, WSResponse }
import play.api.Play.current

private final class OneSignalPush(
    getDevices: String => Fu[List[Device]],
    url: String,
    appId: String,
    key: String
) {

  def apply(userId: String)(data: => PushApi.Data): Funit =
    getDevices(userId) flatMap {
      case Nil => funit
      case devices =>
        WS.url(url)
          .withHeaders(
            "Authorization" -> s"key=$key",
            "Accept" -> "application/json",
            "Content-type" -> "application/json"
          )
          .post(Json.obj(
            "app_id" -> appId,
            "include_player_ids" -> devices.map(_.deviceId),
            "headings" -> Map("en" -> data.title),
            "contents" -> Map("en" -> data.body),
            "data" -> data.payload,
            "android_group" -> data.stacking.key,
            "android_group_message" -> Map("en" -> data.stacking.message),
            "collapse_id" -> data.stacking.key,
            "ios_badgeType" -> "Increase",
            "ios_badgeCount" -> 1
          )).flatMap {
            case res if res.status == 200 || res.status == 400 =>
              readErrors(res)
                .filterNot(_ contains "must have English language")
                .filterNot(_ contains "All included players are not subscribed") match {
                  case Nil => funit
                  case errors =>
                    fufail(s"[push] ${devices.map(_.deviceId)} $data ${res.status} ${errors mkString ","}")
                }
            case res => fufail(s"[push] ${devices.map(_.deviceId)} $data ${res.status} ${res.body}")
          }
    }

  private def readErrors(res: WSResponse): List[String] =
    ~(res.json \ "errors").asOpt[List[String]]
}
