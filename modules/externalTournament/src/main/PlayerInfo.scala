package lidraughts.externalTournament

import lidraughts.game.Game

case class PlayerInfo(
    player: ExternalPlayer,
    results: List[PlayerInfo.Pairing]
) {

  def reverse = copy(results = results.reverse)
}

object PlayerInfo {

  def make(
    tour: ExternalTournament,
    player: ExternalPlayer,
    finished: Cached.FinishedGames,
    ongoing: List[GameWithMeta]
  ) =
    ~player.byes ::: (ongoing.filter(_.game.userIds.contains(player.userId)) ::: finished.games).flatMap { withMeta =>
      withMeta.game.playerByUserId(player.userId) map { player =>
        PlayerInfo.Result(
          game = withMeta,
          color = player.color,
          win = withMeta.game.winnerColor.map(player.color ==)
        )
      }
    } |> { results =>
      if (results.isEmpty || !tour.hasRounds) results
      else if (results.length >= ~finished.rounds) results.sortBy(p => -p.round)
      else {
        val actualRounds = ~finished.actualRoundsPlayed(ongoing)
        val emptyRounds = (1 to actualRounds).toList filterNot { r => results.exists(_.round == r) }
        (emptyRounds.map(Empty) ::: results).sortBy(p => -p.round)
      }
    } |> { sheet =>
      new PlayerInfo(
        player = player,
        results = sheet
      )
    }

  sealed trait Pairing {

    def round: Int
  }

  case class Result(game: GameWithMeta, color: draughts.Color, win: Option[Boolean]) extends Pairing {

    def round = ~game.round
  }
  case class Bye(r: Int, f: Boolean) extends Pairing {

    def round = r
  }
  case class Empty(round: Int) extends Pairing
}
