package lidraughts.push

import org.joda.time.DateTime
import reactivemongo.bson._

import lidraughts.db.dsl._
import lidraughts.user.User

private final class DeviceApi(coll: Coll) {

  private implicit val DeviceBSONHandler = Macros.handler[Device]

  private[push] def findByDeviceId(deviceId: String): Fu[Option[Device]] =
    coll.find($id(deviceId)).uno[Device]

  private[push] def findLastManyByUserId(platform: String, max: Int)(userId: String): Fu[List[Device]] =
    coll.find($doc(
      "platform" -> platform,
      "userId" -> userId
    )).sort($doc("seenAt" -> -1)).list[Device](max)

  private[push] def findLastOneByUserId(platform: String)(userId: String): Fu[Option[Device]] =
    findLastManyByUserId(platform, 1)(userId) map (_.headOption)

  def register(user: User, platform: String, deviceId: String) = {
    lidraughts.mon.push.register.in(platform)()
    coll.update($id(deviceId), Device(
      _id = deviceId,
      platform = platform,
      userId = user.id,
      seenAt = DateTime.now
    ), upsert = true).void
  }

  def unregister(user: User) = {
    lidraughts.mon.push.register.out()
    coll.remove($doc("userId" -> user.id)).void
  }

  def delete(device: Device) =
    coll.remove($id(device._id)).void
}
