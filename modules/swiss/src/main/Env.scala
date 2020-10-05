package lidraughts.swiss

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
    mongoCache: lidraughts.memo.MongoCache.Builder,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    chatApi: lidraughts.chat.ChatApi,
    lightUserApi: lidraughts.user.LightUserApi,
    onStart: String => Unit,
    historyApi: lidraughts.history.HistoryApi,
    proxyGame: Game.ID => Fu[Option[Game]],
    proxyGames: List[Game.ID] => Fu[List[(Game.ID, Option[Game])]],
    val isProd: Boolean
) {

  private val settings = new {
    val CollectionSwiss = config getString "collection.swiss"
    val CollectionPlayer = config getString "collection.player"
    val CollectionPairing = config getString "collection.pairing"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val SocketName = config getString "socket.name"
    val SequencerTimeout = config duration "sequencer.timeout"
    val PairingExecutable = config getString "bbpairing"
    val NetBaseUrl = config getString "net.base_url"
  }
  import settings._

  private val sheetApi = new SwissSheetApi(
    playerColl = playerColl,
    pairingColl = pairingColl
  )

  private lazy val rankingApi = new SwissRankingApi(
    playerColl = playerColl,
    asyncCache = asyncCache
  )

  val trf = new SwissTrf(
    playerColl = playerColl,
    sheetApi = sheetApi,
    baseUrl = NetBaseUrl
  )

  private val pairingSystem = new PairingSystem(
    trf = trf,
    executable = PairingExecutable
  )

  private val scoring = new SwissScoring(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl
  )(system)

  private val director = new SwissDirector(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl,
    pairingSystem = pairingSystem,
    onStart = onStart
  )

  private val boardApi = new SwissBoardApi(
    rankingApi = rankingApi,
    lightUserApi = lightUserApi,
    proxyGame = proxyGame
  )

  private val statsApi = new SwissStatsApi(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl,
    sheetApi = sheetApi,
    mongoCache = mongoCache
  )

  lazy val verify = new SwissCondition.Verify(historyApi)

  lazy val api = new SwissApi(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl,
    cache = cache,
    socketMap = socketMap,
    director = director,
    scoring = scoring,
    rankingApi = rankingApi,
    standingApi = standingApi,
    boardApi = boardApi,
    verify = verify,
    chatApi = chatApi,
    lightUserApi = lightUserApi,
    proxyGames = proxyGames,
    bus = system.lidraughtsBus
  )(system)

  private val socketMap: SocketMap = lidraughts.socket.SocketMap[SwissSocket](
    system = system,
    mkTrouper = (swissId: String) => new SwissSocket(
      system = system,
      swissId = swissId,
      history = new History(ttl = HistoryMessageTtl),
      lightUser = lightUserApi.async,
      uidTtl = UidTimeout,
      keepMeAlive = () => socketMap touch swissId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "swiss.socketMap",
    broomFrequency = 3945 millis
  )

  def version(swissId: Swiss.Id): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](swissId.value)(GetVersion)

  lazy val socketHandler = new SocketHandler(
    swissColl = swissColl,
    hub = hub,
    socketMap = socketMap,
    chat = hub.chat,
    flood = flood
  )

  lazy val standingApi = new SwissStandingApi(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl,
    asyncCache = asyncCache,
    lightUserApi = lightUserApi
  )

  lazy val json = new SwissJson(
    swissColl = swissColl,
    playerColl = playerColl,
    pairingColl = pairingColl,
    standingApi = standingApi,
    rankingApi = rankingApi,
    boardApi = boardApi,
    statsApi = statsApi,
    lightUserApi = lightUserApi
  )

  lazy val forms = new SwissForm(
    isProd = isProd
  )

  private lazy val cache = new SwissCache(
    asyncCache = asyncCache,
    swissColl = swissColl
  )(system)

  lazy val getName = new GetSwissName(cache.name.sync)

  private[swiss] lazy val swissColl = db(CollectionSwiss)
  private[swiss] lazy val playerColl = db(CollectionPlayer)
  private[swiss] lazy val pairingColl = db(CollectionPairing)

  system.lidraughtsBus.subscribeFun(
    'finishGame, 'adjustCheater, 'adjustBooster, 'teamKick, 'deploy
  ) {
      case lidraughts.game.actorApi.FinishGame(game, _, _) => api.finishGame(game)
      case lidraughts.hub.actorApi.team.KickFromTeam(teamId, userId) => api.kickFromTeam(teamId, userId)
      case lidraughts.hub.actorApi.mod.MarkCheater(userId, true) => api.kickLame(userId)
      case lidraughts.hub.actorApi.mod.MarkBooster(userId) => api.kickLame(userId)
      case m: lidraughts.hub.actorApi.Deploy => socketMap tellAll m
    }

  ResilientScheduler(
    every = Every(1 second),
    atMost = AtMost(20 seconds),
    initialDelay = 20 seconds,
    logger = logger branch "startPendingRounds"
  ) { api.startPendingRounds }(system)

  ResilientScheduler(
    every = Every(10 seconds),
    atMost = AtMost(15 seconds),
    initialDelay = 20 seconds,
    logger = logger branch "checkOngoingGames"
  ) { api.checkOngoingGames }(system)
}

object Env {

  lazy val current = "swiss" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "swiss",
    system = lidraughts.common.PlayApp.system,
    db = lidraughts.db.Env.current,
    flood = lidraughts.security.Env.current.flood,
    hub = lidraughts.hub.Env.current,
    mongoCache = lidraughts.memo.Env.current.mongoCache,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    chatApi = lidraughts.chat.Env.current.api,
    lightUserApi = lidraughts.user.Env.current.lightUserApi,
    onStart = lidraughts.round.Env.current.onStart,
    historyApi = lidraughts.history.Env.current.api,
    proxyGame = lidraughts.round.Env.current.proxy.game _,
    proxyGames = lidraughts.round.Env.current.proxy.games _,
    isProd = lidraughts.common.PlayApp.isProd
  )
}
