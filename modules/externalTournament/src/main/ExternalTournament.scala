package lidraughts.externalTournament

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
    variant: Variant
) {

  def id = _id

  def speed = Speed(clock)

  def perfType: Option[PerfType] = PerfPicker.perfType(speed, variant, none)
  def perfLens = PerfPicker.mainOrDefault(speed, variant, none)
}

object ExternalTournament {

  type ID = String

  private[externalTournament] def make(
    name: String,
    clock: Option[ClockConfig],
    days: Option[Int],
    userId: User.ID,
    variant: Variant
  ): ExternalTournament = new ExternalTournament(
    _id = ornicar.scalalib.Random nextString 8,
    name = name,
    createdBy = userId,
    clock = clock,
    days = days,
    variant = variant
  )
}
