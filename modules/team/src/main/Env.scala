package lidraughts.team

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent.duration._

import lidraughts.common.{ LightWfdUser, MaxPerPage }
import lidraughts.mod.ModlogApi
import lidraughts.notify.NotifyApi
import lidraughts.socket.History
import lidraughts.socket.Socket.{ GetVersion, SocketVersion }

final class Env(
    config: Config,
    hub: lidraughts.hub.Env,
    modLog: ModlogApi,
    notifyApi: NotifyApi,
    system: ActorSystem,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    db: lidraughts.db.Env,
    flood: lidraughts.security.Flood,
    userEnv: lidraughts.user.Env
) {

  private val settings = new {
    val CollectionTeam = config getString "collection.team"
    val CollectionMember = config getString "collection.member"
    val CollectionRequest = config getString "collection.request"
    val HistoryMessageTtl = config duration "history.message.ttl"
    val UidTimeout = config duration "uid.timeout"
    val SocketTimeout = config duration "socket.timeout"
    val PaginatorMaxPerPage = config getInt "paginator.max_per_page"
    val PaginatorMaxUserPerPage = config getInt "paginator.max_user_per_page"
  }
  import settings._

  lazy val colls = new Colls(
    team = db(CollectionTeam),
    request = db(CollectionRequest),
    member = db(CollectionMember)
  )

  lazy val forms = new DataForm(colls.team, hub.captcher)

  lazy val memberStream = new TeamMemberStream(colls.member)(system)

  lazy val api = new TeamApi(
    coll = colls,
    cached = cached,
    notifier = notifier,
    bus = system.lidraughtsBus,
    indexer = hub.teamSearch,
    timeline = hub.timeline,
    modLog = modLog
  )

  lazy val paginator = new PaginatorBuilder(
    coll = colls,
    maxPerPage = MaxPerPage(PaginatorMaxPerPage),
    maxUserPerPage = MaxPerPage(PaginatorMaxUserPerPage),
    lightUserApi = userEnv.lightUserApi,
    lightWfdUserApi = userEnv.lightWfdUserApi
  )

  private val socketMap: SocketMap = lidraughts.socket.SocketMap[TeamSocket](
    system = system,
    mkTrouper = (teamId: String) => new TeamSocket(
      system = system,
      teamId = teamId,
      history = new History(ttl = HistoryMessageTtl),
      lightUser = userEnv.lightUser,
      lightWfdUser = userEnv.lightWfdUser,
      toWfdName = userEnv.wfdUsername,
      isWfdTeam = cached.isWfd,
      uidTtl = UidTimeout,
      keepMeAlive = () => socketMap touch teamId
    ),
    accessTimeout = SocketTimeout,
    monitoringName = "team.socketMap",
    broomFrequency = 3853 millis
  )

  lazy val cli = new Cli(api, colls)

  lazy val cached = new Cached(asyncCache)(system)

  def version(teamId: Team.ID): Fu[SocketVersion] =
    socketMap.askIfPresentOrZero[SocketVersion](teamId)(GetVersion)

  lazy val socketHandler = new SocketHandler(
    hub = hub,
    socketMap = socketMap,
    chat = hub.chat,
    flood = flood
  )

  private lazy val notifier = new Notifier(notifyApi = notifyApi)

  system.lidraughtsBus.subscribeFun('shadowban, 'teamJoinedBy) {
    case lidraughts.hub.actorApi.mod.Shadowban(userId, true) => api deleteRequestsByUserId userId
    case lidraughts.hub.actorApi.team.TeamIdsJoinedBy(userId, promise) =>
      promise completeWith cached.teamIdsList(userId)
  }
}

object Env {

  lazy val current = "team" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "team",
    hub = lidraughts.hub.Env.current,
    modLog = lidraughts.mod.Env.current.logApi,
    notifyApi = lidraughts.notify.Env.current.api,
    system = lidraughts.common.PlayApp.system,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    db = lidraughts.db.Env.current,
    flood = lidraughts.security.Env.current.flood,
    userEnv = lidraughts.user.Env.current
  )
}
