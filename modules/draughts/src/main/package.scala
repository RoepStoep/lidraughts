import ornicar.scalalib

import scala.util.Try

package object draughts

  extends scalalib.Validation
  with scalalib.Common
  with scalalib.OrnicarNonEmptyList

  with scalaz.syntax.std.ToBooleanOps

  with scalaz.std.OptionFunctions
  with scalaz.syntax.std.ToOptionOps
  with scalaz.syntax.std.ToOptionIdOps

  with scalaz.std.ListInstances
  with scalaz.syntax.std.ToListOps

  with scalaz.syntax.ToValidationOps
  with scalaz.syntax.ToFunctorOps
  with scalaz.syntax.ToIdOps {

  val White = Color.White
  val Black = Color.Black

  type Direction = (Int, PosMotion => Option[PosMotion])
  type Directions = List[Direction]

  type PieceMap = Map[Pos, Piece]

  type PositionHash = Array[Byte]

  object implicitFailures {
    implicit def stringToFailures(str: String): Failures = scalaz.NonEmptyList(str)
  }

  def parseIntOption(str: String): Option[Int] =
    Try(Integer.parseInt(str)).toOption

  private[draughts] def logger = draughtsLog("draughts")

}
