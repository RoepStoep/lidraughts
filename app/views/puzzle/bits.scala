package views
package html.puzzle

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

object bits {

  private val dataLastmove = attr("data-lastmove")

  def daily(p: lidraughts.puzzle.Puzzle, fen: draughts.format.FEN, lastMove: String) =
    views.html.board.bits.mini(fen, p.variant.boardSize, p.color, lastMove)(span)

  def jsI18n()(implicit ctx: Context) = i18nJsObject(translations)

  private val translations = List(
    trans.training,
    trans.yourPuzzleRatingX,
    trans.goodMove,
    trans.butYouCanDoBetter,
    trans.bestMove,
    trans.keepGoing,
    trans.puzzleFailed,
    trans.butYouCanKeepTrying,
    trans.yourTurn,
    trans.findTheBestMoveForBlack,
    trans.findTheBestMoveForWhite,
    trans.viewTheSolution,
    trans.success,
    trans.fromGameLink,
    trans.boardEditor,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.wasThisPuzzleAnyGood,
    trans.pleaseVotePuzzle,
    trans.thankYou,
    trans.puzzleId,
    trans.ratingX,
    trans.hidden,
    trans.playedXTimes,
    trans.continueTraining,
    trans.retryThisPuzzle,
    trans.toTrackYourProgress,
    trans.signUp,
    trans.trainingSignupExplanation,
    trans.thisPuzzleIsCorrect,
    trans.thisPuzzleIsWrong,
    trans.puzzles,
    trans.analysis,
    trans.rated,
    trans.casual,
    // ceval
    trans.depthX,
    trans.usingServerAnalysis,
    trans.loadingEngine,
    trans.cloudAnalysis,
    trans.goDeeper,
    trans.showThreat,
    trans.gameOver,
    trans.inLocalBrowser,
    trans.toggleLocalEvaluation
  )
}
