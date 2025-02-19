package views.html.challenge

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.challenge.Challenge.Status

import controllers.routes

object theirs {

  def apply(
    c: lidraughts.challenge.Challenge,
    json: play.api.libs.json.JsObject,
    user: Option[lidraughts.user.User]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, false),
      moreCss = cssTag("challenge.page")
    ) {
        main(cls := "page-small challenge-page challenge-theirs box box-pad")(
          c.status match {
            case Status.Created | Status.External | Status.Offline => frag(
              h1(user.fold[Frag](trans.anonymous())(u =>
                frag(
                  userLink(u),
                  " (", u.perfs(c.perfType).glicko.display, ")"
                ))),
              bits.details(c),
              c.notableInitialFen.map { fen =>
                div(cls := "board-preview", views.html.board.bits.mini(fen, c.variant.boardSize, !c.finalColor)(div))
              },
              if (!c.mode.rated || ctx.isAuth) frag(
                (c.mode.rated && c.unlimited) option
                  badTag(trans.bewareTheGameIsRatedButHasNoClock()),
                postForm(cls := "accept", action := routes.Challenge.accept(c.id))(
                  submitButton(cls := "text button button-fat", dataIcon := "G")(trans.joinTheGame())
                )
              )
              else frag(
                hr,
                badTag(
                  p(trans.thisGameIsRated()),
                  p(trans.youMustSignInToJoinIt(
                    a(cls := "button", href := s"${routes.Auth.login}?referrer=${routes.Round.watcher(c.id, "white")}")(trans.signIn())
                  ))
                )
              )
            )
            case Status.Declined => div(cls := "follow-up")(
              h1(trans.challenge.challengeDeclined()),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
            case Status.Accepted => div(cls := "follow-up")(
              h1(trans.challenge.challengeAccepted()),
              bits.details(c),
              a(id := "challenge-redirect", href := routes.Round.watcher(c.id, "white"), cls := "button button-fat")(
                trans.joinTheGame()
              )
            )
            case Status.Canceled => div(cls := "follow-up")(
              h1(trans.challenge.challengeCanceled()),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
          }
        )
      }
}
