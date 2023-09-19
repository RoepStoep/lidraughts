package lidraughts.app
package mashup

import lidraughts.forum.MiniForumPost
import lidraughts.team.{ Team, RequestRepo, MemberRepo, RequestWithUser, TeamApi }
import lidraughts.tournament.{ Tournament, TournamentRepo }
import lidraughts.user.{ User, UserRepo }
import lidraughts.swiss.{ Swiss, SwissApi }

case class TeamInfo(
    mine: Boolean,
    createdByMe: Boolean,
    requestedByMe: Boolean,
    subscribed: Boolean,
    requests: List[RequestWithUser],
    forumPosts: List[MiniForumPost],
    tours: List[TeamInfo.AnyTour]
) {

  import TeamInfo._

  def hasRequests = requests.nonEmpty

  def userIds = forumPosts.flatMap(_.userId)

  lazy val featuredTours: List[AnyTour] = {
    val (enterable, finished) = tours.partition(_.isEnterable) match {
      case (e, f) => e.sortBy(_.startsAt).take(5) -> f.sortBy(-_.startsAt.getSeconds).take(5)
    }
    enterable ::: finished.take(5 - enterable.size)
  }
}

object TeamInfo {
  case class AnyTour(any: Either[Tournament, Swiss]) extends AnyVal {
    def isEnterable = any.fold(_.isEnterable, _.isEnterable)
    def startsAt = any.fold(_.startsAt, _.startsAt)
    def isNowOrSoon = any.fold(_.isNowOrSoon, _.isNowOrSoon)
    def nbPlayers = any.fold(_.nbPlayers, _.nbPlayers)
  }
  def anyTour(tour: Tournament) = AnyTour(Left(tour))
  def anyTour(swiss: Swiss) = AnyTour(Right(swiss))
}

final class TeamInfoApi(
    api: TeamApi,
    swissApi: SwissApi,
    getForumPosts: String => Fu[List[MiniForumPost]],
    preloadTeams: Set[Team.ID] => Funit
) {

  import TeamInfo._

  def apply(team: Team, me: Option[User]): Fu[TeamInfo] = for {
    requests ← (team.enabled && me.??(m => team.isCreator(m.id))) ?? api.requestsWithUsers(team)
    mine <- me.??(m => api.belongsTo(team.id, m.id))
    requestedByMe ← !mine ?? me.??(m => RequestRepo.exists(team.id, m.id))
    subscribed <- me.ifTrue(mine) ?? { api.isSubscribed(team, _) }
    forumPosts ← getForumPosts(team.id)
    tours <- TournamentRepo.byTeam(team.id, 5, withTeamBattle = !team.isWfd).flatMap { tours =>
      if (tours.size < 5 || tours.exists(_.isFinished)) fuccess(tours)
      else TournamentRepo.byTeamUpcoming(team.id, 5, withTeamBattle = !team.isWfd).dmap(_.reverse)
    }
    _ <- tours.nonEmpty ?? {
      preloadTeams(tours.flatMap(_.teamBattle.??(_.teams)).toSet)
    }
    swisses <- swissApi.featuredInTeam(team.id)
  } yield TeamInfo(
    mine = mine,
    createdByMe = ~me.map(m => team.isCreator(m.id)),
    requestedByMe = requestedByMe,
    subscribed = subscribed,
    requests = requests,
    forumPosts = forumPosts,
    tours = tours.map(anyTour) ::: swisses.map(anyTour)
  )

  def tournaments(team: Team, nb: Int): Fu[List[AnyTour]] =
    for {
      tours <- TournamentRepo.byTeam(team.id, nb, withTeamBattle = !team.isWfd)
      swisses <- swissApi.visibleInTeam(team.id, nb)
    } yield {
      tours.map(anyTour) ::: swisses.map(anyTour)
    }.sortBy(-_.startsAt.getSeconds)
}
