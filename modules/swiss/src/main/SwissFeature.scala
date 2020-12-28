package lidraughts.swiss

import org.joda.time.DateTime
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._

import lidraughts.common.Heapsort
import lidraughts.db.dsl._
import lidraughts.hub.lightTeam.TeamId
import lidraughts.memo._

final class SwissFeature(
    swissColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    swissCache: SwissCache
) {

  import BsonHandlers._

  def get(teams: Seq[TeamId]) =
    cache.get zip getForTeams(teams) map {
      case (cached, teamed) =>
        FeaturedSwisses(
          created = teamed.created ::: cached.created,
          started = teamed.started ::: cached.started
        )
    }

  private val startsAtOrdering = Ordering.by[Swiss, Long](_.startsAt.getMillis)

  private def getForTeams(teams: Seq[TeamId]): Fu[FeaturedSwisses] =
    teams.map(swissCache.featuredInTeam.get).sequenceFu.map(_.flatten) flatMap { ids =>
      swissColl.byIds[Swiss](ids.map(_.value), ReadPreference.secondaryPreferred)
    } map {
      _.filter(_.isNotFinished).partition(_.isCreated) match {
        case (created, started) =>
          FeaturedSwisses(
            created = Heapsort.topN(created, 10, startsAtOrdering.reverse),
            started = Heapsort.topN(started, 10, startsAtOrdering)
          )
      }
    }

  private val cache = asyncCache.single[FeaturedSwisses](
    name = "swiss.featurable",
    f = {
      val now = DateTime.now
      cacheCompute($doc("$gt" -> now, "$lt" -> now.plusHours(1))) zip
        cacheCompute($doc("$gt" -> now.minusHours(3), "$lt" -> now)) map {
          case (created, started) => FeaturedSwisses(created, started)
        }
    },
    expireAfter = _.ExpireAfterWrite(10 seconds)
  )

  private def cacheCompute(startsAtRange: Bdoc): Fu[List[Swiss]] =
    swissColl
      .find(
        $doc(
          "featurable" -> true,
          "settings.i" $lte 600, // hits the partial index
          "startsAt" -> startsAtRange,
          "garbage" $ne true
        )
      )
      .sort($sort desc "nbPlayers")
      .list[Swiss](5)
}
