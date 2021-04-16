package lidraughts.externalTournament

import lidraughts.game.Game

case class PlayerInfo(
    player: ExternalPlayer,
    results: List[PlayerInfo.Result]
)

object PlayerInfo {

  def make(
    player: ExternalPlayer,
    games: List[Game]
  ) = new PlayerInfo(
    player = player,
    results = games.flatMap { game =>
      game.playerByUserId(player.userId) map { player =>
        PlayerInfo.Result(
          game = game,
          color = player.color,
          win = game.winnerColor.map(player.color ==)
        )
      }
    }
  )

  case class Result(game: Game, color: draughts.Color, win: Option[Boolean])
}
