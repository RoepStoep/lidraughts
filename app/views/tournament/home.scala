package views.html.tournament

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.tournament.Tournament

import controllers.routes

object home {

  def apply(
    scheduled: List[Tournament],
    finished: lidraughts.common.paginator.Paginator[Tournament],
    winners: lidraughts.tournament.AllWinners,
    json: play.api.libs.json.JsObject
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournaments.txt(),
      moreCss = cssTag("tournament.home"),
      wrapClass = "full-screen-force",
      moreJs = frag(
        infiniteScrollTag,
        jsAt(s"compiled/lidraughts.tournamentSchedule${isProd ?? (".min")}.js"),
        embedJsUnsafe(s"""var app=LidraughtsTournamentSchedule.app(document.querySelector('.tour-chart'), ${
          safeJsonValue(Json.obj(
            "data" -> json,
            "i18n" -> bits.jsI18n
          ))
        });
var d=lidraughts.StrongSocket.defaults;d.params.flag="tournament";d.events.reload=app.update;""")
      ),
      openGraph = lidraughts.app.ui.OpenGraph(
        url = s"$netBaseUrl${routes.Tournament.home().url}",
        title = trans.tournamentHomeTitle.txt(),
        description = trans.tournamentHomeDescription.txt()
      ).some
    ) {
        main(cls := "tour-home")(
          st.aside(cls := "tour-home__side")(
            h2(
              a(href := routes.Tournament.leaderboard)(trans.leaderboard())
            ),
            ul(cls := "leaderboard")(
              winners.top.map { w =>
                li(
                  userIdLink(w.userId.some),
                  a(title := w.tourName, href := routes.Tournament.show(w.tourId))(scheduledTournamentNameShortHtml(w.tourName))
                )
              }
            ),
            p(cls := "tour__links")(
              ctx.me map { me =>
                frag(
                  a(href := routes.UserTournament.path(me.username, "created"))(trans.arena.myTournaments()),
                  br
                )
              },
              a(href := "/tournament/calendar")(trans.tournamentCalendar()),
              br,
              a(href := routes.Tournament.help("arena".some))(trans.tournamentFAQ())
            ),
            h2(trans.lidraughtsTournaments()),
            div(cls := "scheduled")(
              scheduled.map { tour =>
                tour.schedule.filter(s => s.freq != lidraughts.tournament.Schedule.Freq.Hourly) map { s =>
                  a(href := routes.Tournament.show(tour.id), dataIcon := tournamentIconChar(tour))(
                    strong(tour.name),
                    momentFromNow(s.at)
                  )
                }
              }
            )
          ),
          st.section(cls := "tour-home__schedule box")(
            div(cls := "box__top")(
              h1(trans.tournaments()),
              ctx.isAuth option div(cls := "box__top__actions")(a(
                href := routes.Tournament.form(),
                cls := "button button-green text",
                dataIcon := "O"
              )(trans.createANewTournament()))
            ),
            div(cls := "tour-chart")
          ),
          div(cls := "tour-home__list box")(
            table(cls := "slist")(
              thead(
                tr(
                  th(colspan := 2, cls := "large")(trans.finished()),
                  th(trans.duration()),
                  th(trans.winner()),
                  th(trans.players())
                )
              ),
              finishedPaginator(finished)
            )
          )
        )
      }
}
