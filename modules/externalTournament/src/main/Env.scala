package lidraughts.externalTournament

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.socket.History
import lidraughts.socket.Socket.{ GetVersion, SocketVersion }
import lidraughts.memo._

final class Env(
    config: Config,
    system: ActorSystem,
    db: lidraughts.db.Env,
    flood: lidraughts.security.Flood,
    hub: lidraughts.hub.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi
) {

  private val settings = new {
    val CollectionExternalTournament = config getString "collection.externalTournament"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
  }
  import settings._

  private lazy val externalTournamentColl = db(CollectionExternalTournament)

  private lazy val nameCache = new Syncache[String, Option[String]](
    name = "externalTournament.name",
    compute = id => api byId id map2 { (tour: ExternalTournament) => tour.name },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )(system)

  def name(id: String): Option[String] = nameCache sync id

  private val socketMap: SocketMap = lidraughts.socket.SocketMap[ExternalTournamentSocket](
    system = system,
    mkTrouper = (tourId: String) => new ExternalTournamentSocket(
      system = system,
      tourId = tourId,
      history = new History(ttl = HistoryMessageTtl),
      lightUser = lightUserApi.async,
      uidTtl = UidTimeout,
      keepMeAlive = () => socketMap touch tourId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "externalTournament.socketMap",
    broomFrequency = 4169 millis
  )

  def version(tourId: String): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](tourId)(GetVersion)

  lazy val socketHandler = new SocketHandler(
    coll = externalTournamentColl,
    hub = hub,
    socketMap = socketMap,
    chat = hub.chat,
    flood = flood
  )

  lazy val jsonView = new JsonView(lightUserApi)

  lazy val api = new ExternalTournamentApi(
    coll = externalTournamentColl,
    asyncCache = asyncCache
  )
}

object Env {

  lazy val current = "externalTournament" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "externalTournament",
    system = lidraughts.common.PlayApp.system,
    flood = lidraughts.security.Env.current.flood,
    hub = lidraughts.hub.Env.current,
    db = lidraughts.db.Env.current,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    lightUserApi = lidraughts.user.Env.current.lightUserApi
  )
}
