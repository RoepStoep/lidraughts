package lidraughts.externalTournament

import lidraughts.user.User

private[externalTournament] case class FmjdPlayer(
    _id: String,
    userId: Option[String],
    firstName: String,
    lastName: String,
    country: String,
    title: Option[String],
    rating: Option[Int],
    titleW: Option[String],
    ratingW: Option[Int]
) {

  def id = _id

  def is(uid: User.ID): Boolean = userId.contains(uid)
  def is(user: User): Boolean = is(user.id)

  def displayName = s"${lastName.capitalize}, ${firstName.capitalize}"
}
