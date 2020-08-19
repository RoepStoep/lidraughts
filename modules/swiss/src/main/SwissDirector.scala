package lidraughts.swiss

import draughts.{ Black, Color, White }
import org.joda.time.DateTime

import lidraughts.db.dsl._
import lidraughts.game.{ IdGenerator, Game, GameRepo, Player => GamePlayer }
import lidraughts.user.User

final private class SwissDirector(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    pairingSystem: PairingSystem,
    onStart: Game.ID => Unit
) {
  import BsonHandlers._

  // sequenced by SwissApi
  private[swiss] def startRound(from: Swiss): Fu[Option[Swiss]] =
    pairingSystem(from)
      .flatMap { pendings =>
        val pendingPairings = pendings.collect { case Right(p) => p }
        if (pendingPairings.isEmpty) fuccess(none) // terminate
        else {
          val swiss = from.startRound
          for {
            players <- SwissPlayer.fields { f =>
              playerColl
                .find($doc(f.swissId -> swiss.id))
                .list[SwissPlayer]()
            }
            ids <- IdGenerator.games(pendingPairings.size)
            pairings = pendingPairings.zip(ids).map {
              case (SwissPairing.Pending(w, b), id) =>
                SwissPairing(
                  id = id,
                  swissId = swiss.id,
                  round = swiss.round,
                  white = w,
                  black = b,
                  status = Left(SwissPairing.Ongoing)
                )
            }
            _ <- swissColl
              .update(
                $id(swiss.id),
                $unset("nextRoundAt") ++ $set(
                  "round" -> swiss.round,
                  "nbOngoing" -> pairings.size,
                  "lastRoundAt" -> DateTime.now
                )
              )
              .void
            date = DateTime.now
            byes = pendings.collect { case Left(bye) => bye.player }
            _ <- SwissPlayer.fields { f =>
              playerColl
                .update($doc(f.userId $in byes, f.swissId -> swiss.id), $addToSet(f.byes -> swiss.round))
                .void
            }
            pairingsBson = pairings.map(pairingHandler.write)
            _ <- pairingColl.bulkInsert(pairingsBson.toStream, ordered = true).void
            games = pairings.map(makeGame(swiss, SwissPlayer.toMap(players)))
            _ <- lidraughts.common.Future.applySequentially(games) { game =>
              GameRepo.insertDenormalized(game) >>- onStart(game.id)
            }
          } yield swiss.some
        }
      }
      .recover {
        case PairingSystem.BBPairingException(msg, input) =>
          if (msg contains "The number of rounds is larger than the reported number of rounds.") none
          else {
            logger.warn(s"BBPairing ${from.id} $msg")
            logger.info(s"BBPairing ${from.id} $input")
            from.some
          }
      }

  private def makeGame(swiss: Swiss, players: Map[User.ID, SwissPlayer])(
    pairing: SwissPairing
  ): Game =
    Game
      .make(
        draughts = draughts.DraughtsGame(
          variantOption = Some(swiss.variant),
          fen = none
        ) |> { g =>
            val turns = g.player.fold(0, 1)
            g.copy(
              clock = swiss.clock.toClock.some,
              turns = turns,
              startedAtTurn = turns
            )
          },
        whitePlayer = makePlayer(White, players get pairing.white err s"Missing pairing white $pairing"),
        blackPlayer = makePlayer(Black, players get pairing.black err s"Missing pairing black $pairing"),
        mode = draughts.Mode(swiss.settings.rated),
        source = lidraughts.game.Source.Swiss,
        pdnImport = None
      )
      .withId(pairing.gameId)
      .withSwissId(swiss.id.value)
      .start

  private def makePlayer(color: Color, player: SwissPlayer) =
    lidraughts.game.Player.make(color, player.userId, player.rating, player.provisional)
}
