package lidraughts.externalTournament

import lidraughts.game.Game

case class GameWithMeta(
    game: Game,
    meta: Option[GameMeta]
) {

  def round = meta.flatMap(_.round)
}

case class GameIdWithMeta(
    gameId: Game.ID,
    meta: Option[GameMeta]
) {

  def round = meta.flatMap(_.round)

  def fetchGame(fetch: Game.ID => Fu[Option[Game]]): Fu[Option[GameWithMeta]] =
    fetch(gameId) dmap { _.map(GameWithMeta(_, meta)) }
}

case class GameMeta(
    _id: Game.ID,
    round: Option[Int]
) {

  def id = _id

  def nonEmpty = round.isDefined
  def isEmpty = !nonEmpty
}
