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
    "autoStartGames" -> boolean,
    "startsAt" -> optional(Fields.startsAt),
    "userDisplay" -> optional(Fields.userDisplay),
    "rounds" -> optional(Fields.round),
    "microMatches" -> optional(boolean)
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
    autoStart = false,
    startsAt = none,
    userDisplay = none,
    rounds = none,
    microMatches = none
  )

  def tournamentUpdate(t: ExternalTournament) = tournamentForm fill TournamentData(
    variant = t.variant.key.some,
    name = t.name,
    description = t.settings.description,
    clock = t.clock,
    days = t.days,
    rated = t.rated,
    chat = t.settings.hasChat.some,
    autoStart = t.settings.autoStart,
    startsAt = t.futureStartsAt,
    userDisplay = t.settings.userDisplay.key.some,
    rounds = t.settings.nbRounds,
    microMatches = t.settings.microMatches option true
  )

  lazy val playerCreate = Form(mapping(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField,
    "fmjdId" -> optional(nonEmptyText(minLength = 5, maxLength = 6))
  )(PlayerData.apply)(PlayerData.unapply)) fill PlayerData(
    userId = "",
    fmjdId = none
  )

  lazy val playerBye = Form(mapping(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField,
    "round" -> Fields.round,
    "full" -> boolean
  )(ByeData.apply)(ByeData.unapply)) fill ByeData(
    userId = "",
    round = 0,
    full = false
  )

  lazy val gameCreate = Form(mapping(
    "whiteUserId" -> lidraughts.user.DataForm.historicalUsernameField,
    "blackUserId" -> lidraughts.user.DataForm.historicalUsernameField,
    "startsAt" -> Fields.startsAt,
    "round" -> optional(Fields.round),
    "fen" -> optional(nonEmptyText)
  )(GameData.apply)(GameData.unapply)) fill GameData(
    whiteUserId = "",
    blackUserId = "",
    startsAt = DateTime.now,
    round = none,
    fen = none
  )

  case class TournamentData(
      variant: Option[String],
      name: String,
      description: Option[String],
      clock: Option[draughts.Clock.Config],
      days: Option[Int],
      rated: Boolean,
      chat: Option[Boolean],
      autoStart: Boolean,
      startsAt: Option[DateTime],
      userDisplay: Option[String],
      rounds: Option[Int],
      microMatches: Option[Boolean]
  ) {

    def make(userId: String) =
      ExternalTournament.make(
        userId = userId,
        config = this
      )

    def changedGameSettings(tour: ExternalTournament) = List(
      Variant.orDefault(~variant) != tour.variant option "variant",
      clock != tour.clock option "clock",
      days != tour.days option "days",
      rated != tour.rated option "rated",
      ~microMatches != tour.settings.microMatches option "microMatches"
    ).flatten

    def validateUnlimited = !rated || (clock.isDefined || days.isDefined)
  }

  case class GameData(
      whiteUserId: String,
      blackUserId: String,
      startsAt: DateTime,
      round: Option[Int],
      fen: Option[String]
  )

  case class PlayerData(
      userId: String,
      fmjdId: Option[String]
  )

  case class ByeData(
      userId: String,
      round: Int,
      full: Boolean
  )

  private object Fields {

    private val maxClockSeconds = 180 * 60
    private val maxClockIncrement = 180 * 60

    val variant = text.verifying(Variant.byKey.contains _)
    val name = cleanNonEmptyText(minLength = 3, maxLength = 40)
    val description = cleanNonEmptyText(maxLength = 20000)
    val clock = mapping(
      "limit" -> number(min = 0, max = maxClockSeconds),
      "increment" -> number(min = 0, max = maxClockIncrement)
    )(draughts.Clock.Config.apply)(draughts.Clock.Config.unapply)
    val days = number(min = 1, max = 14)
    val round = number(min = 1, max = 100)
    val userDisplay = text.verifying(ExternalTournament.UserDisplay.byKey.contains _)
    val startsAt = inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
  }

}
