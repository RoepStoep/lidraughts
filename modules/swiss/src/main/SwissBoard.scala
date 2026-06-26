package lidraughts.swiss

import com.github.blemale.scaffeine.Scaffeine
import scala.concurrent.duration._

import lidraughts.common.{ LightUser, LightWfdUser }
import lidraughts.game.Game

private case class SwissBoard(
    gameId: Game.ID,
    white: SwissBoard.Player,
    black: SwissBoard.Player
)

private object SwissBoard {
  case class Player(user: Either[LightUser, LightWfdUser], rank: Int, rating: Int)
  case class WithGame(board: SwissBoard, game: Game)
}

final private class SwissBoardApi(
    scoring: SwissScoring,
    lightUserApi: lidraughts.user.LightUserApi,
    lightWfdUserApi: lidraughts.user.LightWfdUserApi,
    proxyGame: Game.ID => Fu[Option[Game]]
) {

  private val displayBoards = 6

  private val boardsCache = Scaffeine()
    .expireAfterWrite(60 minutes)
    .build[Swiss.Id, List[SwissBoard]]

  def apply(swiss: Swiss): Fu[List[SwissBoard.WithGame]] =
    boardsCache.getIfPresent(swiss.id) match {
      case Some(boards) => hydrate(boards)
      case None if swiss.nbOngoing > 0 => computeAndHydrate(swiss.id)
      case None => fuccess(Nil)
    }

  def update(data: SwissScoring.Result): Funit =
    data match {
      case SwissScoring.Result(swiss, leaderboard, playerMap, pairings) =>
        val ranks = leaderboardRanks(leaderboard)
        val boards = buildBoards(swiss, leaderboard, playerMap, pairings, ranks)
        if (boards.isEmpty && swiss.nbOngoing > 0) boardsCache.invalidate(swiss.id)
        else boardsCache.put(swiss.id, boards)
        funit
    }

  private def leaderboardRanks(leaderboard: List[(SwissPlayer, SwissSheet)]): Ranking =
    leaderboard.zipWithIndex.map {
      case ((p, _), i) => p.userId -> (i + 1)
    }.toMap

  private def computeAndHydrate(id: Swiss.Id): Fu[List[SwissBoard.WithGame]] =
    scoring(id).flatMap {
      case Some(res) => update(res) >> (boardsCache.getIfPresent(id) ?? hydrate)
      case None => fuccess(Nil)
    }

  private def hydrate(boards: List[SwissBoard]): Fu[List[SwissBoard.WithGame]] =
    boards
      .map { board =>
        proxyGame(board.gameId) map2 { g: Game =>
          SwissBoard.WithGame(board, g)
        }
      }
      .sequenceFu
      .dmap(_.flatten)

  private def buildBoards(
    swiss: Swiss,
    leaderboard: List[(SwissPlayer, SwissSheet)],
    playerMap: SwissPlayer.PlayerMap,
    pairings: SwissPairing.PairingMap,
    ranks: Ranking
  ): List[SwissBoard] =
    leaderboard
      .collect {
        case (player, _) if player.present => player
      }
      .flatMap { player =>
        pairings get player.userId flatMap {
          _ get swiss.round
        }
      }
      .filter(_.isOngoing)
      .distinct
      .take(displayBoards)
      .flatMap { pairing =>
        for {
          p1 <- playerMap get pairing.white
          p2 <- playerMap get pairing.black
          u1 <- swissLightUser(p1.userId, ~swiss.isWfd)
          u2 <- swissLightUser(p2.userId, ~swiss.isWfd)
          r1 <- ranks get p1.userId
          r2 <- ranks get p2.userId
        } yield SwissBoard(
          pairing.gameId,
          white = SwissBoard.Player(u1, r1, p1.rating),
          black = SwissBoard.Player(u2, r2, p2.rating)
        )
      }

  private def swissLightUser(userId: String, isWfd: Boolean): Option[Either[LightUser, LightWfdUser]] =
    if (isWfd) lightWfdUserApi.sync(userId).map(Right(_))
    else lightUserApi.sync(userId).map(Left(_))
}