package lidraughts.externalTournament

import actorApi._
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.User
import ExternalPlayer.Status

final class ExternalTournamentApi(
    coll: Coll,
    socketMap: SocketMap,
    cached: Cached
) {

  import BsonHandlers._

  def tournamentForm = DataForm.tournament
  def playerForm = DataForm.player

  def byId(id: ExternalTournament.ID) = coll.byId[ExternalTournament](id)

  def create(
    data: DataForm.TournamentData,
    me: User
  ): Fu[ExternalTournament] = {
    val tour = data make me.id
    coll.insert(tour) inject tour
  }

  def addPlayer(
    tour: ExternalTournament,
    data: DataForm.PlayerData
  ): Fu[Option[ExternalPlayer]] =
    ExternalPlayerRepo.exists(tour.id, data.userId) flatMap { exists =>
      if (exists) fuccess(none)
      else {
        val player = data make tour
        ExternalPlayerRepo.insert(player) >>- {
          socketReload(tour.id)
        } inject player.some
      }
    }

  def answer(
    tourId: ExternalTournament.ID,
    me: User,
    accept: Boolean
  ): Fu[Boolean] =
    ExternalPlayerRepo.find(tourId, me.id) flatMap {
      case Some(player) if !player.joined && accept =>
        ExternalPlayerRepo.setStatus(player.id, Status.Joined) >>- {
          socketReload(tourId)
        } inject true
      case Some(player) if player.invited && !accept =>
        ExternalPlayerRepo.setStatus(player.id, Status.Rejected) >>- {
          socketReload(tourId)
        } inject true
      case _ => fuFalse
    }

  def finishGame(game: Game): Unit =
    game.externalTournamentId.foreach { tourId =>
      cached.finishedGamesCache.invalidate(tourId)
      cached.ongoingGameIdsCache.invalidate(tourId)
      socketReload(tourId)
    }

  def startGame(game: Game): Unit =
    game.externalTournamentId.foreach { tourId =>
      cached.ongoingGameIdsCache.invalidate(tourId)
      socketReload(tourId)
    }

  def addChallenge(c: lidraughts.challenge.Challenge): Unit =
    c.externalTournamentId.foreach { tourId =>
      socketReload(tourId)
    }

  def socketReload(tourId: ExternalTournament.ID): Unit = socketMap.tell(tourId, Reload)
}
