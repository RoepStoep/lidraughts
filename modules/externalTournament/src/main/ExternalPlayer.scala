package lidraughts.externalTournament

import lidraughts.user.User

import ornicar.scalalib.Random

private[externalTournament] case class ExternalPlayer(
    _id: ExternalPlayer.ID, // random
    tourId: ExternalTournament.ID,
    userId: User.ID,
    fmjdId: Option[String],
    rating: Int,
    provisional: Boolean,
    status: ExternalPlayer.Status,
    rank: Option[Int],
    points: Int,
    byes: Option[List[PlayerInfo.Bye]]
) {

  def id = _id

  def is(uid: User.ID): Boolean = uid == userId
  def is(user: User): Boolean = is(user.id)
  def is(other: ExternalPlayer): Boolean = is(other.userId)

  def accepted = status.is(_.Accepted)
  def invited = status.is(_.Invited)
  def ranked = rank.isDefined

  def page = rank.fold(1) { r => (math.floor((r - 1) / 10) + 1).toInt }

  def withRank(r: Int) = copy(rank = r.some)

  def hasBye(r: Int) = byes.exists(_.exists(_.r == r))
}

private[externalTournament] object ExternalPlayer {

  type ID = String

  sealed abstract class Status(val id: Int, val key: String) {

    def is(s: Status): Boolean = this == s
    def is(f: Status.type => Status): Boolean = is(f(Status))
  }

  object Status {

    case object Invited extends Status(0, "invited")
    case object Rejected extends Status(10, "rejected")
    case object Accepted extends Status(20, "accepted")

    val all = List(Invited, Rejected, Accepted)
    val byId = all map { v => (v.id, v) } toMap

    def apply(id: Int): Option[Status] = byId get id
  }

  private[externalTournament] def make(
    tour: ExternalTournament,
    user: User,
    fmjdId: Option[String]
  ): ExternalPlayer = {
    val perf = tour.perfLens(user.perfs)
    new ExternalPlayer(
      _id = Random.nextString(8),
      tourId = tour.id,
      userId = user.id,
      fmjdId = fmjdId,
      status = Status.Invited,
      rating = perf.intRating,
      provisional = perf.provisional,
      rank = none,
      points = 0,
      byes = none
    )
  }
}
