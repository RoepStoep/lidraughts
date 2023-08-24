package lidraughts.team

import akka.actor._
import akka.pattern.pipe
import play.api.libs.iteratee._
import play.api.libs.json._
import scala.collection.breakOut
import scala.concurrent.duration._

import actorApi.{ Member => SocketMember, _ }
import lidraughts.chat.Chat
import lidraughts.hub.Trouper
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.{ SocketTrouper, History, Historical, Socket }

private[team] final class TeamSocket(
    system: ActorSystem,
    teamId: String,
    protected val history: History[Messadata],
    lightUser: lidraughts.common.LightUser.Getter,
    lightWfdUser: lidraughts.common.LightWfdUser.Getter,
    toWfdName: String => Option[String],
    isWfdTeam: String => Boolean,
    uidTtl: Duration,
    keepMeAlive: () => Unit
) extends SocketTrouper[SocketMember](system, uidTtl) with Historical[SocketMember, Messadata] {

  private var delayedCrowdNotification = false

  private def chatClassifier = Chat classify Chat.Id(teamId)

  lidraughtsBus.subscribe(this, chatClassifier)

  override def stop(): Unit = {
    super.stop()
    lidraughtsBus.unsubscribe(this, chatClassifier)
  }

  protected def receiveSpecific = ({

    case lidraughts.socket.Socket.GetVersion(promise) => promise success history.version

    case Join(uid, user, version, promise) =>
      val (enumerator, channel) = Concurrent.broadcast[JsValue]
      val member = SocketMember(channel, user)
      addMember(uid, member)
      notifyCrowd
      promise success Connected(
        prependEventsSince(version, enumerator, member),
        member
      )

    case NotifyCrowd =>
      delayedCrowdNotification = false
      showSpectators(lightUser, isWfdTeam(teamId) option lightWfdUser)(members.values) foreach {
        notifyAll("crowd", _)
      }

  }: Trouper.Receive) orElse lidraughts.chat.Socket.out(
    send = (t, d, trollish) => notifyVersion(t, d, Messadata(trollish)),
    pimpUser = Some(() => isWfdTeam(teamId) option toWfdName)
  )

  override protected def broom: Unit = {
    super.broom
    if (members.nonEmpty) keepMeAlive()
  }

  override protected def afterQuit(uid: Socket.Uid, member: SocketMember) = notifyCrowd

  private def notifyCrowd: Unit =
    if (!delayedCrowdNotification) {
      delayedCrowdNotification = true
      system.scheduler.scheduleOnce(1 second)(this ! NotifyCrowd)
    }

  protected def shouldSkipMessageFor(message: Message, member: SocketMember) =
    message.metadata.trollish && !member.troll
}
