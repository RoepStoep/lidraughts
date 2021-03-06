package lidraughts.app
package templating

import controllers.routes
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.tournament.Env.{ current => tournamentEnv }
import lidraughts.tournament.{ Tournament, System, Schedule }
import lidraughts.user.{ User, UserContext }

import play.api.libs.json.Json

trait TournamentHelper { self: I18nHelper with DateHelper with UserHelper =>

  def netBaseUrl: String

  def tournamentJsData(tour: Tournament, version: Int, user: Option[User]) = {

    val data = Json.obj(
      "tournament" -> Json.obj("id" -> tour.id),
      "version" -> version
    )
    Json stringify {
      user.fold(data) { u => data ++ Json.obj("username" -> u.username) }
    }
  }

  def tournamentLink(tour: Tournament): Frag = a(
    dataIcon := "g",
    cls := (if (tour.isScheduled) "text is-gold" else "text"),
    href := routes.Tournament.show(tour.id).url
  )(tour.fullName)

  def tournamentLink(tourId: String): Frag = a(
    dataIcon := "g",
    cls := "text",
    href := routes.Tournament.show(tourId).url
  )(tournamentIdToName(tourId))

  def tournamentIdToName(id: String) = tournamentEnv.cached name id getOrElse "Tournament"

  object scheduledTournamentNameShortHtml {
    private def icon(c: Char) = s"""<span data-icon="$c"></span>"""
    private val replacements = List(
      "Lidraughts " -> "",
      "Marathon" -> icon('\\'),
      "HyperBullet" -> s"H${icon(lidraughts.rating.PerfType.Bullet.iconChar)}",
      "SuperBlitz" -> s"S${icon(lidraughts.rating.PerfType.Blitz.iconChar)}"
    ) ::: lidraughts.rating.PerfType.leaderboardable.map { pt =>
        pt.name -> icon(pt.iconChar)
      }
    def apply(name: String): Frag = raw {
      replacements.foldLeft(name) {
        case (n, (from, to)) => n.replace(from, to)
      }
    }
  }

  def systemName(sys: System)(implicit ctx: UserContext) = sys match {
    case System.Arena => System.Arena.toString
  }

  def tournamentIconChar(tour: Tournament): String = tour.schedule.map(_.freq) match {
    case Some(Schedule.Freq.Marathon | Schedule.Freq.ExperimentalMarathon) => "\\"
    case _ => tour.spotlight.flatMap(_.iconFont) | tour.perfType.fold('g')(_.iconChar).toString
  }
}
