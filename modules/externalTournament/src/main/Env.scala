package lidraughts.externalTournament

import akka.actor._
import com.typesafe.config.Config

import scala.concurrent.duration._
import lidraughts.game.Game
import lidraughts.socket.History
import lidraughts.socket.Socket.{ GetVersion, SocketVersion }

final class Env(
    config: Config,
    system: ActorSystem,
    db: lidraughts.db.Env,
    flood: lidraughts.security.Flood,
    hub: lidraughts.hub.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi,
    proxyGame: Game.ID => Fu[Option[Game]]
) {

  private val settings = new {
    val CollectionExternalTournament = config getString "collection.externalTournament"
    val CollectionExternalPlayer = config getString "collection.externalPlayer"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
  }
  import settings._

  private val bus = system.lidraughtsBus

  private[externalTournament] lazy val externalTournamentColl = db(CollectionExternalTournament)
  private[externalTournament] lazy val externalPlayerColl = db(CollectionExternalPlayer)

  lazy val cached = new Cached(
    asyncCache = asyncCache,
    proxyGame = proxyGame
  )(system)

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
    socketMap = socketMap,
    cached = cached
  )

  bus.subscribeFuns(
    'challenge -> {
      case lidraughts.challenge.Event.Create(c) if c.isExternalTournament =>
        api addChallenge c
    },
    'startGame -> {
      case lidraughts.game.actorApi.StartGame(g) if g.isExternalTournament =>
        api startGame g
    },
    'finishGame -> {
      case lidraughts.game.actorApi.FinishGame(g, _, _) if g.isExternalTournament =>
        api finishGame g
    }
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
    lightUserApi = lidraughts.user.Env.current.lightUserApi,
    proxyGame = lidraughts.round.Env.current.proxy.game _
  )
}
