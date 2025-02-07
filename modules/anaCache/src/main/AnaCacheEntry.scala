package lidraughts.anaCache

import org.joda.time.DateTime
import reactivemongo.bson._

import draughts.format.{ Forsyth, FEN }
import draughts.variant.Variant
import lidraughts.common.IpAddress
import lidraughts.user.User

case class AnaCacheEntry(
    _id: BSONObjectID,
    pos: AnaCacheEntry.Position,
    userId: Option[User.ID],
    ip: IpAddress,
    createdAt: DateTime
) {

}

object AnaCacheEntry {

  def makeEntry(input: Input, userId: Option[User.ID], ip: IpAddress) = AnaCacheEntry(
    BSONObjectID.generate,
    input.pos,
    userId,
    ip,
    DateTime.now
  )

  final class SmallFen private (val value: String) extends AnyVal with StringValue

  object SmallFen {
    private[anaCache] def raw(str: String) = new SmallFen(str)
    def make(variant: Variant, fen: FEN): SmallFen = {
      val base = Forsyth.<<@(variant, fen.value).fold(fen.value.split(':').take(3).mkString("").filter { c => c != 'W' }) { sit =>
        val boardStr = Forsyth.compressedBoard(sit.board)
        sit.color.fold(boardStr, "0" + boardStr)
      }
      val str = if (variant.frisianVariant) base + ~fen.value.split(':').lift(5) else base
      new SmallFen(str)
    }
    def validate(variant: Variant, fen: FEN): Option[SmallFen] =
      Forsyth.<<@(variant, fen.value).exists(_ playable true) option make(variant, fen)
  }

  case class Position(variant: Variant, smallFen: SmallFen)

  case class Input(pos: Position, fen: FEN)

  object Input {
    case class Candidate(variant: Variant, fen: String) {
      def input = SmallFen.validate(variant, FEN(fen)) map { smallFen =>
        Input(Position(variant, smallFen), FEN(fen))
      }
    }
  }
}
