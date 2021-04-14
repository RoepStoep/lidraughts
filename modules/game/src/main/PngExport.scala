package lidraughts.game

import play.api.libs.iteratee._
import play.api.libs.ws.WS
import play.api.Play.current

import draughts.format.{ Forsyth, FEN, Uci }
import draughts.variant.Variant

final class PngExport(url: String, size: Int) {

  def fromGame(game: Game): Fu[Enumerator[Array[Byte]]] = apply(
    fen = FEN(Forsyth >> game.draughts),
    variant = game.variant,
    lastMove = game.lastMoveUci,
    orientation = game.naturalOrientation.some,
    logHint = s"game ${game.id}"
  )

  def apply(
    fen: FEN,
    variant: Variant,
    lastMove: Option[String],
    orientation: Option[draughts.Color],
    logHint: => String
  ): Fu[Enumerator[Array[Byte]]] = {

    val queryString = List(
      "fen" -> fen.value.takeWhile(' ' !=),
      "boardSize" -> variant.boardSize.width.toString,
      "size" -> size.toString
    ) ::: List(
        lastMove.map { "lastMove" -> _ },
        orientation.map { "orientation" -> _.name }
      ).flatten

    WS.url(url).withQueryString(queryString: _*).getStream() flatMap {
      case (res, body) if res.status != 200 =>
        logger.warn(s"PngExport $logHint ${fen.value} ${res.status}")
        fufail(res.status.toString)
      case (_, body) => fuccess(body)
    }
  }
}
