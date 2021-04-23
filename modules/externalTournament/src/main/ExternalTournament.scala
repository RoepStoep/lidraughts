package lidraughts.externalTournament

import org.joda.time.DateTime

import draughts.Clock.{ Config => ClockConfig }
import draughts.Speed
import draughts.variant.Variant
import lidraughts.game.PerfPicker
import lidraughts.rating.PerfType
import lidraughts.user.User

case class ExternalTournament(
    _id: ExternalTournament.ID,
    name: String,
    createdBy: User.ID,
    clock: Option[ClockConfig],
    days: Option[Int],
    rated: Boolean,
    variant: Variant,
    settings: ExternalTournament.Settings
) {

  def id = _id

  def hasRounds = settings.nbRounds.nonEmpty
  def rounds = settings.nbRounds

  def speed = Speed(clock)

  def perfType: PerfType = PerfPicker.perfType(speed, variant, days) getOrElse PerfType.Standard
  def perfLens = PerfPicker.mainOrDefault(speed, variant, days)

  def futureStartsAt = settings.startsAt.filter(DateTime.now.isBefore(_))
  def secondsToStart = futureStartsAt.map(startsAt => (startsAt.getSeconds - nowSeconds).toInt atLeast 0)
}

object ExternalTournament {

  type ID = String

  case class Settings(
      startsAt: Option[DateTime],
      nbRounds: Option[Int],
      description: Option[String],
      userDisplay: UserDisplay,
      autoStart: Boolean,
      hasChat: Boolean,
      microMatches: Boolean
  )

  sealed abstract class UserDisplay(val key: String) {

    def fmjd = this == UserDisplay.Fmjd
  }

  object UserDisplay {

    case object Lidraughts extends UserDisplay("lidraughts")
    case object Fmjd extends UserDisplay("fmjd")

    val all = List(Lidraughts, Fmjd)
    val byKey = all map { v => (v.key, v) } toMap

    def apply(key: String): Option[UserDisplay] = byKey get key
  }

  private[externalTournament] def make(
    userId: User.ID,
    config: DataForm.TournamentData
  ): ExternalTournament = new ExternalTournament(
    _id = ornicar.scalalib.Random nextString 8,
    name = config.name,
    createdBy = userId,
    clock = config.clock,
    days = config.clock.isEmpty ?? config.days,
    rated = config.rated,
    variant = Variant.orDefault(~config.variant) match {
      case draughts.variant.FromPosition => draughts.variant.Standard
      case v @ _ => v
    },
    settings = Settings(
      startsAt = config.startsAt,
      nbRounds = config.rounds,
      description = config.description,
      hasChat = config.chat.getOrElse(true),
      autoStart = config.autoStart,
      microMatches = ~config.microMatches,
      userDisplay = config.userDisplay.flatMap(UserDisplay.apply).getOrElse(UserDisplay.Lidraughts)
    )
  )
}
