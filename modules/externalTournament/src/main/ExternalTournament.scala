package lidraughts.externalTournament

import lidraughts.user.User

case class ExternalTournament(
    _id: String,
    name: String,
    createdBy: User.ID
) {

  def id = _id
}

object ExternalTournament {

  def makeId = ornicar.scalalib.Random nextString 8
}
