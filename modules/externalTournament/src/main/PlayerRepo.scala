package lidraughts.externalTournament

import reactivemongo.bson._

import BsonHandlers._
import lidraughts.db.dsl._
import lidraughts.user.User

object PlayerRepo {

  private lazy val coll = Env.current.externalPlayerColl

  private def selectId(id: ExternalTournament.ID) = $doc("_id" -> id)
  private def selectTour(tourId: ExternalTournament.ID) = $doc("tourId" -> tourId)
  private def selectTourUser(tourId: ExternalTournament.ID, userId: User.ID) = $doc(
    "tourId" -> tourId,
    "userId" -> userId
  )
  private val selectAutoStart = $doc("autoStart" -> true)

  def count(tourId: ExternalTournament.ID): Fu[Int] = coll.countSel(selectTour(tourId))

  def removeByTour(tourId: ExternalTournament.ID) = coll.remove(selectTour(tourId)).void

  def insert(player: ExternalPlayer) = coll.insert(player).void

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

  private[externalTournament] def autoStartUserIds(tourId: ExternalTournament.ID): Fu[List[User.ID]] =
    coll.distinct[User.ID, List](
      "userId", (selectTour(tourId) ++ selectAutoStart).some
    )

  def byTour(tourId: ExternalTournament.ID): Fu[List[ExternalPlayer]] =
    coll.find(selectTour(tourId))
      .list[ExternalPlayer]()

  def searchPlayers(tourId: ExternalTournament.ID, term: String, nb: Int): Fu[List[User.ID]] =
    User.couldBeUsername(term) ?? {
      coll.primitive[User.ID](
        selector = $doc(
          "tourId" -> tourId,
          "userId" $startsWith term.toLowerCase
        ),
        sort = $sort asc "userId",
        nb = nb,
        field = "userId"
      )
    }
}
