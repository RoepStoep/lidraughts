package lidraughts.app
package templating

import play.api.libs.json.JsObject

import lidraughts.common.Lang
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.i18n.{ I18nDb, JsDump, JsQuantity, LangList, TimeagoLocales, Translated, Translator }
import lidraughts.user.UserContext

trait I18nHelper extends UserContext.ToLang {

  def transKey(key: String, db: I18nDb.Ref, args: Seq[Any] = Nil)(implicit lang: Lang): Frag =
    Translator.frag.literal(key, db, args, lang)

  def i18nJsObject(keys: Seq[Translated])(implicit lang: Lang): JsObject =
    JsDump.keysToObject(keys, lang)

  def i18nOptionJsObject(keys: Option[Translated]*)(implicit lang: Lang): JsObject =
    JsDump.keysToObject(keys.flatten, lang)

  def i18nFullDbJsObject(db: I18nDb.Ref)(implicit lang: Lang): JsObject =
    JsDump.dbToObject(db, lang)

  def timeagoLocaleScript(implicit ctx: lidraughts.api.Context): String = {
    TimeagoLocales.js.get(ctx.lang.code) orElse
      TimeagoLocales.js.get(ctx.lang.language) getOrElse
      ~TimeagoLocales.js.get("en")
  }

  def langName = LangList.nameByStr _

  def shortLangName(str: String) = langName(str).takeWhile(','!=)
}
