package lidraughts.swiss

import org.joda.time.DateTime
import scala.concurrent.duration._

import lidraughts.db.dsl._
import lidraughts.memo._

final class SwissFeature(
    swissColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  import BsonHandlers._

  private val cache = asyncCache.single[FeaturedSwisses](
    name = "swiss.featurable",
    f = cacheCompute($doc("$lt" -> DateTime.now)) zip
      cacheCompute($doc("$gt" -> DateTime.now, "$lt" -> DateTime.now.plusHours(1))) map {
        case (a, b) => FeaturedSwisses(a, b)
      },
    expireAfter = _.ExpireAfterWrite(10 seconds)
  )

  private def cacheCompute(startsAtRange: Bdoc): Fu[List[Swiss]] =
    swissColl
      .find(
        $doc(
          "featurable" -> true,
          "settings.i" $lte 600, // hits the partial index
          "startsAt" -> startsAtRange
        )
      )
      .sort($sort desc "nbPlayers")
      .list[Swiss](5)

  def get = cache.get
}
