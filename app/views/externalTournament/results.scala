package views.html
package externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.externalTournament.ExternalTournament
import lidraughts.externalTournament.Cached.FinishedGames

import controllers.routes

object results {

  def apply(tour: ExternalTournament, results: FinishedGames)(implicit ctx: Context) =
    views.html.base.layout(
      title = tour.name,
      moreCss = cssTag("tournament.external.show")
    )(
        main(cls := "box")(
          h1(a(href := routes.ExternalTournament.show(tour.id))(tour.name)),
          table(cls := "slist slist-pad")(
            tbody(
              results.games.map { r =>
                tr(
                  td(cls := "date")(absClientDateTime(r.game.createdAt)),
                  td(cls := "players")(gameVsText(r.game, true)),
                  td(cls := "result")(draughts.Color.showResult(r.game.winnerColor, ctx.pref.draughtsResult))
                )
              }
            )
          )
        )
      )
}
