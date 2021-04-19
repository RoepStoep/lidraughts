package lidraughts.setup

import org.joda.time.DateTime
import scala.collection.breakOut

import draughts.Clock
import draughts.format.{ FEN, Forsyth }
import draughts.variant.FromPosition
import lidraughts.lobby.Color
import lidraughts.rating.PerfType
import lidraughts.game.PerfPicker

case class ApiConfig(
    variant: draughts.variant.Variant,
    clock: Option[Clock.Config],
    days: Option[Int],
    rated: Boolean,
    color: Color,
    position: Option[FEN] = None,
    opponent: Option[String] = None,
    startsAt: Option[DateTime] = None,
    microMatch: Boolean
) extends {

  val strictFen = false

  def >> = (variant.key.some, clock, days, rated, color.name.some, position.map(_.value), opponent, startsAt, microMatch option true).some

  def perfType: Option[PerfType] = PerfPicker.perfType(draughts.Speed(clock), variant, days)

  def realVariant = if (position.isDefined && validVariantForFen) FromPosition else variant

  def fenVariant = realVariant.fromPosition ?? {
    if (variant.fromPosition) draughts.variant.Standard
    else variant
  }.some

  def isFromPositionVariant = variant.fromPosition || Config.fromPositionVariants.contains(variant.id)

  def validVariantForFen = position.isEmpty || isFromPositionVariant

  def validFen = !isFromPositionVariant || (position.isEmpty && !variant.fromPosition) || {
    position ?? { f => ~Forsyth.<<<@(variant, f.value).map(_.situation playable strictFen) }
  }

  def mode = draughts.Mode(rated)
}

object ApiConfig extends BaseHumanConfig {

  lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).map(60*)(breakOut)

  def <<(v: Option[String], cl: Option[Clock.Config], d: Option[Int], r: Boolean, c: Option[String], pos: Option[String], opp: Option[String], start: Option[DateTime], mm: Option[Boolean]) =
    new ApiConfig(
      variant = draughts.variant.Variant.orDefault(~v),
      clock = cl,
      days = d,
      rated = r,
      color = Color.orDefault(~c),
      position = pos map FEN,
      opponent = opp,
      startsAt = start,
      microMatch = ~mm
    )
}
