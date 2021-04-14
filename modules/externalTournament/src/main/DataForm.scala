package lidraughts.externalTournament

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

object DataForm {

  import lidraughts.common.Form.cleanNonEmptyText

  val tournament = Form(mapping(
    "name" -> cleanNonEmptyText(minLength = 3, maxLength = 40)
  )(TournamentData.apply)(TournamentData.unapply)) fill TournamentData(
    name = ""
  )

  val player = Form(mapping(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField
  )(PlayerData.apply)(PlayerData.unapply)) fill PlayerData(
    userId = ""
  )

  case class TournamentData(
      name: String
  ) {

    def make(userId: String) = ExternalTournament.make(
      name = name,
      userId = userId
    )
  }

  case class PlayerData(
      userId: String
  ) {

    def make(tourId: String) = ExternalPlayer.make(
      tourId = tourId,
      userId = userId
    )
  }
}
