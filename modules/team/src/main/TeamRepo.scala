package lidraughts.team

import org.joda.time.{ DateTime, Period }
import reactivemongo.api._
import reactivemongo.bson._

import lidraughts.db.dsl._
import lidraughts.user.User

object TeamRepo {

  // dirty
  private val coll = Env.current.colls.team

  import BSONHandlers._

  def byOrderedIds(ids: Seq[Team.ID]) = coll.byOrderedIds[Team, Team.ID](ids)(_.id)

  def exists(id: Team.ID) = coll exists $id(id)

  def cursor(
    readPreference: ReadPreference = ReadPreference.secondaryPreferred
  )(implicit cp: CursorProducer[Team]) =
    coll.find(enabledSelect).cursor[Team](readPreference)

  def enabled(id: Team.ID) = coll.uno[Team]($id(id) ++ enabledSelect)

  def owned(id: Team.ID, createdBy: User.ID): Fu[Option[Team]] =
    coll.uno[Team]($id(id) ++ $doc("createdBy" -> createdBy))

  def teamIdsByCreator(userId: User.ID): Fu[List[String]] =
    coll.distinct[String, List]("_id", $doc("createdBy" -> userId).some)

  def creatorOf(teamId: Team.ID): Fu[Option[User.ID]] =
    coll.primitiveOne[User.ID]($id(teamId), "_id")

  def isCreator(teamId: Team.ID, userId: User.ID): Fu[Boolean] =
    coll.exists($id(teamId) ++ $doc("createdBy" -> userId))

  def isWfd(teamId: Team.ID): Fu[Boolean] =
    enabled(teamId).dmap(t => ~t.map(_.isWfd))

  def name(id: Team.ID): Fu[Option[String]] =
    coll.primitiveOne[String]($id(id), "name")

  def mini(id: Team.ID): Fu[Option[Team.Mini]] =
    name(id) map2 { n: String => Team.Mini(id, n) }

  def userHasCreatedSince(userId: String, duration: Period): Fu[Boolean] =
    coll.exists($doc(
      "createdAt" $gt DateTime.now.minus(duration),
      "createdBy" -> userId
    ))

  def ownerOf(teamId: Team.ID): Fu[Option[String]] =
    coll.primitiveOne[String]($id(teamId), "createdBy")

  def incMembers(teamId: Team.ID, by: Int): Funit =
    coll.update($id(teamId), $inc("nbMembers" -> by)).void

  def enable(team: Team) = coll.updateField($id(team.id), "enabled", true)

  def disable(team: Team) = coll.updateField($id(team.id), "enabled", false)

  def setWfd(team: Team) = coll.updateField($id(team.id), "wfd", true)

  def addRequest(teamId: Team.ID, request: Request): Funit =
    coll.update(
      $id(teamId) ++ $doc("requests.user" $ne request.user),
      $push("requests", request.user)
    ).void

  def changeOwner(teamId: Team.ID, newOwner: User.ID) =
    coll.updateField($id(teamId), "createdBy", newOwner)

  private[team] val enabledSelect = $doc("enabled" -> true)

  private[team] val sortPopular = $sort desc "nbMembers"
}
