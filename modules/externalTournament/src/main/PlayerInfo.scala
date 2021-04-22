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
    } |> { results: List[Pairing] =>
      if (results.isEmpty || !tour.hasRounds) results
      else {
        val actualRounds = ~finished.actualRoundsPlayed(ongoing)
        val emptyRounds = (1 to actualRounds).toList filterNot { r => results.exists(_.round == r) }
        (emptyRounds.map(Empty) ::: results).sortBy(p => -p.round)
      }
    } |> { results =>
      if (results.isEmpty || !tour.settings.microMatches) results
      else {
        results.foldRight(List.empty[Pairing]) {
          case o @ (r, p :: tail) if p.round == r.round =>
            p -> r match {
              case (r1 @ Result(g1, _, _), r2 @ Result(g2, _, _)) if isMicroMatchPair(g1, g2) =>
                MicroMatch(r1, r2) :: tail
              case _ => r :: o._2
            }
          case (r, acc) => r :: acc
        }
      }
    } |> { sheet =>
      new PlayerInfo(
        player = player,
        results = sheet
      )
    }

  private def isMicroMatchPair(g1: GameWithMeta, g2: GameWithMeta) =
    ~{
      for {
        id1 <- g1.game.metadata.microMatchGameIdText
        id2 <- g2.game.metadata.microMatchGameIdText
        nr1 <- g1.game.metadata.microMatchGameNr
        nr2 <- g2.game.metadata.microMatchGameNr
      } yield (id1 == g2.game.id || id2 == g1.game.id) && nr1 != nr2
    }

  sealed trait Pairing {

    def round: Int
  }

  case class Result(game: GameWithMeta, color: draughts.Color, win: Option[Boolean]) extends Pairing {

    def points = win.fold(1)(if (_) 2 else 0)

    def ongoing = game.game.isBeingPlayed.option(true)

    def round = ~game.round
  }
  case class MicroMatch(r1: Result, r2: Result) extends Pairing {

    def win =
      if (~ongoing) none
      else (r1.points + r2.points) |> { points =>
        if (points == 2) none
        else (points > 2).some
      }

    def ongoing = (~r1.ongoing || ~r2.ongoing) option true

    def round = r1.round
  }
  case class Bye(r: Int, f: Boolean) extends Pairing {

    def round = r
  }
  case class Empty(round: Int) extends Pairing
}
