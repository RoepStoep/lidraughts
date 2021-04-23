package lidraughts.externalTournament

import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.duration._
import lidraughts.challenge.Challenge
import lidraughts.game.{ Game, GameRepo }
import lidraughts.memo._

final class Cached(
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    proxyGame: Game.ID => Fu[Option[Game]],
    gameMetaApi: GameMetaApi,
    challengeApi: lidraughts.challenge.ChallengeApi
)(implicit system: akka.actor.ActorSystem) {

  import Cached._

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

  private[externalTournament] val finishedGamesCache = asyncCache.clearable[ExternalTournament.ID, FinishedGames](
    name = "externalTournament.finishedGames",
    f = computeFinished,
    expireAfter = _.ExpireAfterAccess(1 minute)
  )

  private def computeFinished(id: ExternalTournament.ID) =
    GameRepo
      .finishedByExternalTournament(id)
      .flatMap(_.map(gameMetaApi.withMeta).sequenceFu)
      .map { games =>
        val rounds = games.foldLeft(none[Int]) { (acc, g) =>
          g.round match {
            case o @ Some(r) if r > ~acc => o
            case _ => acc
          }
        }
        FinishedGames(rounds, games)
      }

  def getFinishedGames(tourId: ExternalTournament.ID): Fu[FinishedGames] =
    finishedGamesCache.get(tourId)

  def addFinishedGame(tourId: ExternalTournament.ID, g: GameWithMeta) =
    finishedGamesCache.update(tourId, { finished =>
      val rounds = g.round match {
        case o @ Some(r) if r > ~finished.rounds => o
        case _ => finished.rounds
      }
      val games = (g :: finished.games).sortBy(_.game.createdAt)(gameSortDesc)
      FinishedGames(rounds, games)
    })

  private[externalTournament] val ongoingGameIdsCache = asyncCache.clearable[String, List[GameIdWithMeta]](
    name = "externalTournament.ongoingGameIds",
    f = id => GameRepo.ongoingIdsByExternalTournament(id).flatMap(_.map(gameMetaApi.withMeta).sequenceFu),
    expireAfter = _.ExpireAfterWrite(1 minute)
  )

  def getOngoingGames(id: ExternalTournament.ID): Fu[List[GameWithMeta]] =
    ongoingGameIdsCache.get(id).flatMap { gameIds =>
      gameIds.map(_.fetchGame(proxyGame))
        .sequenceFu
        .dmap(_.flatten)
    }

  private[externalTournament] val upcomingGamesCache = asyncCache.clearable[String, List[Challenge]](
    name = "externalTournament.upcomingGames",
    f = id => challengeApi.allForExternalTournament(id),
    expireAfter = _.ExpireAfterAccess(1 minute)
  )

  def getUpcomingGames(id: ExternalTournament.ID): Fu[List[Challenge]] =
    upcomingGamesCache.get(id)

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

  private def computePage(page: (ExternalTournament.ID, Int)) =
    for {
      tour <- Env.current.api.byId(page._1) flatten "Invalid tournament ID"
      players <- ExternalPlayerRepo.byTour(page._1)
      rankedPlayers = players.filter(p => p.ranked && p.page == page._2).sortBy(_.rank)
      finished <- getFinishedGames(page._1)
      ongoing <- getOngoingGames(page._1)
      playerInfos = rankedPlayers.map(p => PlayerInfo.make(tour, p, finished, ongoing).reverse)
      playerInfoJson <- playerInfos.map(Env.current.jsonView.playerInfoJson(tour, _, players)).sequenceFu
    } yield Json.obj(
      "page" -> page._2,
      "players" -> playerInfoJson
    )
}

object Cached {

  case class FinishedGames(
      rounds: Option[Int],
      games: List[GameWithMeta]
  ) {

    def actualRoundsPlayed(ongoing: List[GameWithMeta]) = {
      ongoing.foldLeft(rounds) { (acc, g) =>
        g.round match {
          case o @ Some(r) if r > ~acc => o
          case _ => acc
        }
      }
    }
  }
}