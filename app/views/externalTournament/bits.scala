package views.html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.i18n.{ I18nKeys => trans, I18nDb, JsDump }

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

  def jsI18n(implicit ctx: Context) = i18nJsObject(i18nKeys) ++
    JsDump.keysToObject(i18nExternalKeys, I18nDb.External, ctx.lang) ++
    JsDump.keysToObject(i18nSwissKeys, I18nDb.Swiss, ctx.lang) ++
    JsDump.keysToObject(i18nStudyKeys, I18nDb.Study, ctx.lang)

  private val i18nKeys = List(
    trans.join,
    trans.decline,
    trans.youArePlaying,
    trans.joinTheGame,
    trans.gamesPlayed,
    trans.none,
    trans.points,
    trans.winRate,
    trans.averageOpponent,
    trans.viewAllResults,
    trans.latestResults,
    trans.upcomingGames,
    trans.nowPlaying,
    trans.rating,
    trans.countryOrRegion,
    trans.draughtsTitle,
    trans.unknown,
    trans.username,
    trans.spectators,
    trans.microMatch,
    trans.microMatchWin,
    trans.microMatchLoss,
    trans.microMatchDraw
  )

  private val i18nExternalKeys = List(
    trans.external.youHaveBeenInvitedToPlay,
    trans.external.pleaseReviewTheFollowing,
    trans.external.youHaveBeenAssignedFmjdIdX,
    trans.external.toFmjdProfile,
    trans.external.contactTournamentOrganizerXIfNotCorrect,
    trans.external.yourPublicFmjdDataWillBeVisible,
    trans.external.yourGamesStartAutomatically,
    trans.external.invitedPlayers,
    trans.external.awaiting,
    trans.external.rejected
  )

  private val i18nSwissKeys = List(
    trans.swiss.roundX,
    trans.swiss.bye
  )

  private val i18nStudyKeys = List(
    trans.study.noneYet
  )
}
