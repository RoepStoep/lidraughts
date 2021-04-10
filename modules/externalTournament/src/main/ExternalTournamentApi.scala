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

  def createForm = DataForm.form

  def create(
    data: DataForm.Data,
    userId: User.ID
  ): Fu[ExternalTournament] = {
    val tour = data make userId
    coll.insert(tour) inject tour
  }

  def byId(id: String) = coll.byId[ExternalTournament](id)

  def finishGame(game: Game): Unit = {
    game.externalTournamentId.foreach { tourId =>
      cached.finishedGamesCache.invalidate(tourId)
      socketReload(tourId)
    }
  }

  def socketReload(tourId: String): Unit = socketMap.tell(tourId, Reload)
}
