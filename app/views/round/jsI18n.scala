package views.html.round

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.i18n.{ I18nKeys => trans }

object jsI18n {

  def apply(g: lidraughts.game.Game)(implicit ctx: Context) = i18nJsObject {
    baseTranslations ++ {
      if (g.isCorrespondence) correspondenceTranslations
      else realtimeTranslations
    } ++ {
      g.variant.exotic ?? variantTranslations
    } ++ {
      g.isTournament ?? tournamentTranslations
    } ++ {
      g.isSwiss ?? swissTranslations
    } ++ {
      g.isSimul ?? simulTranslations
    } ++ {
      g.metadata.drawLimit.isDefined ?? drawLimitTranslations
    } ++ {
      g.metadata.microMatch.isDefined ?? microMatchTranslations
    }
  }

  private val correspondenceTranslations = Vector(
    trans.oneDay,
    trans.nbDays,
    trans.nbHours
  )

  private val realtimeTranslations = Vector(
    trans.nbSecondsToPlayTheFirstMove
  )

  private val drawLimitTranslations = Vector(
    trans.drawOffersAfterX,
    trans.drawOffersNotAllowed
  )

  private val variantTranslations = Vector(
    trans.promotion,
    trans.variantEnding
  )

  private val microMatchTranslations = Vector(
    trans.microMatchRematchAwaiting
  )

  private val tournamentTranslations = Vector(
    trans.backToTournament,
    trans.viewTournament,
    trans.standing
  )

  private val simulTranslations = Vector(
    trans.nbVictories,
    trans.nbDraws,
    trans.nbGames,
    trans.nbGamesOngoing,
    trans.ongoing,
    trans.succeeded,
    trans.failed,
    trans.simulTimeOut,
    trans.simulTimeOutExplanation,
    trans.targetWinningPercentage,
    trans.currentWinningPercentage,
    trans.relativeScoreRequired,
    trans.nbMinutes
  )

  private val swissTranslations = Vector(
    trans.backToTournament,
    trans.viewTournament
  )

  private val baseTranslations = Vector(
    trans.flipBoard,
    trans.aiNameLevelAiLevel,
    trans.youPlayTheWhitePieces,
    trans.youPlayTheBlackPieces,
    trans.itsYourTurn,
    trans.yourTurn,
    trans.abortGame,
    trans.proposeATakeback,
    trans.offerDraw,
    trans.resign,
    trans.opponentLeftChoices,
    trans.forceResignation,
    trans.forceDraw,
    trans.threefoldRepetition,
    trans.claimADraw,
    trans.drawOfferSent,
    trans.cancel,
    trans.yourOpponentOffersADraw,
    trans.accept,
    trans.decline,
    trans.takebackPropositionSent,
    trans.yourOpponentProposesATakeback,
    trans.thisPlayerUsesDraughtsComputerAssistance,
    trans.gameAborted,
    trans.whiteResigned,
    trans.blackResigned,
    trans.whiteLeftTheGame,
    trans.blackLeftTheGame,
    trans.draw,
    trans.timeOut,
    trans.whiteIsVictorious,
    trans.blackIsVictorious,
    trans.withdraw,
    trans.rematch,
    trans.rematchOfferSent,
    trans.rematchOfferAccepted,
    trans.waitingForOpponent,
    trans.cancelRematchOffer,
    trans.newOpponent,
    trans.preferences.moveConfirmation,
    trans.viewRematch,
    trans.whitePlays,
    trans.blackPlays,
    trans.giveNbSeconds,
    trans.preferences.giveMoreTime,
    trans.gameOver,
    trans.analysis,
    trans.yourOpponentWantsToPlayANewGameWithYou,
    trans.backToSimul,
    trans.xComplete
  )
}
