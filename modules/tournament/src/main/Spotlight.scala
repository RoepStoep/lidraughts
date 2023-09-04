package lidraughts.tournament

import org.joda.time.DateTime

import lidraughts.user.User

case class Spotlight(
    headline: String,
    description: String,
    homepageHours: Option[Int] = None, // feature on homepage hours before start
    iconFont: Option[String] = None,
    iconImg: Option[String] = None,
    drawLimit: Option[Int] = None
)

object Spotlight {

  import Schedule.Freq._

  def select(tours: List[Tournament], user: Option[User], max: Int): List[Tournament] =
    user.fold(sort(tours) take max) { select(tours, _, max) }

  def select(tours: List[Tournament], user: User, max: Int): List[Tournament] =
    sort(tours.filter { select(_, user) }) take max

  private def sort(tours: List[Tournament]) = tours.sortBy { t =>
    -(t.schedule.fold(if (t.isPromoted) userPromotedImportance else 0)(_.freq.importance))
  }

  private def select(tour: Tournament, user: User): Boolean = tour.isEnterable &&
    tour.spotlight.fold(automatically(tour, user)) { manually(tour, _) }

  private def manually(tour: Tournament, spotlight: Spotlight): Boolean =
    spotlight.homepageHours.exists { hours =>
      tour.startsAt.minusHours(hours) isBefore DateTime.now
    }

  private def automatically(tour: Tournament, user: User): Boolean = tour.perfType ?? { pt =>
    if (tour.isPromoted) true
    else tour.schedule ?? { sched =>
      def playedSinceWeeks(weeks: Int) = user.perfs(pt).latest ?? { l =>
        l.plusWeeks(weeks) isAfter DateTime.now
      }
      sched.freq match {
        case Hourly => canMaybeJoinLimited(tour, user) && playedSinceWeeks(2)
        case Daily | Eastern => playedSinceWeeks(2)
        case Weekly | Weekend => playedSinceWeeks(4)
        case Unique => playedSinceWeeks(4)
        case Monthly | Shield | Marathon | Yearly => true
        case ExperimentalMarathon => false
      }
    }
  }

  private def canMaybeJoinLimited(tour: Tournament, user: User): Boolean =
    tour.conditions.isRatingLimited &&
      tour.conditions.nbRatedGame.fold(true) { c => c(user).accepted } &&
      tour.conditions.minRating.fold(true) { c => c(user).accepted } &&
      tour.conditions.maxRating.fold(true)(_ maybe user)
}
