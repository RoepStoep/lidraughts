package lidraughts.challenge

import draughts.format.FEN
import draughts.variant.{ Variant, FromPosition }
import draughts.{ Mode, Speed }
import org.joda.time.DateTime

import lidraughts.game.PerfPicker
import lidraughts.i18n.{ I18nKey, I18nKeys }
import lidraughts.rating.PerfType
import lidraughts.user.User
import draughts.format.Forsyth

case class Challenge(
    _id: String,
    status: Challenge.Status,
    variant: Variant,
    initialFen: Option[FEN],
    timeControl: Challenge.TimeControl,
    mode: Mode,
    colorChoice: Challenge.ColorChoice,
    finalColor: draughts.Color,
    challenger: EitherChallenger,
    destUser: Option[Challenge.Registered],
    rematchOf: Option[String],
    createdAt: DateTime,
    seenAt: DateTime,
    expiresAt: DateTime,
    external: Option[Challenge.ExternalChallenge],
    microMatch: Option[Boolean]
) {

  import Challenge._

  def id = _id

  def challengerUser = challenger.right.toOption
  def challengerUserId = challengerUser.map(_.id)
  def challengerIsAnon = challenger.isLeft
  def destUserId = destUser.map(_.id)

  def userIds = List(challengerUserId, destUserId).flatten

  def daysPerTurn = timeControl match {
    case TimeControl.Correspondence(d) => d.some
    case _ => none
  }
  def unlimited = timeControl == TimeControl.Unlimited

  def clock = timeControl match {
    case c: TimeControl.Clock => c.some
    case _ => none
  }

  def hasClock = clock.isDefined

  def openDest = destUser.isEmpty
  def online = status == Status.Created
  def active = online || status == Status.Offline
  def declined = status == Status.Declined
  def accepted = status == Status.Accepted

  def setDestUser(u: User) = copy(
    destUser = toRegistered(variant, timeControl)(u).some
  )

  def speed = speedOf(timeControl)

  def notableInitialFen: Option[FEN] =
    customStartingPosition ?? initialFen

  def customStartingPosition: Boolean =
    variant.fromPosition ||
      (fromPositionVariants(variant) && initialFen.isDefined && !initialFen.exists(_.value == variant.initialFen))

  def isExternal = external.isDefined

  def isMicroMatch = ~microMatch

  def acceptExternal(u: User) = external match {
    case Some(e) if challengerUserId.contains(u.id) => copy(
      external = e.copy(challengerAccepted = true).some
    )
    case Some(e) if destUserId.contains(u.id) => copy(
      external = e.copy(destUserAccepted = true).some
    )
    case _ => this
  }

  def hasAcceptedExternal(u: Option[User]) = u match {
    case Some(user) => external match {
      case Some(e) if challengerUserId.contains(user.id) => e.challengerAccepted
      case Some(e) if destUserId.contains(user.id) => e.destUserAccepted
      case _ => false
    }
    case _ => false
  }

  lazy val perfType = perfTypeOf(variant, timeControl)
}

object Challenge {

  type ID = String

  sealed abstract class Status(val id: Int) {
    val name = toString.toLowerCase
  }
  object Status {
    case object Created extends Status(10)
    case object Offline extends Status(15)
    case object Canceled extends Status(20)
    case object Declined extends Status(30)
    case object Accepted extends Status(40)
    case object External extends Status(50)
    val all = List(Created, Offline, Canceled, Declined, Accepted, External)
    def apply(id: Int): Option[Status] = all.find(_.id == id)
  }

  sealed abstract class DeclineReason(key: I18nKey)

  object DeclineReason {
    case object Generic extends DeclineReason(I18nKeys.challenge.declineGeneric)
    case object Later extends DeclineReason(I18nKeys.challenge.declineLater)
  }

  case class Rating(int: Int, provisional: Boolean) {
    def show = s"$int${if (provisional) "?" else ""}"
  }
  object Rating {
    def apply(p: lidraughts.rating.Perf): Rating = Rating(p.intRating, p.provisional)
  }

  case class Registered(id: User.ID, rating: Rating)
  case class Anonymous(secret: String)

