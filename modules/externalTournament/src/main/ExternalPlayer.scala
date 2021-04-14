package lidraughts.externalTournament

import lidraughts.user.User

import ornicar.scalalib.Random

private[externalTournament] case class ExternalPlayer(
    _id: ExternalPlayer.ID, // random
    tourId: ExternalTournament.ID,
    userId: User.ID,
    autoStart: Boolean = false
) {

  def id = _id

  def is(uid: User.ID): Boolean = uid == userId
  def is(user: User): Boolean = is(user.id)
  def is(other: ExternalPlayer): Boolean = is(other.userId)

  def setAutoStart = copy(autoStart = true)
  def unsetAutoStart = copy(autoStart = false)
}

private[externalTournament] object ExternalPlayer {

  type ID = String

  private[externalTournament] def make(
    tourId: ExternalTournament.ID,
    userId: User.ID
  ): ExternalPlayer = new ExternalPlayer(
    _id = Random.nextString(8),
    tourId = tourId,
    userId = userId
  )
}
