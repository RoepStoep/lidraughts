package lidraughts.anaCache

import play.api.libs.json._

import draughts.format.FEN
import lidraughts.common.PimpedJson._
import lidraughts.user.User

object JsonHandlers {
  import AnaCacheEntry._

  def readPut(o: JsObject): Option[Input.Candidate] = for {
    d <- o obj "d"
    variant = draughts.variant.Variant orDefault ~d.str("variant")
    fen <- d str "fen"
  } yield Input.Candidate(variant, fen)
}
