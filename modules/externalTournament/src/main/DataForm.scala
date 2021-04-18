package lidraughts.externalTournament

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import draughts.variant.Variant

object DataForm {

  import lidraughts.common.Form.cleanNonEmptyText

  private val maxClockSeconds = 180 * 60
  private val maxClockIncrement = 180 * 60

  val tournament = Form(mapping(
    "variant" -> optional(text.verifying(Variant.byKey.contains _)),
    "name" -> cleanNonEmptyText(minLength = 3, maxLength = 40),
    "clock" -> optional(mapping(
      "limit" -> number(min = 0, max = maxClockSeconds),
      "increment" -> number(min = 0, max = maxClockIncrement)
    )(draughts.Clock.Config.apply)(draughts.Clock.Config.unapply)),
    "days" -> optional(number(min = 1, max = 14)),
    "rated" -> boolean,
    "rounds" -> optional(number(min = 1, max = 99))
  )(TournamentData.apply)(TournamentData.unapply)) fill TournamentData(
    variant = none,
    name = "",
    clock = none,
    days = none,
    rated = false,
    rounds = none
  )

  val player = Form(mapping(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField,
    "fmjdId" -> optional(nonEmptyText(minLength = 5, maxLength = 6))
  )(PlayerData.apply)(PlayerData.unapply)) fill PlayerData(
    userId = "",
    fmjdId = none
  )

  case class TournamentData(
      variant: Option[String],
      name: String,
      clock: Option[draughts.Clock.Config],
      days: Option[Int],
      rated: Boolean,
      rounds: Option[Int]
  ) {

    def make(userId: String) =
      ExternalTournament.make(
        userId = userId,
        config = this
      )
  }

  case class PlayerData(
      userId: String,
      fmjdId: Option[String]
  )
}
