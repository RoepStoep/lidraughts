package lidraughts.externalTournament

import lidraughts.game.Game

case class PlayerInfo(
    player: ExternalPlayer,
    results: List[PlayerInfo.Result]
) {

  def reverse = copy(results = results.reverse)
}

object PlayerInfo {

  def make(
    player: ExternalPlayer,
    games: List[GameWithMeta]
  ) = new PlayerInfo(
    player = player,
    results = games.flatMap { withMeta =>
      withMeta.game.playerByUserId(player.userId) map { player =>
        PlayerInfo.Result(
          game = withMeta,
          color = player.color,
          win = withMeta.game.winnerColor.map(player.color ==)
        )
      }
    }
  )

  case class Result(game: GameWithMeta, color: draughts.Color, win: Option[Boolean])
}
