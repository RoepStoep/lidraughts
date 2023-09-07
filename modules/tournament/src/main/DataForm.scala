package lidraughts.tournament

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation
import play.api.data.validation.{ Constraint, Constraints }

import draughts.Mode
import draughts.StartingPosition
import draughts.variant.{ Brazilian, Variant, Standard, Russian }
import lidraughts.common.Form._
import lidraughts.hub.lightTeam._
import lidraughts.user.User

final class DataForm {

  import DataForm._
  import UTCDate._

  def create(user: User, teamBattleId: Option[TeamId] = None) = form(user) fill TournamentSetup(
    name = canPickName(user) && teamBattleId.isEmpty option user.titleUsername,
    clockTime = clockTimeDefault,
    clockIncrement = clockIncrementDefault,
    minutes = minuteDefault,
    waitMinutes = waitMinuteDefault.some,
    startDate = none,
    variant = draughts.variant.Standard.id.toString.some,
    positionStandard = Standard.initialFen.some,
    positionRussian = Russian.initialFen.some,
    positionBrazilian = Brazilian.initialFen.some,
    password = None,
    mode = none,
    rated = true.some,
    conditions = Condition.DataForm.AllSetup.default,
    teamBattleByTeam = teamBattleId,
    berserkable = true.some,
    streakable = true.some,
    description = none,
    hasChat = true.some,
    promoted = false.some
  )

  def edit(user: User, tour: Tournament, teamBattleId: Option[TeamId] = None) = form(user) fill TournamentSetup(
    name = tour.name.some,
    clockTime = tour.clock.limitInMinutes,
    clockIncrement = tour.clock.incrementSeconds,
    minutes = tour.minutes,
    waitMinutes = none,
    startDate = tour.startsAt.some,
    variant = tour.variant.id.toString.some,
    positionStandard = if (tour.variant.standard) tour.positionKey.some else Standard.initialFen.some,
    positionRussian = if (tour.variant.russian) tour.positionKey.some else Russian.initialFen.some,
    positionBrazilian = if (tour.variant.brazilian) tour.positionKey.some else Brazilian.initialFen.some,
    mode = none,
    rated = tour.mode.rated.some,
    password = tour.password,
    conditions = Condition.DataForm.AllSetup(tour.conditions),
    teamBattleByTeam = teamBattleId,
    berserkable = tour.berserkable.some,
    streakable = tour.streakable.some,
    description = tour.description,
    hasChat = tour.hasChat.some,
    promoted = tour.isPromoted.some
  )

  private val nameType = eventName(2, 30).verifying(
    Constraint[String] { (t: String) =>
      if (t.toLowerCase contains "lidraughts") validation.Invalid(validation.ValidationError("mustNotContainLidraughts"))
      else validation.Valid
    }
  )

  private def form(user: User) = Form(mapping(
    "name" -> optional(nameType),
    "clockTime" -> numberInDouble(clockTimeChoices),
    "clockIncrement" -> numberIn(clockIncrementChoices),
    "minutes" -> {
      if (lidraughts.security.Granter(_.ManageTournament)(user)) number
      else numberIn(minuteChoices)
    },
    "waitMinutes" -> optional(numberIn(waitMinuteChoices)),
    "startDate" -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
    "variant" -> optional(text.verifying(v => guessVariant(v).isDefined)),
    "position_standard" -> optional(nonEmptyText),
    "position_russian" -> optional(nonEmptyText),
    "position_brazilian" -> optional(nonEmptyText),
    "mode" -> optional(number.verifying(Mode.all map (_.id) contains _)), // deprecated, use rated
    "rated" -> optional(boolean),
    "password" -> optional(cleanNonEmptyText),
    "conditions" -> Condition.DataForm.all,
    "teamBattleByTeam" -> optional(nonEmptyText),
    "berserkable" -> optional(boolean),
    "streakable" -> optional(boolean),
    "description" -> optional(cleanNonEmptyText(maxLength = 800)),
    "hasChat" -> optional(boolean),
    "promoted" -> optional(boolean)
  )(TournamentSetup.apply)(TournamentSetup.unapply)
    .verifying("Invalid clock", _.validClock)
    .verifying("15s variant games cannot be rated", _.validRatedUltraBulletVariant)
    .verifying("Increase tournament duration, or decrease game clock", _.sufficientDuration)
    .verifying("Reduce tournament duration, or increase game clock", _.excessiveDuration)
    .verifying("Start date is too far in the future", _.validStartDate))
}

object DataForm {

