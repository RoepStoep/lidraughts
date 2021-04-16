package views.html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.i18n.{ I18nKeys => trans }

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

  def jsI18n(implicit ctx: Context) = i18nJsObject(i18nKeys)

  private val i18nKeys = List(
    trans.join,
    trans.decline,
    trans.youArePlaying,
    trans.joinTheGame,
    trans.none,
    trans.points,
    trans.winRate,
    trans.averageOpponent,
    trans.recentlyFinished,
    trans.spectators
  )
}
