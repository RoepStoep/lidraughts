package views.html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.i18n.{ I18nKeys => trans, I18nDb, JsDump }

object bits {

  def jsI18n(implicit ctx: Context) =
    i18nJsObject(i18nKeys) ++
      JsDump.keysToObject(i18nStudyKeys, I18nDb.Study, ctx.lang)

  private val i18nKeys = List(
    trans.spectators
  )

  private val i18nStudyKeys = List(
    trans.study.noneYet
  )
}
