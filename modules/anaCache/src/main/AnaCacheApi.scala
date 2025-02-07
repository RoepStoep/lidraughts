package lidraughts.anaCache

import lidraughts.common.IpAddress
import lidraughts.db.dsl._
import lidraughts.user.User

final class AnaCacheApi(
    coll: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  import AnaCacheEntry._
  import BSONHandlers._

  def put(candidate: Input.Candidate, userId: Option[User.ID], ip: IpAddress): Funit =
    candidate.input ?? { put(_, userId, ip) }

  private def put(input: Input, userId: Option[User.ID], ip: IpAddress): Funit = {
    val entry = makeEntry(input, userId, ip)
    logger.info(s"entry from $userId $ip: $entry")
    coll.insert(entry).void
  }
}
