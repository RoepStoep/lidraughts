package lidraughts.externalTournament

import com.github.blemale.scaffeine.Scaffeine
import play.api.libs.json._
import scala.concurrent.duration._

import lidraughts.game.{ Game, GameRepo }
import lidraughts.memo._

private[externalTournament] final class Cached(
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    proxyGame: Game.ID => Fu[Option[Game]]
)(implicit system: akka.actor.ActorSystem) {

  def api = Env.current.api

  private lazy val nameCache = new Syncache[String, Option[String]](
    name = "externalTournament.name",
    compute = id => api byId id map2 { (tour: ExternalTournament) => tour.name },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )(system)

  def name(id: String): Option[String] = nameCache sync id

  private[externalTournament] val finishedGamesCache = asyncCache.clearable[String, List[Game]](
    name = "externalTournament.finishedGames",
    f = GameRepo.finishedByExternalTournament,
    expireAfter = _.ExpireAfterAccess(1 hour)
  )

  def getFinishedGames(id: String): Fu[List[Game]] = finishedGamesCache.get(id)

  private[externalTournament] val ongoingGameIdsCache = asyncCache.clearable[String, List[Game.ID]](
    name = "externalTournament.ongoingGameIds",
    f = id => GameRepo.ongoingIdsByExternalTournament(id),
    expireAfter = _.ExpireAfterWrite(1 minute)
  )

  def getOngoingGames(id: ExternalTournament.ID): Fu[List[Game]] =
    ongoingGameIdsCache.get(id).flatMap { gameIds =>
      gameIds.map(proxyGame)
        .sequenceFu
        .dmap(_.flatten)
    }

  private[externalTournament] val standingPageCache = asyncCache.clearable[(String, Int), JsObject](
    name = "externalTournament.standingPages",
    f = computePage,
    expireAfter = _.ExpireAfterAccess(1 hour)
  )

  def invalidateStandings(tourId: ExternalTournament.ID) =
    ExternalPlayerRepo.byTour(tourId).map { players =>
      val maxPage = 1 + players.foldLeft(1) { (t, p) => if (p.page > t) p.page else t }
      for (p <- 1 to maxPage) {
        standingPageCache.invalidate(tourId -> p)
      }
    }

  def getStanding(tourId: ExternalTournament.ID, page: Int): Fu[JsObject] =
    standingPageCache.get(tourId -> page)

  private def computePage(page: (String, Int)) =
    for {
      players <- ExternalPlayerRepo.byTour(page._1)
      rankedPlayers = players.filter(p => p.rank ?? { r => r > 10 * (page._2 - 1) && r <= 10 * page._2 }).sortBy(_.rank)
      games <- getFinishedGames(page._1)
    } yield Json.obj(
      "page" -> page._2,
      "players" -> rankedPlayers.map(p => PlayerInfo.make(p, games).reverse).map(Env.current.jsonView.playerInfoJson)
    )
}