  sealed trait TimeControl
  object TimeControl {
    case object Unlimited extends TimeControl
    case class Correspondence(days: Int) extends TimeControl
    case class Clock(config: draughts.Clock.Config) extends TimeControl {
      // All durations are expressed in seconds
      def limit = config.limit
      def increment = config.increment
      def show = config.show
    }
  }

  sealed trait ColorChoice
  object ColorChoice {
    case object Random extends ColorChoice
    case object White extends ColorChoice
    case object Black extends ColorChoice
  }

  case class ExternalChallenge(
      challengerAccepted: Boolean = false,
      destUserAccepted: Boolean = false,
      startsAt: Option[DateTime] = None
  ) {

    def bothAccepted = challengerAccepted && destUserAccepted
  }

  private def speedOf(timeControl: TimeControl) = timeControl match {
    case TimeControl.Clock(config) => Speed(config)
    case _ => Speed.Correspondence
  }

  private def perfTypeOf(variant: Variant, timeControl: TimeControl): PerfType =
    PerfPicker.perfType(speedOf(timeControl), variant, timeControl match {
      case TimeControl.Correspondence(d) => d.some
      case _ => none
    }).orElse {
      (variant == FromPosition) option perfTypeOf(draughts.variant.Standard, timeControl)
    }.|(PerfType.Correspondence)

  private val idSize = 8

  private def randomId = ornicar.scalalib.Random nextString idSize

  private def toRegistered(variant: Variant, timeControl: TimeControl)(u: User) =
    Registered(u.id, Rating(u.perfs(perfTypeOf(variant, timeControl))))

  // NOTE: Only variants with standardInitialPosition = false!
  private val fromPositionVariants = Set[Variant](
    draughts.variant.FromPosition,
    draughts.variant.Russian,
    draughts.variant.Brazilian
  )

  def make(
    variant: Variant,
    fenVariant: Option[Variant],
    initialFen: Option[FEN],
    timeControl: TimeControl,
    mode: Mode,
    color: String,
    challenger: Either[String, User],
    destUser: Option[User],
    rematchOf: Option[String],
    external: Boolean = false,
    startsAt: Option[DateTime] = None,
    microMatch: Boolean = false
  ): Challenge = {
    val (colorChoice, finalColor) = color match {
      case "white" => ColorChoice.White -> draughts.White
      case "black" => ColorChoice.Black -> draughts.Black
      case _ => ColorChoice.Random -> draughts.Color(scala.util.Random.nextBoolean)
    }
    val finalVariant = fenVariant match {
      case Some(v) if fromPositionVariants(variant) =>
        if (variant.fromPosition && v.standard) FromPosition
        else v
      case _ => variant
    }
    val finalInitialFen =
      fromPositionVariants(finalVariant) ?? {
        initialFen.flatMap(fen => Forsyth.<<@(finalVariant, fen.value)).map(sit => FEN(Forsyth >> sit.withoutGhosts))
      } match {
        case fen @ Some(_) => fen
        case _ => !finalVariant.standardInitialPosition option FEN(finalVariant.initialFen)
      }
    val finalMode = timeControl match {
      case TimeControl.Clock(clock) if !lidraughts.game.Game.allowRated(finalVariant, clock) => Mode.Casual
      case _ => mode
    }
    new Challenge(
      _id = randomId,
      status = if (external) Status.External else Status.Created,
      variant = finalVariant,
      initialFen = finalInitialFen,
      timeControl = timeControl,
      mode = finalMode,
      colorChoice = colorChoice,
      finalColor = finalColor,
      challenger = challenger.fold[EitherChallenger](
        sid => Left(Anonymous(sid)),
        u => Right(toRegistered(finalVariant, timeControl)(u))
      ),
      destUser = destUser map toRegistered(finalVariant, timeControl),
      rematchOf = rematchOf,
      createdAt = DateTime.now,
      seenAt = DateTime.now,
      expiresAt = inTwoWeeks,
      external = external option ExternalChallenge(startsAt = startsAt),
      microMatch = microMatch option true
    ) |> { challenge =>
      if (microMatch && !challenge.customStartingPosition)
        challenge.copy(microMatch = none)
      else challenge
    } |> { challenge =>
      if (challenge.mode.rated && !challenge.isMicroMatch && challenge.customStartingPosition)
        challenge.copy(mode = Mode.Casual)
      else challenge
    }
  }
}
