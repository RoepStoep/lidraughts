package lidraughts.anaCache

import org.joda.time.DateTime

import draughts.variant.Variant
import draughts.format.{ FEN, Forsyth, Uci }
import lidraughts.common.IpAddress
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.User

final class AnaCacheApi(
    coll: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  import AnaCacheEntry._
  import BSONHandlers._

  def getGameEntries(game: Game, initialFen: Option[FEN]) = {
    val variant = game.variant
    val gameStart = game.createdAt
    val gameStop = game.movedAt.plusSeconds(1)
    draughts.Replay.situations(
      game.pdnMovesConcat(true, true).toList,
      initialFen,
      variant,
      finalSquare = true
    ).fold(
        err => {
          logger.info(s"getGameEntries ${game.id} replay error: $err")
          fuccess(Nil)
        },
        _.zipWithIndex.map {
          case (sit, index) =>
            getEntries(
              Position(variant, SmallFen.make(sit)), gameStart, gameStop
            ) dmap { entries =>
                entries.nonEmpty ?? (index, entries).some
              }
        }.sequenceFu.map(_.flatten)
      )
  }

  private def getEntries(pos: Position, start: DateTime, stop: DateTime) = coll.find(
    $doc(
      "pos" -> pos,
      "createdAt" $gte start $lt stop
    )
  ).list[AnaCacheEntry]()

  def put(candidate: Input.Candidate, userId: Option[User.ID], ip: IpAddress): Funit =
    candidate.input ?? { put(_, userId, ip) }

  private def put(input: Input, userId: Option[User.ID], ip: IpAddress): Funit = {
    val entry = makeEntry(input, userId, ip)
    logger.info(s"entry from $userId $ip: $entry")
    coll.insert(entry).void
  }
}
