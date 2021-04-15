package lidraughts.externalTournament

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
  private val selectJoined = $doc("status" -> ExternalPlayer.Status.Joined.id)

  def count(tourId: ExternalTournament.ID): Fu[Int] = coll.countSel(selectTour(tourId))

  def removeByTour(tourId: ExternalTournament.ID) = coll.remove(selectTour(tourId)).void

  def insert(player: ExternalPlayer) = coll.insert(player)

  def remove(tourId: ExternalTournament.ID, userId: User.ID) =
    coll.remove(selectTourUser(tourId, userId)).void

  def filterExists(tourIds: List[ExternalTournament.ID], userId: User.ID): Fu[List[ExternalTournament.ID]] =
    coll.primitive[ExternalTournament.ID]($doc(
      "tourId" $in tourIds,
      "userId" -> userId
    ), "tourId")

  def exists(tourId: ExternalTournament.ID, userId: User.ID) =
    coll.exists(selectTourUser(tourId, userId))

  def find(tourId: ExternalTournament.ID, userId: User.ID): Fu[Option[ExternalPlayer]] =
    coll.find(selectTourUser(tourId, userId)).uno[ExternalPlayer]

  def update(tourId: ExternalTournament.ID, userId: User.ID)(f: ExternalPlayer => Fu[ExternalPlayer]) =
    find(tourId, userId) flatten s"No such player: $tourId/$userId" flatMap f flatMap { player =>
      coll.update(selectId(player._id), player).void
    }

  private[externalTournament] def userIds(tourId: ExternalTournament.ID): Fu[List[User.ID]] =
    coll.distinct[User.ID, List]("userId", selectTour(tourId).some)

  private[externalTournament] def joinedUserIds(tourId: ExternalTournament.ID): Fu[List[User.ID]] =
    coll.distinct[User.ID, List](
      "userId", (selectTour(tourId) ++ selectJoined).some
    )

  def byTour(tourId: ExternalTournament.ID): Fu[List[ExternalPlayer]] =
    coll.find(selectTour(tourId))
      .list[ExternalPlayer]()

  def setStatus(id: ExternalPlayer.ID, status: ExternalPlayer.Status) =
    coll.updateField(selectId(id), "status", status.id) void
}
