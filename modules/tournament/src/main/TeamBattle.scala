package lidraughts.tournament

import play.api.data._
import play.api.data.Forms._

import lidraughts.hub.lightTeam._
import lidraughts.user.User

case class TeamBattle(
    teams: Set[TeamId],
    nbLeaders: Int
) {
  def hasEnoughTeams = teams.size > 1
  lazy val sortedTeamIds = teams.toList.sorted

  def hasTooManyTeams = teams.size > TeamBattle.displayTeams
}

object TeamBattle {

  val maxTeams = 100
  val displayTeams = 10

  def init(teamId: TeamId) = TeamBattle(Set(teamId), 5)

  case class TeamVs(teams: draughts.Color.Map[TeamId])

  case class RankedTeam(
      rank: Int,
      teamId: TeamId,
      leaders: List[TeamLeader]
  ) {
    def magicScore = leaders.foldLeft(0)(_ + _.magicScore)
    def score = leaders.foldLeft(0)(_ + _.score)
  }

  case class TeamLeader(userId: User.ID, magicScore: Int) {
    def score: Int = magicScore / 10000
  }

  case class TeamInfo(
      teamId: TeamId,
      nbPlayers: Int,
      avgRating: Int,
      avgPerf: Int,
      avgScore: Int,
      topPlayers: List[Player]
  )

  object DataForm {
    import play.api.data.Forms._
    import lidraughts.common.Form._

    val fields = mapping(
      "teams" -> nonEmptyText,
      "nbLeaders" -> number(min = 1, max = 20)
    )(Setup.apply)(Setup.unapply)
      .verifying("We need at least 2 teams", s => s.potentialTeamIds.size > 1)
      .verifying(
        s"In this version of team battles, no more than $maxTeams teams can be allowed",
        s => s.potentialTeamIds.size <= maxTeams
      )

    def edit(teams: List[String], nbLeaders: Int) = Form(fields) fill
      Setup(s"${teams mkString "\n"}\n", nbLeaders)

    def empty = Form(fields)

    case class Setup(
        teams: String,
        nbLeaders: Int
    ) {
      // guess if newline or comma separated
      def potentialTeamIds: Set[String] = {
        val lines = teams.lines.toList
        val dirtyIds =
          if (lines.size > 1) lines.map(_.takeWhile(' ' !=))
          else lines.headOption.??(_.split(',').toList)
        dirtyIds.map(_.trim).filter(_.nonEmpty).toSet
      }
    }
  }
}
