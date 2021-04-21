package lidraughts.externalTournament

import lidraughts.challenge.Challenge
import lidraughts.db.dsl._
import lidraughts.game.Game

final class GameMetaApi(
    coll: Coll
) {

  import BsonHandlers._

  def find(id: Game.ID): Fu[Option[GameMeta]] =
    coll.byId[GameMeta](id)

  def withMeta(g: Game): Fu[GameWithMeta] =
    coll.byId[GameMeta](g.id) dmap { GameWithMeta(g, _) }

  def withMeta(id: Game.ID): Fu[GameIdWithMeta] =
    coll.byId[GameMeta](id) dmap { GameIdWithMeta(id, _) }

  def insert(m: GameMeta): Funit = {
    if (m.isEmpty) funit
    else coll.insert(m).void
  }
}

