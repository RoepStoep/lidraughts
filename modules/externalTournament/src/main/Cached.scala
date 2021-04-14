package lidraughts.externalTournament

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

  def getOngoingGames(id: String): Fu[List[Game]] =
    ongoingGameIdsCache.get(id).flatMap { gameIds =>
      gameIds.map(proxyGame)
        .sequenceFu
        .dmap(_.flatten)
    }
}
