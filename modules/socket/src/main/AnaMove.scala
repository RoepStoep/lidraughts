package lidraughts.socket

import draughts.format.{ Uci, UciCharPair }
import draughts.opening._
import draughts.variant.Variant
import play.api.libs.json._
import scalaz.Validation.FlatMap._
import lidraughts.tree.{ Branch, Node }

trait AnaAny {
  def branch: Valid[Branch]
  def json(b: Branch, applyAmbiguity: Int = 0): JsObject
  def chapterId: Option[String]
  def path: String
}

case class AnaMove(
    orig: draughts.Pos,
    dest: draughts.Pos,
    variant: Variant,
    fen: String,
    path: String,
    chapterId: Option[String],
    puzzle: Option[Boolean],
    uci: Option[String],
    fullCapture: Option[Boolean] = None
) extends AnaAny {

  private lazy val fullCaptureFields = uci.flatMap(Uci.Move.apply).flatMap(_.capture)
  private lazy val newGame = draughts.DraughtsGame(variant.some, fen.some).apply(
    orig = orig,
    dest = dest,
    finalSquare = fullCaptureFields.isDefined,
    captures = fullCaptureFields,
    partialCaptures = ~fullCapture
  )

  def branch: Valid[Branch] = {
    newGame flatMap {
      case (game, move) => {
        game.pdnMoves.lastOption toValid "Moved but no last move!" map { san =>
          val uci = Uci(move, fullCaptureFields.isDefined)
          val movable = game.situation playable false
          val fen = draughts.format.Forsyth >> game
          val sit = game.situation
          val captLen = if (sit.ghosts > 0) sit.captureLengthFrom(dest) else sit.allMovesCaptureLength.some
          val validMoves = AnaDests.validMoves(sit, game.situation.ghosts > 0 option dest, ~fullCapture)
          val truncatedMoves = (~fullCapture && ~captLen > 1) option AnaDests.truncateMoves(validMoves)
          val truncatedDests = truncatedMoves.map { _ mapValues { _ flatMap (uci => variant.boardSize.pos.posAt(uci.takeRight(2))) } }
          val dests = truncatedDests.getOrElse(validMoves mapValues { _ map (_.dest) })
          val destsUci = truncatedMoves.map(_.values.toList.flatten)
          val alternatives = (~puzzle && sit.ghosts == 0 && ~captLen > 1) option {
            game.situation.validMovesFinal.values.toList.flatMap(_.map { m =>
              Node.Alternative(
                uci = m.toUci.uci,
                fen = draughts.format.Forsyth.exportBoard(m.after)
              )
            }).take(100)
          }
          Branch(
            id = UciCharPair(uci),
            ply = game.turns,
            move = Uci.WithSan(uci, san),
            fen = fen,
            dests = movable option dests,
            destsUci = movable ?? destsUci,
            captureLength = movable ?? captLen,
            opening = (game.turns <= 30 && Variant.openingSensibleVariants(variant)) ?? {
              FullOpeningDB findByFen fen
            },
            drops = if (movable) game.situation.drops else Some(Nil),
            alternatives = alternatives
          )
        }
      }
    }
  }

  def draw = newGame.toOption flatMap {
    case (game, _) => game.situation.autoDraw.some
  }

  def json(b: Branch, applyAmbiguity: Int = 0): JsObject = {
    val node = Node.fullUciNodeJsonWriter.writes(if (applyAmbiguity != 0) b.copy(id = UciCharPair(b.id.a, applyAmbiguity)) else b)
    val withDraw = node.asOpt[JsObject] match {
      case Some(obj) => obj.add("draw" -> draw)
      case _ => node
    }
    Json.obj(
      "node" -> withDraw,
      "path" -> path
    ).add("ch" -> chapterId)
  }
}

object AnaMove {

  def parse(o: JsObject) = for {
    d ← o obj "d"
    v ← Some(draughts.variant.Variant orDefault ~d.str("variant"))
    orig ← d str "orig" flatMap v.boardSize.pos.posAt
    dest ← d str "dest" flatMap v.boardSize.pos.posAt
    fen ← d str "fen"
    path ← d str "path"
  } yield AnaMove(
    orig = orig,
    dest = dest,
    variant = v,
    fen = fen,
    path = path,
    chapterId = d str "ch",
    puzzle = d boolean "puzzle",
    uci = d str "uci",
    fullCapture = d boolean "fullCapture"
  )
}
