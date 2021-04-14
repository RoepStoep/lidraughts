package lidraughts.externalTournament

import actorApi._
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.User

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
  ): Fu[ExternalPlayer] = {
    val player = data make tour.id
    coll.insert(player) inject player
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
