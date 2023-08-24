package lidraughts.tournament

import scala.concurrent.duration._

import lidraughts.hub.lightTeam.TeamId
import lidraughts.memo._
import lidraughts.user.User

private[tournament] final class Cached(
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    createdTtl: FiniteDuration,
    rankingTtl: FiniteDuration
)(implicit system: akka.actor.ActorSystem) {

  val nameCache = new Syncache[Tournament.ID, Option[String]](
    name = "tournament.name",
    compute = id => TournamentRepo byId id map2 { (tour: Tournament) => tour.fullName },
    default = _ => none,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def name(id: Tournament.ID): Option[String] = nameCache sync id

  val wfdCache = new Syncache[Tournament.ID, Boolean](
    name = "tournament.wfd",
    compute = TournamentRepo.isWfd,
    default = _ => false,
    strategy = Syncache.WaitAfterUptime(10 millis),
    expireAfter = Syncache.ExpireAfterAccess(1 hour),
    logger = logger
  )

  def isWfd(id: Tournament.ID) = wfdCache sync id

  val promotable = asyncCache.single(
    name = "tournament.promotable",
    TournamentRepo.promotable,
    expireAfter = _.ExpireAfterWrite(createdTtl)
  )

  def ranking(tour: Tournament): Fu[Ranking] =
    if (tour.isFinished) finishedRanking get tour.id
    else ongoingRanking get tour.id

  private[tournament] val teamInfo = asyncCache.multi[(Tournament.ID, TeamId), Option[TeamBattle.TeamInfo]](
    name = "tournament.teamInfo",
    f = {
      case (tourId, teamId) =>
        PlayerRepo.teamInfo(tourId, teamId) dmap some
    },
    expireAfter = _.ExpireAfterWrite(5.seconds)
  )

  // only applies to ongoing tournaments
  private val ongoingRanking = asyncCache.multi[Tournament.ID, Ranking](
    name = "tournament.ongoingRanking",
    f = PlayerRepo.computeRanking,
    expireAfter = _.ExpireAfterWrite(3.seconds)
  )

  // only applies to finished tournaments
  private val finishedRanking = asyncCache.multi[Tournament.ID, Ranking](
    name = "tournament.finishedRanking",
    f = PlayerRepo.computeRanking,
    expireAfter = _.ExpireAfterAccess(rankingTtl)
  )

  object battle {

    val teamStanding = asyncCache.multi[Tournament.ID, List[TeamBattle.RankedTeam]](
      name = "tournament.teamStanding",
      id => TournamentRepo.teamBattleOf(id) flatMap {
        _ ?? { PlayerRepo.bestTeamIdsByTour(id, _) }
      },
      expireAfter = _.ExpireAfterWrite(1 second)
    )
  }

  private[tournament] object sheet {

    import arena.ScoringSystem.Sheet

    private case class SheetKey(tourId: Tournament.ID, userId: User.ID, streakable: Streakable)

    def apply(tour: Tournament, userId: User.ID): Fu[Sheet] =
      cache.get(SheetKey(tour.id, userId, if (tour.streakable) Streaks else NoStreaks))

    def update(tour: Tournament, userId: User.ID): Fu[Sheet] = {
      val key = SheetKey(tour.id, userId, if (tour.streakable) Streaks else NoStreaks)
      cache.refresh(key)
      cache.get(key)
    }

    private def compute(key: SheetKey): Fu[Sheet] =
      PairingRepo.finishedByPlayerChronological(key.tourId, key.userId) map {
        arena.ScoringSystem.sheet(key.userId, _, key.streakable)
      }

    private val cache = asyncCache.multi[SheetKey, Sheet](
      name = "tournament.sheet",
      f = compute,
      expireAfter = _.ExpireAfterAccess(3.minutes)
    )
  }
}
