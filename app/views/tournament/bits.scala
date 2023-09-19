package views.html.tournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.i18n.{ I18nKeys => trans }
import lidraughts.tournament.Tournament

import controllers.routes

object bits {

  def notFound()(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournamentNotFound.txt()
    ) {
        main(cls := "page-small box box-pad")(
          h1(trans.tournamentNotFound()),
          p(trans.tournamentDoesNotExist()),
          p(trans.tournamentMayHaveBeenCanceled()),
          br,
          br,
          a(href := routes.Tournament.home())(trans.returnToTournamentsHomepage())
        )
      }

  def enterable(tours: List[Tournament]) =
    table(cls := "tournaments")(
      tours map { tour =>
        tr(
          td(cls := "name")(
            a(cls := "text", dataIcon := tournamentIconChar(tour), href := routes.Tournament.show(tour.id))(tour.name)
          ),
          td(momentFromNow(tour.startsAt)),
          td(tour.durationString),
          td(dataIcon := "r", cls := "text")(tour.nbPlayers)
        )
      }
    )

  def jsI18n(implicit ctx: Context) = i18nJsObject(translations)

  private val translations = List(
    trans.standing,
    trans.starting,
    trans.tournamentIsStarting,
    trans.youArePlaying,
    trans.standByX,
    trans.tournamentPairingsAreNowClosed,
    trans.join,
    trans.withdraw,
    trans.joinTheGame,
    trans.signIn,
    trans.averageElo,
    trans.arena.averagePerformance,
    trans.arena.averageScore,
    trans.teamPage,
    trans.arena.viewAllXTeams,
    trans.pickYourTeam,
    trans.whichTeamWillYouRepresent,
    trans.youMustJoinOneOfTheseTeams,
    trans.players,
    trans.gamesPlayed,
    trans.nbPlayers,
    trans.winRate,
    trans.berserkRate,
    trans.performance,
    trans.tournamentComplete,
    trans.movesPlayed,
    trans.whiteWins,
    trans.blackWins,
    trans.draws,
    trans.nextXTournament,
    trans.averageOpponent,
    trans.ratedTournament,
    trans.casualTournament,
    trans.password,
    trans.thematic,
    trans.startingIn
  )
}
