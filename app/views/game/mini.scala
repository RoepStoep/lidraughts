package views.html.game

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.Pov

import controllers.routes
import draughts.format.Forsyth

object mini {

  private val dataLive = attr("data-live")
  private val dataState = attr("data-state")
  private val dataTime = attr("data-time")
  private val dataUserId = attr("data-userid")
  private val cgWrap = span(cls := "cg-wrap")(cgWrapContent)

  def apply(
    pov: Pov,
    ownerLink: Boolean = false,
    tv: Boolean = false,
    withLink: Boolean = true,
    withUserId: Boolean = false
  )(implicit ctx: Context): Tag = {
    val game = pov.game
    val isLive = game.isBeingPlayed
    val tag = if (withLink) a else span
    val boardSize = game.variant.boardSize
    tag(
      href := withLink.option(gameLink(game, pov.color, ownerLink, tv)),
      cls := s"mini-game mini-game-${game.id} mini-game--init ${game.variant.key} is2d is${boardSize.key}",
      dataLive := isLive.option(game.id),
      renderState(pov)
    )(
        renderPlayer(!pov, ctx.pref.draughtsResult, withUserId),
        cgWrap,
        renderPlayer(pov, ctx.pref.draughtsResult, withUserId)
      )
  }

  def noCtx(pov: Pov, tv: Boolean = false, blank: Boolean = false): Frag = {
    val game = pov.game
    val isLive = game.isBeingPlayed
    val boardSize = game.variant.boardSize
    a(
      href := (if (tv) routes.Tv.index() else routes.Round.watcher(pov.gameId, pov.color.name)),
      target := blank.option("_blank"),
      title := gameTitle(pov.game, pov.color),
      cls := s"mini-game mini-game-${game.id} mini-game--init is2d is${boardSize.key} ${isLive ?? "mini-game--live"} ${game.variant.key}",
      dataLive := isLive.option(game.id),
      renderState(pov)
    )(
        renderPlayer(!pov, lidraughts.pref.Pref.default.draughtsResult),
        cgWrap,
        renderPlayer(pov, lidraughts.pref.Pref.default.draughtsResult)
      )
  }

  private def renderState(pov: Pov) = {
    val boardSize = pov.game.variant.boardSize
    dataState := s"${Forsyth.boardAndColor(pov.game.situation)}|${boardSize.width}x${boardSize.height}|${pov.color.name}|${~pov.game.lastMoveKeys}"
  }

  private def renderPlayer(pov: Pov, draughtsResult: Boolean, withUserId: Boolean = false) =
    span(cls := "mini-game__player")(
      span(cls := s"mini-game__user mini-game__user--${pov.color.name}", dataUserId := withUserId ?? pov.player.userId)(
        playerUsername(pov.player, withRating = false),
        span(cls := "rating")(lidraughts.game.Namer ratingStringIfUser pov.player)
      ),
      if (pov.game.finishedOrAborted) renderResult(pov, draughtsResult)
      else pov.game.clock.map { renderClock(_, pov.color) }
    )

  private def renderResult(pov: Pov, draughtsResult: Boolean) =
    span(cls := "mini-game__result")(
      pov.game.winnerColor.fold(if (draughtsResult) "1" else "½") { c =>
        if (c == pov.color) {
          if (draughtsResult) "2" else "1"
        } else "0"
      }
    )

  private def renderClock(clock: draughts.Clock, color: draughts.Color) = {
    val s = clock.remainingTime(color).roundSeconds
    span(
      cls := s"mini-game__clock mini-game__clock--${color.name}",
      dataTime := s
    )(
        f"${s / 60}:${s % 60}%02d"
      )
  }
}
