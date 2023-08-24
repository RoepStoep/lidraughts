package lidraughts.team

import lidraughts.memo.Syncache
import scala.concurrent.duration._

private[team] final class Cached(
    asyncCache: lidraughts.memo.AsyncCache.Builder
)(implicit system: akka.actor.ActorSystem) {

  val nameCache = new Syncache[String, Option[String]](
    name = "team.name",
    compute = TeamRepo.name,
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def name(id: String) = nameCache sync id

  def preloadSet = nameCache preloadSet _

  val wfdCache = new Syncache[String, Boolean](
    name = "team.wfd",
    compute = TeamRepo.isWfd,
    default = _ => false,
    strategy = Syncache.WaitAfterUptime(10 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def isWfd(id: String) = wfdCache sync id

  // ~ 30k entries as of 04/02/17
  private val teamIdsCache = new Syncache[lidraughts.user.User.ID, Team.IdsStr](
    name = "team.ids",
    compute = u => MemberRepo.teamIdsByUser(u).dmap(Team.IdsStr.apply),
    default = _ => Team.IdsStr.empty,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def syncTeamIds = teamIdsCache sync _
  def teamIds = teamIdsCache async _
  def teamIdsList(userId: lidraughts.user.User.ID) = teamIds(userId).dmap(_.toList)

  def invalidateTeamIds = teamIdsCache invalidate _

  val nbRequests = asyncCache.clearable[lidraughts.user.User.ID, Int](
    name = "team.nbRequests",
    f = userId => TeamRepo teamIdsByCreator userId flatMap RequestRepo.countByTeams,
    expireAfter = _.ExpireAfterAccess(12 minutes)
  )
}
