package lidraughts.tournament

import draughts.StartingPosition
import draughts.variant.Variant
import org.joda.time.{ DateTime, DateTimeZone, DateTimeConstants => dt }

import lidraughts.rating.PerfType

case class Schedule(
    freq: Schedule.Freq,
    speed: Schedule.Speed,
    variant: Variant,
    position: StartingPosition,
    at: DateTime,
    conditions: Condition.All = Condition.All.empty
) {

  def name = freq match {
    case m @ Schedule.Freq.ExperimentalMarathon => m.name
    case _ if variant.standard && position.initialStandard =>
      (conditions.minRating, conditions.maxRating) match {
        case (None, None) => s"${freq.toString} ${speed.toString}"
        case (Some(min), _) => s"Elite ${speed.toString}"
        case (_, Some(max)) => s"U${max.rating} ${speed.toString}"
      }
    case _ if variant.standard => s"${position.shortName} ${speed.toString}"
    case Schedule.Freq.Hourly => s"${variant.name} ${speed.toString}"
    case _ => s"${freq.toString} ${variant.name}"
  }

  def openingTable =
    (freq != Schedule.Freq.Shield && freq.isWeeklyOrBetter && (variant.russian || variant.brazilian)) ?? variant.openingTables.headOption

  def day = at.withTimeAtStartOfDay

  def sameSpeed(other: Schedule) = speed == other.speed

  def similarSpeed(other: Schedule) = Schedule.Speed.similar(speed, other.speed)

  def sameVariant(other: Schedule) = variant.id == other.variant.id

  def similar64Variant(other: Schedule) = (variant.russian || variant.brazilian) && (other.variant.russian || other.variant.brazilian)

  def sameVariantAndSpeed(other: Schedule) = sameVariant(other) && sameSpeed(other)

  def sameFreq(other: Schedule) = freq == other.freq

  def sameConditions(other: Schedule) = conditions == other.conditions

  def sameMaxRating(other: Schedule) = conditions sameMaxRating other.conditions

  def sameDay(other: Schedule) = day == other.day

  def hasMaxRating = conditions.maxRating.isDefined

  def similarTo(other: Schedule) =
    similarSpeed(other) && sameVariant(other) && sameFreq(other) && sameConditions(other)

  def perfType = PerfType.byVariant(variant) | Schedule.Speed.toPerfType(speed)

  def plan = Schedule.Plan(this, None)
  def plan(build: Tournament => Tournament) = Schedule.Plan(this, build.some)

  override def toString = s"$freq $variant $speed $conditions $at"
}

object Schedule {

  case class Plan(schedule: Schedule, build: Option[Tournament => Tournament])

  sealed abstract class Freq(val id: Int, val importance: Int) extends Ordered[Freq] {

    val name = toString.toLowerCase

    def compare(other: Freq) = Integer.compare(importance, other.importance)

    def isDaily = this == Schedule.Freq.Daily
    def isDailyOrBetter = this >= Schedule.Freq.Daily
    def isWeeklyOrBetter = this >= Schedule.Freq.Weekly
    def isUnique = this == Schedule.Freq.Unique
    def isShield = this == Schedule.Freq.Shield
  }
  object Freq {
    val userPromotedImportance = 21
    case object Hourly extends Freq(10, 10)
    case object Daily extends Freq(20, 20)
    case object Eastern extends Freq(30, 15)
    case object Weekly extends Freq(40, 40)
    case object Weekend extends Freq(41, 41)
    case object Monthly extends Freq(50, 50)
    case object Shield extends Freq(51, 51)
    case object Marathon extends Freq(60, 60)
    case object ExperimentalMarathon extends Freq(61, 55) { // for DB BC
      override val name = "Experimental Marathon"
    }
    case object Yearly extends Freq(70, 70)
    case object Unique extends Freq(90, 59)
    val all: List[Freq] = List(Hourly, Daily, Eastern, Weekly, Weekend, Monthly, Shield, Marathon, ExperimentalMarathon, Yearly, Unique)
    def apply(name: String) = all.find(_.name == name)
    def byId(id: Int) = all.find(_.id == id)
  }

