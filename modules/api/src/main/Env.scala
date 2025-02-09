package lidraughts.api

import akka.actor._
import com.typesafe.config.Config

import lidraughts.simul.Simul

final class Env(
    config: Config,
    settingStore: lidraughts.memo.SettingStore.Builder,
    renderer: ActorSelection,
    system: ActorSystem,
    scheduler: lidraughts.common.Scheduler,
    roundJsonView: lidraughts.round.JsonView,
    noteApi: lidraughts.round.NoteApi,
    forecastApi: lidraughts.round.ForecastApi,
    urgentGames: lidraughts.user.User => Fu[List[lidraughts.game.Pov]],
    updateIfPresent: lidraughts.game.Game => Fu[lidraughts.game.Game],
    relationApi: lidraughts.relation.RelationApi,
    bookmarkApi: lidraughts.bookmark.BookmarkApi,
    tournamentApi: lidraughts.tournament.TournamentApi,
    crosstableApi: lidraughts.game.CrosstableApi,
    prefApi: lidraughts.pref.PrefApi,
    anaCacheApi: lidraughts.anaCache.AnaCacheApi,
    playBanApi: lidraughts.playban.PlaybanApi,
    gamePdnDump: lidraughts.game.PdnDump,
    gameCache: lidraughts.game.Cached,
    userEnv: lidraughts.user.Env,
    annotator: lidraughts.analyse.Annotator,
    lobbyEnv: lidraughts.lobby.Env,
    setupEnv: lidraughts.setup.Env,
    swissEnv: lidraughts.swiss.Env,
    getSimul: Simul.ID => Fu[Option[Simul]],
    getSimulName: Simul.ID => Fu[Option[String]],
    getTournamentName: String => Option[String],
    getTeamName: lidraughts.team.Team.ID => Option[String],
    isStreaming: lidraughts.user.User.ID => Boolean,
    isPlaying: lidraughts.user.User.ID => Boolean,
    pools: List[lidraughts.pool.PoolConfig],
    challengeJsonView: lidraughts.challenge.JsonView,
    val isProd: Boolean
) {

  val apiToken = config getString "api.token"

  val isStage = config getBoolean "app.stage"

  object Net {
    val Domain = config getString "net.domain"
    val Protocol = config getString "net.protocol"
    val BaseUrl = config getString "net.base_url"
    val Port = config getInt "http.port"
    val AssetDomain = config getString "net.asset.domain"
    val SocketDomain = config getString "net.socket.domain"
    val Email = config getString "net.email"
    val Crawlable = config getBoolean "net.crawlable"
    val RateLimit = config getBoolean "net.ratelimit"
  }
  val PrismicApiUrl = config getString "prismic.api_url"
  val EditorAnimationDuration = config duration "editor.animation.duration"
  val ExplorerEndpoint = config getString "explorer.endpoint"
  val TablebaseEndpoint = config getString "explorer.tablebase.endpoint"

  private val InfluxEventEndpoint = config getString "api.influx_event.endpoint"
  private val InfluxEventEnv = config getString "api.influx_event.env"

  object Accessibility {
    val blindCookieName = config getString "accessibility.blind.cookie.name"
    val blindCookieMaxAge = config getInt "accessibility.blind.cookie.max_age"
    private val blindCookieSalt = config getString "accessibility.blind.cookie.salt"
    def hash(implicit ctx: lidraughts.user.UserContext) = {
      import com.roundeights.hasher.Implicits._
      (ctx.userId | "anon").salt(blindCookieSalt).md5.hex
    }
  }

  val pdnDump = new PdnDump(
    dumper = gamePdnDump,
    annotator = annotator,
    getSimulName = getSimulName,
    getTournamentName = getTournamentName,
    getSwissName = swissEnv.getName
  )

  val userApi = new UserApi(
    jsonView = userEnv.jsonView,
    lightUserApi = userEnv.lightUserApi,
    makeUrl = makeUrl,
    relationApi = relationApi,
    bookmarkApi = bookmarkApi,
    crosstableApi = crosstableApi,
    playBanApi = playBanApi,
    gameCache = gameCache,
    isStreaming = isStreaming,
    isPlaying = isPlaying,
    isOnline = userEnv.onlineUserIdMemo.get,
    recentTitledUserIds = () => userEnv.recentTitledUserIdMemo.keys,
    prefApi = prefApi,
    urgentGames = urgentGames
  )(system)

  val gameApi = new GameApi(
    netBaseUrl = Net.BaseUrl,
    apiToken = apiToken,
    pdnDump = pdnDump,
    gameCache = gameCache,
    crosstableApi = crosstableApi
  )

  val gameApiV2 = new GameApiV2(
    pdnDump = pdnDump,
    swissApi = swissEnv.api,
    getLightUser = userEnv.lightUser,
    upgradeOngoingGame = updateIfPresent
  )(system)

  val userGameApi = new UserGameApi(
    bookmarkApi = bookmarkApi,
    lightUser = userEnv.lightUserSync,
    getTournamentName = getTournamentName
  )

  val roundApi = new RoundApi(
    jsonView = roundJsonView,
    noteApi = noteApi,
    forecastApi = forecastApi,
    bookmarkApi = bookmarkApi,
    swissApi = swissEnv.api,
    tourApi = tournamentApi,
    anaCacheApi = anaCacheApi,
    getSimul = getSimul,
    getTeamName = getTeamName,
    getLightUser = userEnv.lightUserSync
  )

  val lobbyApi = new LobbyApi(
    getFilter = setupEnv.filter,
    lightUserApi = userEnv.lightUserApi,
    seekApi = lobbyEnv.seekApi,
    pools = pools,
    urgentGames = urgentGames
  )

  lazy val eventStream = new EventStream(system, challengeJsonView, userEnv.onlineUserIdMemo.put)

  private def makeUrl(path: String): String = s"${Net.BaseUrl}/$path"

  lazy val cli = new Cli(system.lidraughtsBus)

  KamonPusher.start(system) {
    new KamonPusher(countUsers = () => userEnv.onlineUserIdMemo.count)
  }

  if (InfluxEventEnv != "dev") system.actorOf(Props(new InfluxEvent(
    endpoint = InfluxEventEndpoint,
    env = InfluxEventEnv
  )), name = "influx-event")

  system.registerOnTermination {
    system.lidraughtsBus.publish(lidraughts.hub.actorApi.Shutdown, 'shutdown)
  }
}

object Env {

  lazy val current = "api" boot new Env(
    config = lidraughts.common.PlayApp.loadConfig,
    settingStore = lidraughts.memo.Env.current.settingStore,
    renderer = lidraughts.hub.Env.current.renderer,
    userEnv = lidraughts.user.Env.current,
    annotator = lidraughts.analyse.Env.current.annotator,
    lobbyEnv = lidraughts.lobby.Env.current,
    setupEnv = lidraughts.setup.Env.current,
    swissEnv = lidraughts.swiss.Env.current,
    getSimul = lidraughts.simul.Env.current.repo.find,
    getSimulName = lidraughts.simul.Env.current.api.idToName,
    getTournamentName = lidraughts.tournament.Env.current.cached.name,
    getTeamName = lidraughts.team.Env.current.cached.name _,
    roundJsonView = lidraughts.round.Env.current.jsonView,
    noteApi = lidraughts.round.Env.current.noteApi,
    forecastApi = lidraughts.round.Env.current.forecastApi,
    urgentGames = lidraughts.round.Env.current.proxy.urgentGames,
    updateIfPresent = lidraughts.round.Env.current.proxy.updateIfPresent,
    relationApi = lidraughts.relation.Env.current.api,
    bookmarkApi = lidraughts.bookmark.Env.current.api,
    tournamentApi = lidraughts.tournament.Env.current.api,
    crosstableApi = lidraughts.game.Env.current.crosstableApi,
    playBanApi = lidraughts.playban.Env.current.api,
    prefApi = lidraughts.pref.Env.current.api,
    anaCacheApi = lidraughts.anaCache.Env.current.api,
    gamePdnDump = lidraughts.game.Env.current.pdnDump,
    gameCache = lidraughts.game.Env.current.cached,
    system = lidraughts.common.PlayApp.system,
    scheduler = lidraughts.common.PlayApp.scheduler,
    isStreaming = lidraughts.streamer.Env.current.liveStreamApi.isStreaming,
    isPlaying = lidraughts.relation.Env.current.online.isPlaying,
    pools = lidraughts.pool.Env.current.api.configs,
    challengeJsonView = lidraughts.challenge.Env.current.jsonView,
    isProd = lidraughts.common.PlayApp.isProd
  )
}
