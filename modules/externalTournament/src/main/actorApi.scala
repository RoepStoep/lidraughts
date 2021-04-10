package lidraughts.externalTournament
package actorApi

import scala.concurrent.Promise

import lidraughts.socket.Socket.{ Uid, SocketVersion }
import lidraughts.socket.SocketMember
import lidraughts.user.User

private[externalTournament] case class Member(
    channel: JsChannel,
    userId: Option[String],
    troll: Boolean
) extends SocketMember

private[externalTournament] object Member {
  def apply(channel: JsChannel, user: Option[User]): Member = Member(
    channel = channel,
    userId = user map (_.id),
    troll = user.??(_.troll)
  )
}

private[externalTournament] case class Messadata(trollish: Boolean = false)

private[externalTournament] case class Join(
    uid: Uid,
    user: Option[User],
    version: Option[SocketVersion],
    promise: Promise[Connected]
)
private[externalTournament] case class Talk(tourId: String, u: String, t: String, troll: Boolean)
private[externalTournament] case object Reload
private[externalTournament] case class Connected(enumerator: JsEnumerator, member: Member)

private[externalTournament] case object NotifyCrowd
private[externalTournament] case object NotifyReload