  def canPickName(u: User) = {
    u.count.game >= 10 && u.createdSinceDays(3) && !u.troll
  } || u.hasTitle || u.isVerified

  import draughts.variant._

  val clockTimes: Seq[Double] = Seq(0d, 1 / 4d, 1 / 2d, 3 / 4d, 1d, 3 / 2d) ++ (2d to 8d by 1d) ++ (10d to 30d by 5d) ++ (40d to 60d by 10d)
  val clockTimeDefault = 2d
  private def formatLimit(l: Double) =
    draughts.Clock.Config(l * 60 toInt, 0).limitString + {
      if (l <= 1) " minute" else " minutes"
    }
  val clockTimeChoices = optionsDouble(clockTimes, formatLimit)

  val clockIncrements = (0 to 7 by 1) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  val clockIncrementDefault = 0
  val clockIncrementChoices = options(clockIncrements, "%d second{s}")

  val minutes = (20 to 60 by 5) ++ (70 to 120 by 10) ++ (150 to 360 by 30)
  val minuteDefault = 45
  val minuteChoices = options(minutes, "%d minute{s}")

  val waitMinutes = Seq(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
  val waitMinuteChoices = options(waitMinutes, "%d minute{s}")
  val waitMinuteDefault = 5

  val validVariants = List(Standard, Frisian, Frysk, Antidraughts, Breakthrough, Russian, Brazilian)

  def guessVariant(from: String): Option[Variant] = validVariants.find { v =>
    v.key == from || parseIntOption(from).exists(v.id ==)
  }
}

private[tournament] case class TournamentSetup(
    name: Option[String],
    clockTime: Double,
    clockIncrement: Int,
    minutes: Int,
    waitMinutes: Option[Int],
    startDate: Option[DateTime],
    variant: Option[String],
    positionStandard: Option[String], // tableKey | fen/random
    positionRussian: Option[String], // NOTE: Safe for variants without standard initial position (i.e. 64 squares)
    positionBrazilian: Option[String],
    mode: Option[Int], // deprecated, use rated
    rated: Option[Boolean],
    password: Option[String],
    conditions: Condition.DataForm.AllSetup,
    teamBattleByTeam: Option[String],
    berserkable: Option[Boolean],
    streakable: Option[Boolean],
    description: Option[String],
    hasChat: Option[Boolean],
    promoted: Option[Boolean]
) {

  def validClock = (clockTime + clockIncrement) > 0

  def realMode = Mode(rated.orElse(mode.map(Mode.Rated.id ==)) | true)

  def realVariant = variant.flatMap(DataForm.guessVariant) | draughts.variant.Standard

  def clockConfig = draughts.Clock.Config((clockTime * 60).toInt, clockIncrement)

  def validRatedUltraBulletVariant =
    realMode == Mode.Casual ||
      lidraughts.game.Game.allowRated(realVariant, clockConfig)

  def sufficientDuration = estimateNumberOfGamesOneCanPlay >= 3
  def excessiveDuration = estimateNumberOfGamesOneCanPlay <= 70

  def maxFutureDays = if (conditions.teamMember.flatMap(_.teamId).exists(_.nonEmpty)) 180 else 31

  def validStartDate = startDate.fold(~waitMinutes > 0)(DateTime.now.plusDays(maxFutureDays).isAfter(_))

  private def estimateNumberOfGamesOneCanPlay: Double = (minutes * 60) / estimatedGameSeconds

  // There are 2 players, and they don't always use all their time (0.8)
  // add 15 seconds for pairing delay
  private def estimatedGameSeconds: Double = {
    (60 * clockTime + 30 * clockIncrement) * 2 * 0.8
  } + 15

  def positionKey(v: Variant) = v match {
    case draughts.variant.Standard => positionStandard
    case draughts.variant.Russian => positionRussian
    case draughts.variant.Brazilian => positionBrazilian
    case _ => none
  }

  def startingPositionFor(v: Variant) =
    positionKey(v).flatMap { key =>
      val parts = key.split('|')
      parts.headOption.flatMap(draughts.OpeningTable.byKey).flatMap { table =>
        parts.lastOption.flatMap(table.openingByFen)
      }
    } | v.startingPosition

  def startingPosition = startingPositionFor(realVariant)

  def openingTableFor(v: Variant) =
    positionKey(v).flatMap { key =>
      key.split('|').headOption.flatMap(draughts.OpeningTable.byKey)
    } filter v.openingTables.contains

  def openingTable = openingTableFor(realVariant)
}
