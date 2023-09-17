package views.html
package practice

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue

import controllers.routes

object show {

  def apply(
    us: lidraughts.practice.UserStudy,
    data: lidraughts.practice.JsonView.JsData
  )(implicit ctx: Context) = views.html.base.layout(
    title = us.practiceStudy.name,
    moreCss = cssTag("analyse.practice"),
    moreJs = frag(
      analyseTag,
      analyseNvuiTag,
      embedJsUnsafe(s"""lidraughts=window.lidraughts||{};lidraughts.practice=${
        safeJsonValue(Json.obj(
          "practice" -> data.practice,
          "study" -> data.study,
          "data" -> data.analysis,
          "i18n" -> (board.userAnalysisI18n() ++ translations),
          "explorer" -> Json.obj(
            "endpoint" -> explorerEndpoint,
            "tablebaseEndpoint" -> tablebaseEndpoint
          )
        ))
      }""")
    ),
    draughtsground = false,
    zoomable = true
  ) {
      main(cls := "analyse")
    }

  private def translations(implicit lang: lidraughts.common.Lang) = i18nJsObject(List(
    trans.learn.goToNextExercise,
    trans.learn.loadNextExerciseImmediately,
    trans.learn.practiceList,
    trans.learn.backToPracticeMenu,
    trans.learn.clickToRetry,
    trans.learn.success,
    trans.learn.playALegalCapture,
    trans.learn.playAllLegalCapturesXRemaining,
    trans.learn.winTheGame,
    trans.learn.winTheGameInX,
    trans.learn.promoteAKing,
    trans.learn.promoteAKingInX,
    trans.learn.holdTheDrawForX,
    trans.learn.equalizeInX,
    trans.learn.getAWinningPositionInX,
    trans.learn.defendForX
  ))
}
