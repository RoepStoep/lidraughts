package lidraughts.externalTournament

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.common.{ AtMost, Every, ResilientScheduler }
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
    challengeApi: lidraughts.challenge.ChallengeApi,
    proxyGame: Game.ID => Fu[Option[Game]],
    updateIfPresent: Game => Fu[Game],
    scheduler: lidraughts.common.Scheduler
) {

  private val settings = new {
    val CollectionExternalTournament = config getString "collection.externalTournament"
    val CollectionExternalPlayer = config getString "collection.externalPlayer"
    val CollectionExternalGameMeta = config getString "collection.externalGameMeta"
    val CollectionFmjdPlayer = config getString "collection.externalFmjdPlayer"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val FmjdPlayersBaseUrl = config getString "fmjd_players.base_url"
    val FmjdPlayersPictureBaseUrl = config getString "fmjd_players.base_picture_url"
    val FmjdPlayersRefreshDelay = config duration "fmjd_players.refresh_delay"
  }
  import settings._

  private val bus = system.lidraughtsBus

  private[externalTournament] lazy val externalTournamentColl = db(CollectionExternalTournament)
  private[externalTournament] lazy val externalPlayerColl = db(CollectionExternalPlayer)
  private[externalTournament] lazy val fmjdPlayerColl = db(CollectionFmjdPlayer)
  private[externalTournament] lazy val gameMetaColl = db(CollectionExternalGameMeta)

  private lazy val fmjdPlayerApi = new FmjdPlayerApi(
    baseUrl = FmjdPlayersBaseUrl,
    basePictureUrl = FmjdPlayersPictureBaseUrl,
    coll = fmjdPlayerColl,
    lightFmjdUserApi = lightFmjdUserApi
  )

  lazy val lightFmjdUserApi = new LightFmjdUserApi(fmjdPlayerColl)(system)

  lazy val gameMetaApi = new GameMetaApi(gameMetaColl)

  lazy val cached = new Cached(
    asyncCache = asyncCache,
    proxyGame = proxyGame,
    gameMetaApi = gameMetaApi
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

  lazy val jsonView = new JsonView(
    lightUserApi = lightUserApi,
    lightFmjdUserApi = lightFmjdUserApi,
    fmjdPlayerApi = fmjdPlayerApi,
    gameMetaApi = gameMetaApi,
    cached = cached
  )

  def playerRepo = ExternalPlayerRepo

  def forms = DataForm

  lazy val api = new ExternalTournamentApi(
    coll = externalTournamentColl,
    socketMap = socketMap,
    cached = cached,
    challengeApi = challengeApi,
    gameMetaApi = gameMetaApi,
    updateIfPresent = updateIfPresent,
    bus = bus
  )(system)

  bus.subscribeFuns(
    'startGame -> {
      case lidraughts.game.actorApi.StartGame(g) if g.isExternalTournament =>
        api.startGame(g) void
    },
    'finishGame -> {
      case lidraughts.game.actorApi.FinishGame(g, _, _) if g.isExternalTournament =>
        api.finishGame(g) void
    }
  )

  scheduler.once(45 seconds)(fmjdPlayerApi.refresh)
  scheduler.effect(FmjdPlayersRefreshDelay, "Refresh FMJD player data")(fmjdPlayerApi.refresh)

  ResilientScheduler(
    every = Every(10 seconds),
    atMost = AtMost(15 seconds),
    initialDelay = 22 seconds,
    logger = logger branch "checkOngoingGames"
  ) { api.checkOngoingGames }(system)
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
    challengeApi = lidraughts.challenge.Env.current.api,
    proxyGame = lidraughts.round.Env.current.proxy.game _,
    updateIfPresent = lidraughts.round.Env.current.proxy.updateIfPresent _,
    scheduler = lidraughts.common.PlayApp.scheduler
  )
}
