package lidraughts.study

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.common.Strings
import lidraughts.hub.actorApi.socket.HasUserId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.socket.Socket.{ GetVersion, SocketVersion }
import lidraughts.user.User

final class Env(
    config: Config,
    lightUserApi: lidraughts.user.LightUserApi,
    settingStore: lidraughts.memo.SettingStore.Builder,
    gamePdnDump: lidraughts.game.PdnDump,
    divider: lidraughts.game.Divider,
    importer: lidraughts.importer.Importer,
    explorerImporter: lidraughts.explorer.ExplorerImporter,
    evalCacheHandler: lidraughts.evalCache.EvalCacheSocketHandler,
    notifyApi: lidraughts.notify.NotifyApi,
    getPref: User => Fu[lidraughts.pref.Pref],
    getRelation: (User.ID, User.ID) => Fu[Option[lidraughts.relation.Relation]],
    system: ActorSystem,
    hub: lidraughts.hub.Env,
    db: lidraughts.db.Env,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  private val settings = new {
    val CollectionStudy = config getString "collection.study"
    val CollectionChapter = config getString "collection.chapter"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val SequencerTimeout = config duration "sequencer.timeout"
    val NetDomain = config getString "net.domain"
    val NetBaseUrl = config getString "net.base_url"
    val MaxPerPage = config getInt "paginator.max_per_page"
  }
  import settings._

  lazy val rateLimitDisabledSetting = {
    import lidraughts.memo.SettingStore.Strings._
    settingStore[Strings](
      "rateLimitDisabled",
      default = Strings(Nil),
      text = "Users who are not ratelimited for study invites/clones - lidraughts usernames separated by a comma".some
    )
  }

  val socketMap: SocketMap = lidraughts.socket.SocketMap[StudySocket](
    system = system,
    mkTrouper = (studyId: String) => new StudySocket(
      system = system,
      studyId = Study.Id(studyId),
      jsonView = jsonView,
      studyRepo = studyRepo,
      chapterRepo = chapterRepo,
      lightUserApi = lightUserApi,
      history = new lidraughts.socket.History(ttl = HistoryMessageTtl),
      uidTtl = UidTimeout,
      lightStudyCache = lightStudyCache,
      keepMeAlive = () => socketMap touch studyId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "study.socketMap",
    broomFrequency = 3697 millis
  )

  def version(studyId: Study.Id): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](studyId.value)(GetVersion)

  def isConnected(studyId: Study.Id, userId: User.ID): Fu[Boolean] =
    socketMap.askIfPresentOrZero[Boolean](studyId.value)(HasUserId(userId, _))

  lazy val socketHandler = new SocketHandler(
    hub = hub,
    socketMap = socketMap,
    chat = hub.chat,
    api = api,
    rateLimitDisabled = rateLimitDisabledSetting.get,
    evalCacheHandler = evalCacheHandler
  )

  private lazy val chapterColl = db(CollectionChapter)

  lazy val studyRepo = new StudyRepo(coll = db(CollectionStudy))
  lazy val chapterRepo = new ChapterRepo(coll = chapterColl)

  lazy val jsonView = new JsonView(
    studyRepo,
    lightUserApi
  )

  private lazy val chapterMaker = new ChapterMaker(
    importer = importer,
    pdnFetch = new PdnFetch,
    pdnDump = gamePdnDump,
    lightUser = lightUserApi,
    chat = hub.chat,
    domain = NetDomain
  )

  private lazy val explorerGame = new ExplorerGame(
    importer = explorerImporter,
    lightUser = lightUserApi.sync,
    baseUrl = NetBaseUrl
  )

  private lazy val studyMaker = new StudyMaker(
    lightUser = lightUserApi.sync,
    chapterMaker = chapterMaker
  )

  private lazy val studyInvite = new StudyInvite(
    studyRepo = studyRepo,
    notifyApi = notifyApi,
    getRelation = getRelation,
    getPref = getPref,
    rateLimitDisabled = rateLimitDisabledSetting.get
  )

  private lazy val sequencer = new StudySequencer(
    studyRepo,
    chapterRepo,
    sequencers = new DuctMap(
      mkDuct = _ => Duct.extra.lazyPromise(5.seconds.some)(system),
      accessTimeout = SequencerTimeout
    )
  )

  private lazy val serverEvalRequester = new ServerEval.Requester(
    draughtsnetActor = hub.draughtsnet,
    chapterRepo = chapterRepo
  )

  lazy val serverEvalMerger = new ServerEval.Merger(
    sequencer = sequencer,
    api = api,
    chapterRepo = chapterRepo,
    socketMap = socketMap,
    divider = divider
  )

  lazy val api = new StudyApi(
    studyRepo = studyRepo,
    chapterRepo = chapterRepo,
    sequencer = sequencer,
    chapterMaker = chapterMaker,
    studyMaker = studyMaker,
    inviter = studyInvite,
    explorerGameHandler = explorerGame,
    lightUser = lightUserApi.sync,
    scheduler = system.scheduler,
    chat = hub.chat,
    bus = system.lidraughtsBus,
    timeline = hub.timeline,
    socketMap = socketMap,
    serverEvalRequester = serverEvalRequester,
    lightStudyCache = lightStudyCache
  )

  lazy val pager = new StudyPager(
    studyRepo = studyRepo,
    chapterRepo = chapterRepo,
    maxPerPage = lidraughts.common.MaxPerPage(MaxPerPage)
  )

  lazy val multiBoard = new StudyMultiBoard(
    runCommand = db.runCommand,
    chapterColl = chapterColl,
    maxPerPage = lidraughts.common.MaxPerPage(9)
  )

  lazy val pdnDump = new PdnDump(
    chapterRepo = chapterRepo,
    gamePdnDump = gamePdnDump,
    lightUser = lightUserApi.sync,
    netBaseUrl = NetBaseUrl
  )

  lazy val lightStudyCache: LightStudyCache = asyncCache.multi(
    name = "study.lightStudyCache",
    f = studyRepo.lightById,
    expireAfter = _.ExpireAfterWrite(20 minutes)
  )

  def cli = new lidraughts.common.Cli {
    def process = {
      case "study" :: "rank" :: "reset" :: Nil => api.resetAllRanks.map { count => s"$count done" }
    }
  }

  system.lidraughtsBus.subscribeFun('gdprErase, 'deploy) {
    case lidraughts.user.User.GDPRErase(user) => api erase user
    case m: lidraughts.hub.actorApi.Deploy => socketMap tellAll m
  }
  system.lidraughtsBus.subscribeFun('studyAnalysisProgress) {
    case lidraughts.analyse.actorApi.StudyAnalysisProgress(analysis, complete) => serverEvalMerger(analysis, complete)
  }
}

object Env {

  lazy val current: Env = "study" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "study",
    lightUserApi = lidraughts.user.Env.current.lightUserApi,
    settingStore = lidraughts.memo.Env.current.settingStore,
    gamePdnDump = lidraughts.game.Env.current.pdnDump,
    divider = lidraughts.game.Env.current.divider,
    importer = lidraughts.importer.Env.current.importer,
    explorerImporter = lidraughts.explorer.Env.current.importer,
    evalCacheHandler = lidraughts.evalCache.Env.current.socketHandler,
    notifyApi = lidraughts.notify.Env.current.api,
    getPref = lidraughts.pref.Env.current.api.getPref,
    getRelation = lidraughts.relation.Env.current.api.fetchRelation,
    system = lidraughts.common.PlayApp.system,
    hub = lidraughts.hub.Env.current,
    db = lidraughts.db.Env.current,
    asyncCache = lidraughts.memo.Env.current.asyncCache
  )
}
