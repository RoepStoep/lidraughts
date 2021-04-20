package lidraughts.externalTournament

import org.joda.time.DateTime
import play.api.libs.json._
import scala.concurrent.duration._

import lidraughts.game.{ Game, GameRepo }
import lidraughts.memo._

private[externalTournament] final class Cached(
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    proxyGame: Game.ID => Fu[Option[Game]],
    gameMetaApi: GameMetaApi
)(implicit system: akka.actor.ActorSystem) {

  def api = Env.current.api

  private lazy val nameCache = new Syncache[String, Option[String]](
    name = "externalTournament.name",
    compute = id => api byId id map2 { (tour: ExternalTournament) => tour.name },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def name(id: String): Option[String] = nameCache sync id

  private val gameSortDesc = Ordering[DateTime].reverse

  private val finishedGamesCache = asyncCache.clearable[String, List[GameWithMeta]](
    name = "externalTournament.finishedGames",
    f = id => GameRepo.finishedByExternalTournament(id).flatMap(_.map(gameMetaApi.withMeta).sequenceFu),
    expireAfter = _.ExpireAfterAccess(1 hour)
  )

  def getFinishedGames(tourId: ExternalTournament.ID): Fu[List[GameWithMeta]] =
    finishedGamesCache.get(tourId)

  def addFinishedGame(tourId: ExternalTournament.ID, g: GameWithMeta) =
    finishedGamesCache.update(tourId, games => (g :: games).sortBy(_.game.createdAt)(gameSortDesc))

  private[externalTournament] val ongoingGameIdsCache = asyncCache.clearable[String, List[GameIdWithMeta]](
    name = "externalTournament.ongoingGameIds",
    f = id => GameRepo.ongoingIdsByExternalTournament(id).flatMap(_.map(gameMetaApi.idWithMeta).sequenceFu),
    expireAfter = _.ExpireAfterWrite(1 minute)
  )

  def getOngoingGames(id: ExternalTournament.ID): Fu[List[GameWithMeta]] =
    ongoingGameIdsCache.get(id).flatMap { gameIds =>
      gameIds.map(_.fetchGame(proxyGame))
        .sequenceFu
        .dmap(_.flatten)
    }

  private val standingPageCache = asyncCache.clearable[(String, Int), JsObject](
    name = "externalTournament.standingPages",
    f = computePage,
    expireAfter = _.ExpireAfterAccess(1 hour)
  )

  def invalidateStandings = standingPageCache.invalidateAll

  def invalidateStandings(tourId: ExternalTournament.ID) =
    ExternalPlayerRepo.byTour(tourId).map { players =>
      val maxPage = 1 + players.foldLeft(1) { (t, p) => if (p.page > t) p.page else t }
      for (p <- 1 to maxPage) {
        standingPageCache.invalidate(tourId -> p)
      }
    }

  def getStandingPage(tourId: ExternalTournament.ID, page: Int): Fu[JsObject] =
    standingPageCache.get(tourId -> page)

  private def computePage(page: (String, Int)) =
    for {
      tourOpt <- Env.current.api.byId(page._1)
      tour = tourOpt err "Invalid tournament ID"
      players <- ExternalPlayerRepo.byTour(page._1)
      rankedPlayers = players.filter(p => p.ranked && p.page == page._2).sortBy(_.rank)
      games <- getFinishedGames(page._1)
      playerInfos = rankedPlayers.map(p => PlayerInfo.make(p, games).reverse)
      playerInfoJson <- playerInfos.map(Env.current.jsonView.playerInfoJson(tour, _, players)).sequenceFu
    } yield Json.obj(
      "page" -> page._2,
      "players" -> playerInfoJson
    )
}
