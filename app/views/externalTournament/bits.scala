package views.html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.i18n.{ I18nKeys => trans, I18nDb, JsDump }

object bits {

  def notFound()(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournamentNotFound.txt()
    ) {
        main(cls := "page-small box box-pad")(
          h1(trans.tournamentNotFound()),
          p(trans.tournamentDoesNotExist())
        )
      }

  def jsI18n(implicit ctx: Context) =
    i18nJsObject(i18nKeys) ++
      JsDump.keysToObject(i18nStudyKeys, I18nDb.Study, ctx.lang)

  private val i18nKeys = List(
    trans.join,
    trans.decline,
    trans.youArePlaying,
    trans.joinTheGame,
    trans.spectators
  )

  private val i18nStudyKeys = List(
    trans.study.noneYet
  )
}
