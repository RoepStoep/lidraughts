package lidraughts.externalTournament

import lidraughts.user.User

import ornicar.scalalib.Random

private[externalTournament] case class ExternalPlayer(
    _id: ExternalPlayer.ID, // random
    tourId: ExternalTournament.ID,
    userId: User.ID,
    status: ExternalPlayer.Status
) {

  def id = _id

  def is(uid: User.ID): Boolean = uid == userId
  def is(user: User): Boolean = is(user.id)
  def is(other: ExternalPlayer): Boolean = is(other.userId)

  def joined = status.is(_.Joined)
  def invited = status.is(_.Invited)
}

private[externalTournament] object ExternalPlayer {

  type ID = String

  sealed abstract class Status(val id: Int) {

    def is(s: Status): Boolean = this == s
    def is(f: Status.type => Status): Boolean = is(f(Status))
  }

  object Status {

    case object Invited extends Status(0)
    case object Rejected extends Status(10)
    case object Joined extends Status(20)

    val all = List(Invited, Rejected, Joined)
    val byId = all map { v => (v.id, v) } toMap

    def apply(id: Int): Option[Status] = byId get id
  }

  private[externalTournament] def make(
    tourId: ExternalTournament.ID,
    userId: User.ID,
    autoJoin: Boolean = false
  ): ExternalPlayer = new ExternalPlayer(
    _id = Random.nextString(8),
    tourId = tourId,
    userId = userId,
    status = if (autoJoin) Status.Joined else Status.Invited
  )
}
