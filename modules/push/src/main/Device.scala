package lidraughts.push

import org.joda.time.DateTime

private final case class Device(
    _id: String, // Firebase token or OneSignal playerId
    platform: String, // cordova platform (android, ios, firebase)
    userId: String,
    seenAt: DateTime
) {

  def deviceId = platform match {
    case "ios" => _id.grouped(8).mkString("<", " ", ">")
    case _ => _id
  }
}
