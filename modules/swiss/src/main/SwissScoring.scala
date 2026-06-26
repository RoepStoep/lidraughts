package lidraughts.swiss

import scala.concurrent.duration._

import lidraughts.db.dsl._

final private class SwissScoring(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll
)(implicit system: akka.actor.ActorSystem) {

  import BsonHandlers._

  def apply(id: Swiss.Id): Fu[Option[SwissScoring.Result]] = sequencer(id)

  private val sequencer =
    new lidraughts.hub.AskPipelines[Swiss.Id, Option[SwissScoring.Result]](
      compute = recompute,
      expiration = 1 minute,
      timeout = 10 seconds,
      name = "swiss.scoring"
    )

  private def recompute(id: Swiss.Id): Fu[Option[SwissScoring.Result]] =
    swissColl.byId[Swiss](id.value) flatMap {
      _.?? { (swiss: Swiss) =>
        for {
          (prevPlayers, pairings) <- fetchPlayers(swiss) zip fetchPairings(swiss)
          pairingMap = SwissPairing.toMap(pairings)
          sheets = SwissSheet.many(swiss, prevPlayers, pairingMap)
          withPoints = (prevPlayers zip sheets).map {
            case (player, sheet) => player.copy(points = sheet.points)
          }
          playerMap = SwissPlayer.toMap(withPoints)
          players = withPoints.map { p =>
            val playerPairings = (~pairingMap.get(p.userId)).values
            val (tieBreakSB, tieBreakSolkoff, perfSum) = playerPairings.foldLeft((0f, 0f, 0f)) {
              case ((sb, solkoff, perfSum), pairing) =>
                val opponent = playerMap.get(pairing opponentOf p.userId)
                val opponentPoints = opponent.??(_.points.value)
                val result = pairing.resultFor(p.userId)
                val newSB = sb + result.fold(opponentPoints / 2) { _ ?? opponentPoints }
                val newSolkoff = solkoff + opponentPoints
                val newPerf = perfSum + opponent.??(_.rating) + result.?? { win =>
                  if (win) 500 else -500
                }
                (newSB, newSolkoff, newPerf)
            }
            p.copy(
              tieBreakSB = Swiss.TieBreak(tieBreakSB),
              tieBreakSolkoff = Swiss.TieBreak(tieBreakSolkoff),
              performance = playerPairings.nonEmpty option Swiss.Performance(perfSum / playerPairings.size)
            )
              .recomputeScore
          }
          _ <- SwissPlayer.fields { f =>
            prevPlayers
              .zip(players)
              .filter {
                case (a, b) => a != b
              }
              .map {
                case (_, player) =>
                  playerColl
                    .update(
                      $id(player.id),
                      $set(
                        f.points -> player.points,
                        f.tieBreakSB -> player.tieBreakSB,
                        f.tieBreakSolkoff -> player.tieBreakSolkoff,
                        f.performance -> player.performance,
                        f.score -> player.score
                      )
                    )
                    .void
              }
              .sequenceFu
              .void
          }
        } yield SwissScoring
          .Result(
            swiss,
            players.zip(sheets).sortBy(-_._1.score.value),
            SwissPlayer toMap players,
            pairingMap
          )
          .some
      }
    }

  private def fetchPlayers(swiss: Swiss) =
    SwissPlayer.fields { f =>
      playerColl
        .find($doc(f.swissId -> swiss.id))
        .sort($sort asc f.score)
        .list[SwissPlayer]()
    }

  private def fetchPairings(swiss: Swiss) =
    !swiss.isCreated ?? SwissPairing.fields { f =>
      pairingColl
        .find($doc(f.swissId -> swiss.id))
        .list[SwissPairing]()
    }
}

private object SwissScoring {

  case class Result(
      swiss: Swiss,
      leaderboard: List[(SwissPlayer, SwissSheet)],
      playerMap: SwissPlayer.PlayerMap,
      pairings: SwissPairing.PairingMap
  )
}
