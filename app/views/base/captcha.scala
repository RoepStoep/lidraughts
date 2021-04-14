package views.html.base

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue

import controllers.routes

object captcha {

  private val dataCheckUrl = attr("data-check-url")
  private val dataMoves = attr("data-moves")
  private val dataPlayable = attr("data-playable")

  def apply(form: lidraughts.common.Form.FormLike, captcha: lidraughts.common.Captcha)(implicit ctx: Context) = frag(
    form3.hidden(form("gameId"), captcha.gameId.some),
    if (ctx.blind) form3.hidden(form("move"), captcha.solutions.head.some)
    else {
      val url = netBaseUrl + routes.Round.watcher(captcha.gameId, if (captcha.white) "white" else "black")
      div(
        cls := List(
          "captcha form-group" -> true,
          "is-invalid" -> lidraughts.common.Captcha.isFailed(form)
        ),
        dataCheckUrl := routes.Main.captchaCheck(captcha.gameId)
      )(
          div(cls := "challenge")(
            views.html.board.bits.mini(
              draughts.format.FEN(captcha.fen),
              draughts.variant.Standard.boardSize,
              draughts.Color(captcha.white)
            ) {
                div(
                  dataMoves := safeJsonValue(Json.toJson(captcha.moves)),
                  dataPlayable := 1
                )
              }
          ),
          div(cls := "captcha-explanation")(
            label(cls := "form-label")(trans.colorPlaysCapture(
              if (captcha.white) trans.white.txt() else trans.black.txt()
            )),
            br, br,
            trans.thisIsADraughtsCaptcha(),
            br,
            trans.clickOnTheBoardToMakeYourMove(),
            br, br,
            trans.help(),
            ": ",
            a(title := trans.viewTheSolution.txt(), target := "_blank", href := url)(url),
            div(cls := "result success text", dataIcon := "E")(trans.success()),
            div(cls := "result failure text", dataIcon := "k")(trans.notTheBestCapture()),
            form3.hidden(form("move"))
          )
        )
    }
  )
}