  sealed abstract class Speed(val id: Int) {
    val name = lidraughts.common.String lcfirst toString
  }
  object Speed {
    case object UltraBullet extends Speed(5)
    case object HyperBullet extends Speed(10)
    case object Bullet extends Speed(20)
    case object HippoBullet extends Speed(25)
    case object SuperBlitz extends Speed(30)
    case object Blitz extends Speed(40)
    case object Rapid extends Speed(50)
    case object Classical extends Speed(60)
    val all: List[Speed] = List(UltraBullet, HyperBullet, Bullet, HippoBullet, SuperBlitz, Blitz, Rapid, Classical)
    val mostPopular: List[Speed] = List(Bullet, Blitz, Rapid, Classical)
    def apply(name: String) = all.find(_.name == name) orElse all.find(_.name.toLowerCase == name.toLowerCase)
    def byId(id: Int) = all find (_.id == id)
    def similar(s1: Speed, s2: Speed) = (s1, s2) match {
      case (a, b) if a == b => true
      case (HyperBullet, Bullet) | (Bullet, HyperBullet) => true
      case (HyperBullet, HippoBullet) | (HippoBullet, HyperBullet) => true
      case (Bullet, HippoBullet) | (HippoBullet, Bullet) => true
      case (Blitz, SuperBlitz) | (SuperBlitz, Blitz) => true
      case _ => false
    }
    def fromClock(clock: draughts.Clock.Config) = {
      val time = clock.estimateTotalSeconds
      if (time < 30) UltraBullet
      else if (time < 60) HyperBullet
      else if (time < 120) Bullet
      else if (time < 180) HippoBullet
      else if (time < 480) Blitz
      else if (time < 1500) Rapid
      else Classical
    }
    def toPerfType(speed: Speed) = speed match {
      case UltraBullet => PerfType.UltraBullet
      case HyperBullet | Bullet | HippoBullet => PerfType.Bullet
      case SuperBlitz | Blitz => PerfType.Blitz
      case Rapid => PerfType.Rapid
      case Classical => PerfType.Classical
    }
  }

  sealed trait Season
  object Season {
    case object Spring extends Season
    case object Summer extends Season
    case object Autumn extends Season
    case object Winter extends Season
  }

  private[tournament] def durationFor(s: Schedule): Int = {
    import Freq._, Speed._
    import draughts.variant._

    (s.freq, s.variant, s.speed) match {

      case (Hourly, _, UltraBullet | HyperBullet | Bullet) => 27
      case (Hourly, _, HippoBullet | SuperBlitz | Blitz) => 57
      case (Hourly, _, Rapid) if s.hasMaxRating => 57
      case (Hourly, _, Rapid | Classical) => 117

      case (Daily, Russian | Brazilian | Antidraughts | Frysk, _) => 90

      case (Daily | Eastern, Standard, SuperBlitz) => 120
      case (Daily | Eastern, Standard, Blitz) => 120
      case (Daily | Eastern, _, Blitz) => 90
      case (Daily | Eastern, _, Rapid | Classical) => 150
      case (Daily | Eastern, _, _) => 60

      case (Weekly, _, UltraBullet | HyperBullet | Bullet) => 60 * 2
      case (Weekly, _, HippoBullet | SuperBlitz | Blitz) => 60 * 3
      case (Weekly, _, Rapid) => 60 * 4
      case (Weekly, _, Classical) => 60 * 5

      case (Weekend, _, UltraBullet | HyperBullet | Bullet) => 90
      case (Weekend, _, HippoBullet | SuperBlitz) => 60 * 2
      case (Weekend, _, Blitz) => 60 * 3
      case (Weekend, _, Rapid) => 60 * 4
      case (Weekend, _, Classical) => 60 * 5

      case (Monthly, _, UltraBullet) => 60 * 2
      case (Monthly, _, HyperBullet | Bullet) => 60 * 3
      case (Monthly, _, HippoBullet | SuperBlitz) => 60 * 3 + 30
      case (Monthly, _, Blitz) => 60 * 4
      case (Monthly, _, Rapid) => 60 * 5
      case (Monthly, _, Classical) => 60 * 6

      case (Shield, _, UltraBullet) => 60 * 3
      case (Shield, _, HyperBullet | Bullet) => 60 * 4
      case (Shield, _, HippoBullet | SuperBlitz) => 60 * 5
      case (Shield, _, Blitz) => 60 * 6
      case (Shield, _, Rapid) => 60 * 8
      case (Shield, _, Classical) => 60 * 10

      case (Yearly, _, UltraBullet | HyperBullet | Bullet) => 60 * 4
      case (Yearly, _, HippoBullet | SuperBlitz) => 60 * 5
      case (Yearly, _, Blitz) => 60 * 6
      case (Yearly, _, Rapid) => 60 * 8
      case (Yearly, _, Classical) => 60 * 10

      case (Marathon, _, _) => 60 * 24 // lol
      case (ExperimentalMarathon, _, _) => 60 * 4

      case (Unique, _, _) => 60 * 6
    }
  }

