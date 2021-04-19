package lidraughts.externalTournament

import reactivemongo.api.commands.GetLastError
import reactivemongo.bson._

import BsonHandlers._
import lidraughts.db.dsl._
import lidraughts.user.User

object ExternalPlayerRepo {

  private lazy val coll = Env.current.externalPlayerColl

  private def selectId(id: ExternalPlayer.ID) = $doc("_id" -> id)
  private def selectTour(tourId: ExternalTournament.ID) = $doc("tourId" -> tourId)
  private def selectTourUser(tourId: ExternalTournament.ID, userId: User.ID) = $doc(
    "tourId" -> tourId,
    "userId" -> userId
  )
  private val selectAccepted = $doc("status" -> ExternalPlayer.Status.Accepted.id)

  def countAccepted(tourId: ExternalTournament.ID): Fu[Int] =
    coll.countSel(selectTour(tourId) ++ selectAccepted)

  def insert(player: ExternalPlayer) =
    coll.insert(player).void

  def remove(player: ExternalPlayer) =
    coll.remove(selectId(player.id)).void

  def exists(tourId: ExternalTournament.ID, userId: User.ID) =
    coll.exists(selectTourUser(tourId, userId))

  def find(tourId: ExternalTournament.ID, userId: User.ID): Fu[Option[ExternalPlayer]] =
    coll.find(selectTourUser(tourId, userId)).uno[ExternalPlayer]

  def findAccepted(tourId: ExternalTournament.ID, userId: User.ID): Fu[Option[ExternalPlayer]] =
    coll.find(selectTourUser(tourId, userId) ++ selectAccepted).uno[ExternalPlayer]

  def update(tourId: ExternalTournament.ID, userId: User.ID)(f: ExternalPlayer => Fu[ExternalPlayer]) =
    find(tourId, userId) flatten s"No such player: $tourId/$userId" flatMap f flatMap { player =>
      coll.update(selectId(player._id), player).void
    }

  def byTour(tourId: ExternalTournament.ID): Fu[List[ExternalPlayer]] =
    coll.find(selectTour(tourId))
      .list[ExternalPlayer]()

  def acceptedByTour(tourId: ExternalTournament.ID): Fu[List[ExternalPlayer]] =
    coll.find(selectTour(tourId) ++ selectAccepted)
      .list[ExternalPlayer]()

  def setStatus(id: ExternalPlayer.ID, status: ExternalPlayer.Status) =
    coll.updateField(selectId(id), "status", status.id) void

  def setRank(id: ExternalPlayer.ID, rank: Int) =
    coll.updateField(selectId(id), "rank", rank) void

  def searchPlayers(tourId: ExternalTournament.ID, term: String, nb: Int): Fu[List[User.ID]] =
    User.couldBeUsername(term) ?? {
      coll.primitive[User.ID](
        selector = $doc(
          "tourId" -> tourId,
          "userId" $startsWith term.toLowerCase
        ),
        sort = $sort desc "points",
        nb = nb,
        field = "userId"
      )
    }
}
