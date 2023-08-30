package lidraughts.team

import actorApi._
import akka.actor.ActorSelection
import lidraughts.db.dsl._
import lidraughts.hub.actorApi.team.{ CreateTeam, JoinTeam, KickFromTeam }
import lidraughts.hub.actorApi.timeline.{ Propagate, TeamJoin, TeamCreate }
import lidraughts.hub.lightTeam.LightTeam
import lidraughts.mod.ModlogApi
import lidraughts.user.{ User, UserRepo, UserContext }
import org.joda.time.Period
import reactivemongo.api.{ Cursor, ReadPreference }

final class TeamApi(
    coll: Colls,
    cached: Cached,
    notifier: Notifier,
    bus: lidraughts.common.Bus,
    indexer: ActorSelection,
    timeline: ActorSelection,
    modLog: ModlogApi
) {

  import BSONHandlers._

  val creationPeriod = Period weeks 1

  def team(id: Team.ID) = coll.team.byId[Team](id)

  def light(id: Team.ID) = coll.team.byId[LightTeam](id, $doc("name" -> true, "wfd" -> true))

  def request(id: Team.ID) = coll.request.byId[Request](id)

  def create(setup: TeamSetup, me: User): Option[Fu[Team]] = me.canTeam option {
    val s = setup.trim
    val team = Team.make(
      name = s.name,
      location = s.location,
      description = s.description,
      open = s.isOpen,
      createdBy = me
    )
    coll.team.insert(team) >>
      MemberRepo.add(team.id, me.id) >>- {
        cached invalidateTeamIds me.id
        indexer ! InsertTeam(team)
        timeline ! Propagate(
          TeamCreate(me.id, team.id)
        ).toFollowersOf(me.id)
        bus.publish(CreateTeam(id = team.id, name = team.name, userId = me.id), 'team)
      } inject team
  }

  def update(team: Team, edit: TeamEdit, me: User): Funit = edit.trim |> { e =>
    team.copy(
      location = e.location,
      description = e.description,
      open = e.isOpen,
      chat = e.chat
    ) |> { team =>
      coll.team.update($id(team.id), team).void >>
        !team.isCreator(me.id) ?? {
          modLog.teamEdit(me.id, team.createdBy, team.name)
        } >>-
        (indexer ! InsertTeam(team))
    }
  }

  def mine(me: User): Fu[List[Team]] =
    cached teamIds me.id flatMap { ids => coll.team.byIds[Team](ids.toArray) }

  def isSubscribed = MemberRepo.isSubscribed _

  def subscribe = MemberRepo.subscribe _

  def hasTeams(me: User): Fu[Boolean] = cached.teamIds(me.id).map(_.value.nonEmpty)

  def hasCreatedRecently(me: User): Fu[Boolean] =
    TeamRepo.userHasCreatedSince(me.id, creationPeriod)

  def requestsWithUsers(team: Team): Fu[List[RequestWithUser]] = for {
    requests ← RequestRepo findByTeam team.id
    users ← UserRepo usersFromSecondary requests.map(_.user)
  } yield requests zip users map {
    case (request, user) => RequestWithUser(request, user)
  }

  def requestsWithUsers(user: User): Fu[List[RequestWithUser]] = for {
    teamIds ← TeamRepo teamIdsByCreator user.id
    requests ← RequestRepo findByTeams teamIds
    users ← UserRepo usersFromSecondary requests.map(_.user)
  } yield requests zip users map {
    case (request, user) => RequestWithUser(request, user)
  }

  def join(teamId: Team.ID, me: User, msg: Option[String]): Fu[Option[Requesting]] =
    coll.team.byId[Team](teamId) flatMap {
      _ ?? { team =>
        if (team.open) doJoin(team, me) inject Joined(team).some
        else
          msg.fold(fuccess[Option[Requesting]](Motivate(team).some)) { txt =>
            createRequest(team, me, txt) inject Joined(team).some
          }
      }
    }

  def joinApi(
    teamId: Team.ID,
    me: User,
    oAuthAppOwner: Option[User.ID],
    msg: Option[String]
  ): Fu[Option[Requesting]] =
    coll.team.byId[Team](teamId) flatMap {
      _ ?? { team =>
        if (team.open || oAuthAppOwner.contains(team.createdBy)) doJoin(team, me) inject Joined(team).some
        else
          msg.fold(fuccess[Option[Requesting]](Motivate(team).some)) { txt =>
            createRequest(team, me, txt) inject Joined(team).some
          }
      }
    }

  def requestable(teamId: Team.ID, user: User): Fu[Option[Team]] = for {
    teamOption ← coll.team.byId[Team](teamId)
    able ← teamOption.??(requestable(_, user))
  } yield teamOption filter (_ => able)

  def requestable(team: Team, user: User): Fu[Boolean] = for {
    belongs <- belongsTo(team.id, user.id)
    requested <- RequestRepo.exists(team.id, user.id)
  } yield !belongs && !requested

  def createRequest(team: Team, user: User, msg: String): Funit =
    requestable(team, user) flatMap {
      _ ?? {
        val request = Request.make(team = team.id, user = user.id, message = msg)
        coll.request.insert(request).void >>- (cached.nbRequests invalidate team.createdBy)
      }
    }

  def cancelRequest(teamId: Team.ID, user: User): Fu[Option[Team]] =
    coll.team.byId[Team](teamId) flatMap {
      _ ?? { team =>
        RequestRepo.cancel(team.id, user) map (_ option team)
      }
    }

  def processRequest(team: Team, request: Request, accept: Boolean): Funit = for {
    _ ← coll.request.remove(request)
    _ = cached.nbRequests invalidate team.createdBy
    userOption ← UserRepo byId request.user
    _ ← userOption.filter(_ => accept).??(user =>
      doJoin(team, user) >>- notifier.acceptRequest(team, request))
  } yield ()

  def deleteRequestsByUserId(userId: lidraughts.user.User.ID) =
    RequestRepo.getByUserId(userId) flatMap {
      _.map { request =>
        RequestRepo.remove(request.id) >>
          TeamRepo.creatorOf(request.team).map { _ ?? cached.nbRequests.invalidate }
      }.sequenceFu
    }

  def doJoin(team: Team, user: User): Funit = !belongsTo(team.id, user.id) flatMap {
    _ ?? {
      MemberRepo.add(team.id, user.id) >>
        TeamRepo.incMembers(team.id, +1) >>- {
          cached invalidateTeamIds user.id
          timeline ! Propagate(TeamJoin(user.id, team.id)).toFollowersOf(user.id)
          bus.publish(JoinTeam(id = team.id, userId = user.id), 'team)
        }
    } recover lidraughts.db.ignoreDuplicateKey
  }

  def quit(teamId: Team.ID, me: User): Fu[Option[Team]] =
    coll.team.byId[Team](teamId) flatMap {
      _ ?? { team =>
        doQuit(team, me.id) inject team.some
      }
    }

  def teamsOf(username: String) =
    cached.teamIdsList(User normalize username) flatMap {
      coll.team.byIds[Team](_, ReadPreference.secondaryPreferred)
    }

  private def doQuit(team: Team, userId: User.ID): Funit = belongsTo(team.id, userId) flatMap {
    _ ?? {
      MemberRepo.remove(team.id, userId) map { res =>
        if (res.n == 1) TeamRepo.incMembers(team.id, -1)
        cached.invalidateTeamIds(userId)
      }
    }
  }

  def quitAll(userId: User.ID): Funit = MemberRepo.removeByUser(userId)

  def kick(team: Team, userId: User.ID, me: User): Funit =
    doQuit(team, userId) >>
      (!team.isCreator(me.id)).?? {
        modLog.teamKick(me.id, userId, team.name)
      } >>-
      bus.publish(KickFromTeam(teamId = team.id, userId = userId), 'teamKick)

  def changeOwner(team: Team, userId: User.ID, me: User): Funit =
    MemberRepo.exists(team.id, userId) flatMap { e =>
      e ?? {
        TeamRepo.changeOwner(team.id, userId) >>
          modLog.teamMadeOwner(me.id, userId, team.name) >>-
          notifier.madeOwner(team, userId)
      }
    }

  def enable(team: Team): Funit =
    TeamRepo.enable(team).void >>- (indexer ! InsertTeam(team))

  def disable(team: Team): Funit =
    TeamRepo.disable(team).void >>- (indexer ! RemoveTeam(team.id))

  def setWfd(team: Team): Funit =
    TeamRepo.setWfd(team).void >>- cached.wfdCache.invalidate(team.id)

  // delete for ever, with members but not forums
  def delete(team: Team): Funit =
    coll.team.remove($id(team.id)) >>
      MemberRepo.removeByteam(team.id) >>-
      (indexer ! RemoveTeam(team.id))

  def syncBelongsTo(teamId: Team.ID, userId: User.ID): Boolean =
    cached.syncTeamIds(userId) contains teamId

  def belongsTo(teamId: Team.ID, userId: User.ID): Fu[Boolean] =
    cached.teamIds(userId) dmap (_ contains teamId)

  def owns(teamId: Team.ID, userId: User.ID): Fu[Boolean] =
    TeamRepo ownerOf teamId dmap (_ has userId)

  def teamName(teamId: Team.ID): Option[String] = cached.name(teamId)

  def filterExistingIds(ids: Set[String]): Fu[Set[Team.ID]] =
    coll.team.distinct[Team.ID, Set]("_id", Some("_id" $in ids))

  def autocomplete(term: String, max: Int): Fu[List[Team]] =
    coll.team.find($doc(
      "name".$startsWith(java.util.regex.Pattern.quote(term), "i"),
      "enabled" -> true
    )).sort($sort desc "nbMembers").list[Team](max, ReadPreference.secondaryPreferred)

  def nbRequests(teamId: Team.ID) = cached.nbRequests get teamId

  def recomputeNbMembers: Funit =
    coll.team
      .find($empty, $id(true))
      .cursor[Bdoc](ReadPreference.secondaryPreferred)
      .foldWhileM({}) { (_, doc) =>
        (doc.getAs[String]("_id") ?? recomputeNbMembers) inject Cursor.Cont({})
      }

  private[team] def recomputeNbMembers(teamId: Team.ID): Funit =
    MemberRepo.countByTeam(teamId) flatMap { nb =>
      coll.team.updateField($id(teamId), "nbMembers", nb).void
    }
}
