package lidraughts.anaCache

import reactivemongo.bson._

import lidraughts.db.dsl._

private object BSONHandlers {

  import AnaCacheEntry._

  implicit val PositionHandler = new BSONHandler[BSONString, Position] {
    def read(bs: BSONString): Position = bs.value split ':' match {
      case Array(fen) => Position(draughts.variant.Standard, SmallFen raw fen)
      case Array(variantId, fen) => Position(
        parseIntOption(variantId) flatMap draughts.variant.Variant.apply err s"Invalid anaCache variant $variantId",
        SmallFen raw fen
      )
      case _ => sys error s"Invalid anaCache position ${bs.value}"
    }
    def write(x: Position) = BSONString {
      if (x.variant.standard || x.variant == draughts.variant.FromPosition) x.smallFen.value
      else s"${x.variant.id}:${x.smallFen.value}"
    }
  }

  implicit val entryHandler = Macros.handler[AnaCacheEntry]
}