  private val timeZoneCET = DateTimeZone.forID("Europe/Amsterdam")
  def offsetCET(day: DateTime) = day.withTime(12, 0, 0, 0).withZone(timeZoneCET).getHourOfDay - 12

  private val dailyIncDays = Set(dt.MONDAY, dt.WEDNESDAY, dt.SATURDAY)
  private def dailyInc(s: Schedule) = dailyIncDays.contains(s.at.getDayOfWeek)

  private def standardInc(s: Schedule) = s.at.getHourOfDay % 3 == 1
  private def variantInc(s: Schedule) = s.at.getHourOfDay % 12 == 1 || s.at.getHourOfDay % 12 == 2
  private def bulletInc(s: Schedule) = s.at.getHourOfDay % 3 == 0
  private def bullet64Inc(s: Schedule) = s.at.getHourOfDay % 6 == 2
  private def blitz64Inc(s: Schedule) = s.at.getHourOfDay % 12 == 0 || s.at.getHourOfDay % 12 == 1

  private[tournament] def clockFor(s: Schedule) = {
    import Freq._, Speed._
    import draughts.variant._

    val TC = draughts.Clock.Config

    (s.freq, s.variant, s.speed) match {
      // Special cases.
      case (Daily | Eastern, Standard, SuperBlitz) if dailyInc(s) => TC(3 * 60, 2)
      case (Daily | Eastern, Standard, Bullet) if !dailyInc(s) => TC(60, 1)
      case (Daily, Antidraughts, SuperBlitz) => TC(3 * 60, 2)
      case (Weekly, Frisian, Blitz) => TC(5 * 60, 2)
      case (Hourly, Frisian | Antidraughts, Bullet) => TC(60, 1)
      case (Hourly, Standard, Bullet) if bulletInc(s) => TC(60, 1)
      case (Hourly, Brazilian | Russian, Bullet) if bullet64Inc(s) => TC(60, 1)
      case (Hourly, Brazilian | Russian, SuperBlitz) if blitz64Inc(s) => TC(3 * 60, 2)
      case (Hourly, Antidraughts | Frisian, SuperBlitz) if variantInc(s) => TC(3 * 60, 2)
      case (Hourly, Standard, SuperBlitz) if standardInc(s) => TC(3 * 60, 2)
      case (Shield, Standard, Bullet) => TC(60, 1)
      case (Shield, variant, Blitz) if variant.exotic => TC(3 * 60, 2)

      case (_, _, UltraBullet) => TC(15, 0)
      case (_, _, HyperBullet) => TC(30, 0)
      case (_, _, Bullet) => TC(60, 0)
      case (_, _, HippoBullet) => TC(2 * 60, 0)
      case (_, _, SuperBlitz) => TC(3 * 60, 0)
      case (_, _, Blitz) => TC(5 * 60, 0)
      case (_, _, Rapid) => TC(10 * 60, 0)
      case (_, _, Classical) => TC(20 * 60, 10)
    }
  }
  private[tournament] def addCondition(s: Schedule) =
    s.copy(conditions = conditionFor(s))

  private[tournament] def conditionFor(s: Schedule) =
    if (s.conditions.relevant) s.conditions
    else {
      import Freq._, Speed._

      val nbRatedGame = (s.freq, s.speed, s.variant) match {

        case (Hourly | Daily | Eastern, _, variant) if variant.exotic => 0
        case (Weekly | Weekend | Monthly | Yearly, _, variant) if variant.exotic => 0

        case (Hourly | Daily | Eastern, HyperBullet | Bullet, _) => 10
        case (Hourly | Daily | Eastern, HippoBullet | SuperBlitz | Blitz, _) => 5
        case (Hourly | Daily | Eastern, Rapid, _) => 5

        case (Weekly | Weekend | Monthly | Yearly | Shield, HyperBullet | Bullet, _) => 15
        case (Weekly | Weekend | Monthly | Yearly | Shield, HippoBullet | SuperBlitz | Blitz, _) => 10
        case (Weekly | Weekend | Monthly | Yearly | Shield, Rapid, _) => 5

        case _ => 0
      }

      val minRating = s.freq match {
        case Weekend => 2200
        case _ => 0
      }

      Condition.All(
        nbRatedGame = nbRatedGame.some.filter(0<).map {
          Condition.NbRatedGame(s.perfType.some, _)
        },
        minRating = minRating.some.filter(0<).map {
          Condition.MinRating(s.perfType, _)
        },
        maxRating = none,
        titled = none,
        teamMember = none
      )
    }
}
