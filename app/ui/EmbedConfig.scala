package lidraughts.app
package ui

import play.api.mvc.RequestHeader

import lidraughts.common.{ Nonce, Lang }

case class EmbedConfig(bg: String, board: String, lang: Lang, req: RequestHeader, nonce: Nonce)

object EmbedConfig {

  object implicits {
    implicit def configLang(implicit config: EmbedConfig): Lang = config.lang
    implicit def configReq(implicit config: EmbedConfig): RequestHeader = config.req
  }

  def apply(req: RequestHeader): EmbedConfig = EmbedConfig(
    bg = get("bg", req).filterNot("auto" ==) | "light",
    board = lidraughts.pref.Theme(~get("theme", req)).cssClass,
    lang = lidraughts.i18n.I18nLangPicker(req, none),
    req = req,
    nonce = Nonce.random
  )

  private def get(name: String, req: RequestHeader): Option[String] =
    req.queryString get name flatMap (_.headOption) filter (_.nonEmpty)
}
