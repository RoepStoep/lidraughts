package lidraughts.externalTournament

import lidraughts.user.User

case class ExternalTournament(
    _id: ExternalTournament.ID,
    name: String,
    createdBy: User.ID
) {

  def id = _id
}

object ExternalTournament {

  type ID = String

  private[externalTournament] def make(
    name: String,
    userId: User.ID
  ): ExternalTournament = new ExternalTournament(
    _id = ornicar.scalalib.Random nextString 8,
    name = name,
    createdBy = userId
  )
}
