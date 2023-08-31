package views.html.user.show

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.user.User

import controllers.routes

object newPlayer {

  def apply(u: User)(implicit ctx: Context) =
    div(cls := "new-player")(
      h2(trans.welcomeToLidraughts()),
      p(
        trans.thisIsYourProfilePage(),
        u.profile.isEmpty option frag(
          br,
          trans.wouldYouLikeToX(
            a(href := routes.Account.profile)(trans.improveIt())
          )
        )
      ),
      p(
        if (u.kid) trans.kidModeIsEnabled()
        else trans.enabledKidModeSuggestion(
          a(href := routes.Account.kid)(trans.kidMode())
        )
      ),
      p(trans.whatNowSuggestions()),
      ul(
        li(a(href := routes.Puzzle.home)(trans.improveWithDraughtsTacticsPuzzles())),
        li(a(href := s"${routes.Lobby.home}#ai")(trans.playTheAI())),
        li(a(href := s"${routes.Lobby.home}#hook")(trans.playOpponentsWorldwide())),
        li(a(href := routes.User.list)(trans.followFriends())),
        li(a(href := routes.Tournament.home(1))(trans.playInTournaments())),
        li(a(href := routes.Study.allDefault(1))(trans.learnFromStudies())),
        li(a(href := routes.Pref.form("game-display"))(trans.configureLidraughts())),
        li(trans.exploreTheSiteAndHaveFun())
      )
    )
}
