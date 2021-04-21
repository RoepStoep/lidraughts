package lidraughts.round

import draughts.format.Forsyth
import draughts.variant._
import draughts.{ DraughtsGame, Board, Color => DraughtsColor, Clock, Situation }
import DraughtsColor.{ White, Black }
import com.github.blemale.scaffeine.Cache
import com.github.blemale.scaffeine.{ Cache, Scaffeine }
import scala.concurrent.duration._

import lidraughts.game.{ GameRepo, Game, Event, Progress, Pov, Source, AnonCookie, PerfPicker }
import lidraughts.memo.ExpireSetMemo
import lidraughts.user.{ User, UserRepo }

private[round] final class Rematcher(
    messenger: Messenger,
    onStart: Game.ID => Unit,
    rematches: Cache[Game.ID, Game.ID],
    bus: lidraughts.common.Bus
) {

  import Rematcher.Offers

  private val offers: Cache[Game.ID, Offers] = Scaffeine()
    .expireAfterWrite(30 minutes)
    .build[Game.ID, Offers]

  def isOffering(pov: Pov): Boolean = offers.getIfPresent(pov.gameId).exists(_(pov.color))

  def yes(pov: Pov): Fu[Events] = pov match {
    case Pov(game, color) if game playerCouldRematch color =>
      if (isOffering(!pov) || game.opponent(color).isAi)
        rematches.getIfPresent(game.id).fold(rematchJoin(pov.game))(rematchExists(pov))
      else fuccess(rematchCreate(pov))
    case _ => fuccess(List(Event.ReloadOwner))
  }

  def no(pov: Pov): Fu[Events] = {
    if (isOffering(pov)) messenger.system(pov.game, _.rematchOfferCanceled)
    else if (isOffering(!pov)) messenger.system(pov.game, _.rematchOfferDeclined)
    offers invalidate pov.game.id
    fuccess(List(Event.RematchOffer(by = none)))
  }

  def microRematch(game: Game): Fu[Events] =
    rematchJoin(game)

  private def rematchExists(pov: Pov)(nextId: Game.ID): Fu[Events] =
    GameRepo game nextId flatMap {
      _.fold(rematchJoin(pov.game))(g => fuccess(redirectEvents(g)))
    }

  private def rematchJoin(game: Game): Fu[Events] =
    rematches.getIfPresent(game.id) match {
      case None => for {
        nextGame ← returnGame(game) map (_.start)
        _ = offers invalidate game.id
        _ = rematches.put(game.id, nextGame.id)
        _ ← GameRepo insertDenormalized nextGame
      } yield {
        if (nextGame.metadata.isMicroRematch) messenger.system(game, _.microMatchRematchStarted)
        else messenger.system(game, _.rematchOfferAccepted)
        onStart(nextGame.id)
        redirectEvents(nextGame)
      }
      case Some(rematchId) => GameRepo game rematchId map { _ ?? redirectEvents }
    }

  private def rematchCreate(pov: Pov): Events = {
    messenger.system(pov.game, _.rematchOfferSent)
    pov.opponent.userId foreach { forId =>
      bus.publish(lidraughts.hub.actorApi.round.RematchOffer(pov.gameId), Symbol(s"rematchFor:$forId"))
    }
    offers.put(pov.gameId, Offers(white = pov.color.white, black = pov.color.black))
    List(Event.RematchOffer(by = pov.color.some))
  }

  private def nextMicroMatch(g: Game) =
    if (!g.aborted && g.metadata.microMatch.contains("micromatch")) s"1:${g.id}".some
    else g.metadata.microMatch.isDefined option "micromatch"

  private def returnGame(g: Game): Fu[Game] = for {
    initialFen <- GameRepo initialFen g
    situation = initialFen flatMap { fen => Forsyth <<< fen.value }
    pieces = g.variant match {
      case FromPosition | Russian | Brazilian => situation.fold(g.variant.pieces)(_.situation.board.pieces)
      case variant => variant.pieces
    }
    users <- UserRepo byIds g.userIds
    game <- Game.make(
      draughts = DraughtsGame(
        situation = Situation(
          board = Board(pieces, variant = g.variant),
          color = situation.fold[draughts.Color](White)(_.situation.color)
        ),
        clock = g.clock map { c => Clock(c.config) },
        turns = situation ?? (_.turns),
        startedAtTurn = situation ?? (_.turns)
      ),
      whitePlayer = returnPlayer(g, White, users),
      blackPlayer = returnPlayer(g, Black, users),
      mode = if (!g.isExternalTournament && users.exists(_.lame)) draughts.Mode.Casual else g.mode,
      source = g.source | Source.Lobby,
      daysPerTurn = g.daysPerTurn,
      pdnImport = None,
      microMatch = nextMicroMatch(g)
    ).withUniqueId.dmap { game =>
        g.externalTournamentId.fold(game)(game.withExternalTournamentId)
      }
  } yield game

  private def returnPlayer(game: Game, color: DraughtsColor, users: List[User]): lidraughts.game.Player =
    game.opponent(color).aiLevel match {
      case Some(ai) => lidraughts.game.Player.make(color, ai.some)
      case None => lidraughts.game.Player.make(
        color,
        game.opponent(color).userId.flatMap { id => users.find(_.id == id) },
        PerfPicker.mainOrDefault(game)
      )
    }

  private def redirectEvents(game: Game): Events = {
    val whiteId = game fullIdOf White
    val blackId = game fullIdOf Black
    List(
      Event.RedirectOwner(White, blackId, AnonCookie.json(game, Black)),
      Event.RedirectOwner(Black, whiteId, AnonCookie.json(game, White)),
      // tell spectators about the rematch
      Event.RematchTaken(game.id)
    )
  }
}

private object Rematcher {

  case class Offers(white: Boolean, black: Boolean) {
    def apply(color: draughts.Color) = color.fold(white, black)
  }
}
