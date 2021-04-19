package lidraughts.externalTournament

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import draughts.variant.Variant

object DataForm {

  import lidraughts.common.Form._
  import lidraughts.common.Form.UTCDate._

  private val tournamentForm = Form(mapping(
    "variant" -> optional(Fields.variant),
    "name" -> Fields.name,
    "description" -> optional(Fields.description),
    "clock" -> optional(Fields.clock),
    "days" -> optional(Fields.days),
    "rated" -> boolean,
    "hasChat" -> optional(boolean),
    "userDisplay" -> optional(Fields.userDisplay),
    "rounds" -> optional(Fields.rounds)
  )(TournamentData.apply)(TournamentData.unapply)
    .verifying("Unlimited timecontrol cannot be rated", _.validateUnlimited))

  def tournamentCreate = tournamentForm fill TournamentData(
    variant = none,
    name = "",
    description = none,
    clock = none,
    days = none,
    rated = false,
    chat = true.some,
    userDisplay = none,
    rounds = none
  )

  def tournamentUpdate(t: ExternalTournament) = tournamentForm fill TournamentData(
    variant = t.variant.key.some,
    name = t.name,
    description = t.settings.description,
    clock = t.clock,
    days = t.days,
    rated = t.rated,
    chat = t.settings.hasChat.some,
    userDisplay = t.settings.userDisplay.key.some,
    rounds = t.settings.nbRounds
  )

  lazy val playerCreate = Form(mapping(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField,
    "fmjdId" -> optional(nonEmptyText(minLength = 5, maxLength = 6))
  )(PlayerData.apply)(PlayerData.unapply)) fill PlayerData(
    userId = "",
    fmjdId = none
  )

  lazy val gameCreate = Form(mapping(
    "whiteUserId" -> lidraughts.user.DataForm.historicalUsernameField,
    "blackUserId" -> lidraughts.user.DataForm.historicalUsernameField,
    "startsAt" -> optional(inTheFuture(utcDate)),
    "autoStart" -> optional(boolean),
    "round" -> optional(Fields.rounds)
  )(GameData.apply)(GameData.unapply)
    .verifying("Autostart requires startsAt to be specified", _.validAutoStart)) fill GameData(
    whiteUserId = "",
    blackUserId = "",
    startsAt = none,
    autoStart = none,
    round = none
  )

  case class TournamentData(
      variant: Option[String],
      name: String,
      description: Option[String],
      clock: Option[draughts.Clock.Config],
      days: Option[Int],
      rated: Boolean,
      chat: Option[Boolean],
      userDisplay: Option[String],
      rounds: Option[Int]
  ) {

    def make(userId: String) =
      ExternalTournament.make(
        userId = userId,
        config = this
      )

    def changedGameSettings(tour: ExternalTournament) =
      Variant.orDefault(~variant) != tour.variant ||
        clock != tour.clock ||
        days != tour.days ||
        rated != tour.rated

    def validateUnlimited = !rated || (clock.isDefined || days.isDefined)
  }

  case class GameData(
      whiteUserId: String,
      blackUserId: String,
      startsAt: Option[DateTime],
      autoStart: Option[Boolean],
      round: Option[Int]
  ) {

    def validAutoStart = !(~autoStart) || startsAt.isDefined
  }

  case class PlayerData(
      userId: String,
      fmjdId: Option[String]
  )

  private object Fields {

    private val maxClockSeconds = 180 * 60
    private val maxClockIncrement = 180 * 60

    private val colors = Set(draughts.White, draughts.Black).map(_.name)

    val variant = text.verifying(Variant.byKey.contains _)
    val name = cleanNonEmptyText(minLength = 3, maxLength = 40)
    val description = cleanNonEmptyText(maxLength = 20000)
    val clock = mapping(
      "limit" -> number(min = 0, max = maxClockSeconds),
      "increment" -> number(min = 0, max = maxClockIncrement)
    )(draughts.Clock.Config.apply)(draughts.Clock.Config.unapply)
    val days = number(min = 1, max = 14)
    val rounds = number(min = 1, max = 100)
    val userDisplay = text.verifying(ExternalTournament.UserDisplay.byKey.contains _)
  }

}
