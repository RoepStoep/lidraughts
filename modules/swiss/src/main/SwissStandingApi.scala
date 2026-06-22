package lidraughts.swiss

import com.github.blemale.scaffeine.Scaffeine
import play.api.libs.json._
import scala.concurrent.duration._

import lidraughts.common.{ LightUser, LightWfdUser }
import lidraughts.db.dsl._

/*
 * Getting a standing page of a tournament can be very expensive
 * because it can iterate through thousands of mongodb documents.
 * Try to cache the stuff, and limit concurrent access to prevent
 * overloading mongodb.
 */
final class SwissStandingApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    getLightUsers: LightUsersGetter
) {

  import BsonHandlers._

  private val pageCache = Scaffeine()
    .expireAfterWrite(60 minutes)
    .build[(Swiss.Id, Int), JsObject]

  def apply(swiss: Swiss, page: Int): Fu[JsObject] =
    fuccess(pageCache.getIfPresent(swiss.id -> page)) getOrElse {
      if (page == 1) first get swiss.id
      else compute(swiss, page)
    }

  def update(res: SwissScoring.Result): Funit =
    getLightUsers(res.leaderboard.map(_._1.userId), ~res.swiss.isWfd) map {
      _.zip(res.leaderboard).zipWithIndex
        .grouped(10)
        .toList
        .foldLeft(0) {
          case (i, pagePlayers) =>
            val page = i + 1
            pageCache.put(
              res.swiss.id -> page,
              Json.obj(
                "page" -> page,
                "players" -> pagePlayers
                  .map {
                    case ((user, (player, sheet)), r) =>
                      SwissJson.playerJson(
                        res.swiss,
                        SwissPlayer.View(
                          player = player,
                          rank = r + 1,
                          user = lightUserWithFallback(user, player.userId),
                          ~res.pairings.get(player.userId),
                          sheet
                        )
                      )
                  }
              )
            )
            page
        }
    } map { lastPage =>
      // make sure there's no extra page in the cache in case of players leaving the tournament
      pageCache.invalidate(res.swiss.id -> (lastPage + 1))
    }

  private def lightUserWithFallback(user: Either[Option[LightUser], Option[LightWfdUser]], userId: String) = user match {
    case Right(u) => Right(u | LightWfdUser.fallback(userId))
    case Left(u) => Left(u | LightUser.fallback(userId))
  }

  private val first = asyncCache.multi[Swiss.Id, JsObject](
    name = "swiss.page.first",
    f = compute(_, 1),
    expireAfter = _.ExpireAfterWrite(1 second)
  )

  private def compute(id: Swiss.Id, page: Int): Fu[JsObject] =
    swissColl.byId[Swiss](id.value) flatten s"No such tournament: $id" flatMap { compute(_, page) }

  private def compute(swiss: Swiss, page: Int): Fu[JsObject] =
    for {
      rankedPlayers <- bestWithRankByPage(swiss.id, 10, page atLeast 1)
      pairings <- !swiss.isCreated ?? SwissPairing.fields { f =>
        pairingColl
          .find($doc(f.swissId -> swiss.id, f.players $in rankedPlayers.map(_.player.userId)))
          .sort($sort asc f.round)
          .list[SwissPairing]()
          .map(SwissPairing.toMap)
      }
      sheets = SwissSheet.many(swiss, rankedPlayers.map(_.player), pairings)
      users <- getLightUsers(rankedPlayers.map(_.player.userId), ~swiss.isWfd)
    } yield Json.obj(
      "page" -> page,
      "players" -> rankedPlayers
        .zip(users)
        .zip(sheets)
        .map {
          case SwissPlayer.Ranked(rank, player) ~ user ~ sheet =>
            SwissJson.playerJson(
              swiss,
              SwissPlayer.View(
                player,
                rank,
                lightUserWithFallback(user, player.userId),
                ~pairings.get(player.userId),
                sheet
              )
            )
        }
    )

  private def bestWithRank(id: Swiss.Id, nb: Int, skip: Int): Fu[List[SwissPlayer.Ranked]] =
    best(id, nb, skip).map { res =>
      res
        .foldRight(List.empty[SwissPlayer.Ranked] -> (res.size + skip)) {
          case (p, (res, rank)) => (SwissPlayer.Ranked(rank, p) :: res, rank - 1)
        }
        ._1
    }

  private def bestWithRankByPage(id: Swiss.Id, nb: Int, page: Int): Fu[List[SwissPlayer.Ranked]] =
    bestWithRank(id, nb, (page - 1) * nb)

  private def best(id: Swiss.Id, nb: Int, skip: Int): Fu[List[SwissPlayer]] =
    SwissPlayer.fields { f =>
      playerColl.find($doc(f.swissId -> id)).sort($sort desc f.score).skip(skip).list[SwissPlayer](nb)
    }
}
