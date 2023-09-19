package controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.common.{ HTTPRequest, MaxPerSecond }
import lidraughts.common.paginator.PaginatorJson
import lidraughts.hub.lightTeam._
import lidraughts.security.Granter
import lidraughts.team.{ Joined, Motivate, Team => TeamModel, TeamRepo, MemberRepo }
import lidraughts.user.{ User => UserModel, UserRepo }
import views._

object Team extends LidraughtsController {

  private def forms = Env.team.forms
  private def api = Env.team.api
  private def jsonView = Env.team.jsonView
  private def paginator = Env.team.paginator

  def all(page: Int) = Open { implicit ctx =>
    paginator popularTeams page map { html.team.list.all(_) }
  }

  def home(page: Int) = Open { implicit ctx =>
    ctx.me.??(api.hasTeams) map {
      case true => Redirect(routes.Team.mine)
      case false => Redirect(routes.Team.all(page))
    }
  }

  def show(id: String, page: Int) = Open { implicit ctx =>
    OptionFuOk(api team id) { renderTeam(_, page) }
  }

  def search(text: String, page: Int) = OpenBody { implicit ctx =>
    if (text.trim.isEmpty) paginator popularTeams page map { html.team.list.all(_) }
    else Env.teamSearch(text, page) map { html.team.list.search(text, _) }
  }

  private def renderTeam(team: TeamModel, page: Int = 1)(implicit ctx: Context) =
    for {
      info <- Env.current.teamInfo(team, ctx.me)
      members <- paginator.teamMembers(team, page)
      hasChat = canHaveChat(team, info)
      chat <- hasChat ?? Env.chat.api.userChat.cached
        .findMine(lidraughts.chat.Chat.Id(team.id), ctx.me)
        .dmap(some)
      userIds = info.userIds ::: chat.??(_.chat.userIds)
      _ <- Env.user.lightUserApi preloadMany userIds
      _ <- team.isWfd ?? Env.user.lightWfdUserApi.preloadMany(userIds)
      version <- hasChat ?? Env.team.version(team.id).dmap(some)
    } yield html.team.show(team, members, info, chat, version, team.isWfd option Env.user.wfdUsername)

  private def canHaveChat(team: TeamModel, info: lidraughts.app.mashup.TeamInfo)(implicit ctx: Context): Boolean =
    team.chat && {
      (info.mine && !ctx.kid) || isGranted(_.ChatTimeout)
    }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      Env.team.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  def legacyUsers(teamId: String) = Action {
    MovedPermanently(routes.Team.users(teamId).url)
  }

  def users(teamId: String) = Action.async { req =>
    import Api.limitedDefault
    api.team(teamId) flatMap {
      _ ?? { team =>
        Api.GlobalLinearLimitPerIP(HTTPRequest lastRemoteAddress req) {
          import play.api.libs.iteratee._
          Api.jsonStream {
            Env.team.memberStream(team, MaxPerSecond(20)) &>
              Enumeratee.map(Env.api.userApi.one)
          } |> fuccess
        }
      }
    }
  }

  def tournaments(teamId: String) = Open { implicit ctx =>
    TeamRepo.enabled(teamId) flatMap {
      _ ?? { team =>
        Env.current.teamInfo.tournaments(team, 40) map { tours =>
          Ok(html.team.tournaments.page(team, tours))
        }
      }
    }
  }

  def edit(id: String) = Auth { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      fuccess(html.team.form.edit(team, forms edit team))
    }
  }

  def wfd(id: String) = Auth { implicit ctx => me =>
    WithWfdTeam(id) { team =>
      MemberRepo userIdsByTeam team.id flatMap UserRepo.byIds map { users =>
        html.team.wfd.profiles(team, users)
      }
    }
  }

  def wfdProfileForm(id: String, userId: String) = Auth { implicit ctx => me =>
    WithWfdTeam(id) { team =>
      OptionFuOk(UserRepo byId userId) { user =>
        fuccess(html.team.wfd.profileForm(team, user, Env.user.forms profileWfdOrProfileOf user))
      }
    }
  }

  def wfdProfileApply(id: String, userId: String) = AuthBody { implicit ctx => me =>
    WithWfdTeam(id) { _ =>
      implicit val req: Request[_] = ctx.body
      Env.user.forms.profileWfd.bindFromRequest.fold(
        jsonFormError,
        profile => {
          UserRepo.setProfileWfd(userId, profile) >>-
            Env.user.lightWfdUserApi.invalidate(userId) inject Ok(
              Json.obj(
                "ok" -> true,
                "fullName" -> ~profile.nonEmptyRealName
              )
            )
        }
      )
    }
  }

  def update(id: String) = AuthBody { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      implicit val req = ctx.body
      forms.edit(team).bindFromRequest.fold(
        err => BadRequest(html.team.form.edit(team, err)).fuccess,
        data => api.update(team, data, me) inject Redirect(routes.Team.show(team.id))
      )
    }
  }

  def kickForm(id: String) = Auth { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      MemberRepo userIdsByTeam team.id map { userIds =>
        html.team.admin.kick(team, userIds - me.id)
      }
    }
  }

  def kick(id: String) = AuthBody { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      implicit val req = ctx.body
      forms.selectMember.bindFromRequest.value ?? { api.kick(team, _, me) } inject Redirect(
        routes.Team.show(team.id)
      )
    }
  }
  def kickUser(teamId: String, userId: String) = Scoped(_.Team.Write) { req => me =>
    api team teamId flatMap {
      _ ?? { team =>
        if (team isCreator me.id) api.kick(team, userId, me) inject jsonOkResult
        else Forbidden(jsonError("Not your team")).fuccess
      }
    }
  }

  def changeOwnerForm(id: String) = Auth { implicit ctx => _ =>
    WithOwnedTeam(id) { team =>
      MemberRepo userIdsByTeam team.id map { userIds =>
        html.team.admin.changeOwner(team, userIds - team.createdBy)
      }
    }
  }

  def changeOwner(id: String) = AuthBody { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      implicit val req = ctx.body
      forms.selectMember.bindFromRequest.value ?? { api.changeOwner(team, _, me) } inject Redirect(
        routes.Team.show(team.id)
      )
    }
  }

  def close(id: String) = Secure(_.ManageTeam) { implicit ctx => me =>
    OptionFuResult(api team id) { team =>
      (api delete team) >>
        Env.mod.logApi.deleteTeam(me.id, team.name, team.description) inject
        Redirect(routes.Team all 1)
    }
  }

  def form = Auth { implicit ctx => me =>
    OnePerWeek(me) {
      forms.anyCaptcha map { captcha =>
        Ok(html.team.form.create(forms.create, captcha))
      }
    }
  }

  def create = AuthBody { implicit ctx => implicit me =>
    OnePerWeek(me) {
      implicit val req = ctx.body
      forms.create.bindFromRequest.fold(
        err => forms.anyCaptcha map { captcha =>
          BadRequest(html.team.form.create(err, captcha))
        },
        data => api.create(data, me) ?? {
          _ map { team => Redirect(routes.Team.show(team.id)): Result }
        }
      )
    }
  }

  def mine = Auth { implicit ctx => me =>
    api mine me map { html.team.list.mine(_) }
  }

  def join(id: String) = AuthOrScopedBody(_.Team.Write)(
    auth = implicit ctx => me => negotiate(
      html = api.join(id, me, none) flatMap {
        case Some(Joined(team)) => Redirect(routes.Team.show(team.id)).fuccess
        case Some(Motivate(team)) => Redirect(routes.Team.requestForm(team.id)).fuccess
        case _ => notFound(ctx)
      },
      api = _ => {
        implicit val body = ctx.body
        forms.apiRequest.bindFromRequest
          .fold(
            newJsonFormError,
            msg =>
              api.join(id, me, msg) flatMap {
                case Some(Joined(_)) => jsonOkResult.fuccess
                case Some(Motivate(_)) =>
                  BadRequest(
                    jsonError("This team requires confirmation.")
                  ).fuccess
                case _ => notFoundJson("Team not found")
              }
          )
      }
    ),
    scoped = implicit req => me => {
      implicit val lang = reqLang
      forms.apiRequest.bindFromRequest
        .fold(
          newJsonFormError,
          msg =>
            Env.oAuth.server.fetchAppAuthor(req) flatMap {
              api.joinApi(id, me, _, msg)
            } flatMap {
              case Some(Joined(_)) => jsonOkResult.fuccess
              case Some(Motivate(_)) =>
                Forbidden(
                  jsonError("This team requires confirmation, and is not owned by the oAuth app owner.")
                ).fuccess
              case _ => notFoundJson("Team not found")
            }
        )
    }
  )

  def subscribe(teamId: String) = {
    def doSub(req: Request[_], me: UserModel) =
      Form(single("v" -> boolean))
        .bindFromRequest()(req)
        .fold(_ => funit, v => api.subscribe(teamId, me.id, v))
    AuthOrScopedBody(_.Team.Write)(
      auth = ctx => me => doSub(ctx.body, me) inject Redirect(routes.Team.show(teamId)),
      scoped = req => me => doSub(req, me) inject jsonOkResult
    )
  }

  def requests = Auth { implicit ctx => me =>
    Env.team.cached.nbRequests invalidate me.id
    api requestsWithUsers me map { html.team.request.all(_) }
  }

  def requestForm(id: String) = Auth { implicit ctx => me =>
    OptionFuOk(api.requestable(id, me)) { team =>
      fuccess(html.team.request.requestForm(team, forms.request))
    }
  }

  def requestCreate(id: String) = AuthBody { implicit ctx => me =>
    OptionFuResult(api.requestable(id, me)) { team =>
      implicit val req = ctx.body
      forms.request.bindFromRequest.fold(
        err => BadRequest(html.team.request.requestForm(team, err)).fuccess,
        setup => api.createRequest(team, me, setup.message) inject Redirect(routes.Team.show(team.id))
      )
    }
  }

  def requestProcess(requestId: String) = AuthBody { implicit ctx => me =>
    OptionFuRedirectUrl(for {
      requestOption ← api request requestId
      teamOption ← requestOption.??(req => TeamRepo.owned(req.team, me.id))
    } yield (teamOption |@| requestOption).tupled) {
      case (team, request) =>
        implicit val req = ctx.body
        forms.processRequest.bindFromRequest.fold(
          _ => fuccess(routes.Team.show(team.id).toString), {
            case (decision, url) =>
              api.processRequest(team, request, (decision === "accept")) inject url
          }
        )
    }
  }

  def quit(id: String) = AuthOrScoped(_.Team.Write)(
    auth = implicit ctx => me =>
      OptionFuResult(api.cancelRequest(id, me) orElse api.quit(id, me)) { team =>
        negotiate(
          html = Redirect(routes.Team.show(team.id)).fuccess,
          api = _ => jsonOkResult.fuccess
        )
      }(ctx),
    scoped = req => me => api.quit(id, me) flatMap {
      _.fold(notFoundJson())(_ => jsonOkResult.fuccess)
    }
  )

  def autocomplete = Action.async { req =>
    get("term", req).filter(_.nonEmpty) match {
      case None => BadRequest("No search term provided").fuccess
      case Some(term) => for {
        teams <- api.autocomplete(term, 10)
        _ <- Env.user.lightUserApi preloadMany teams.map(_.createdBy)
      } yield Ok {
        JsArray(teams map { team =>
          Json.obj(
            "id" -> team.id,
            "name" -> team.name,
            "owner" -> Env.user.lightUserApi.sync(team.createdBy).fold(team.createdBy)(_.name),
            "members" -> team.nbMembers
          )
        })
      } as JSON
    }
  }

  def pmAll(id: String) = Auth { implicit ctx => _ =>
    WithOwnedTeam(id) { team =>
      lidraughts.tournament.TournamentRepo.byTeamUpcoming(team.id, 3, withTeamBattle = !team.isWfd) map { tours =>
        Ok(html.team.admin.pmAll(team, forms.pmAll, tours))
      }
    }
  }

  def pmAllSubmit(id: String) = AuthBody { implicit ctx => me =>
    WithOwnedTeam(id) { team =>
      implicit val req = ctx.body
      forms.pmAll.bindFromRequest.fold(
        err =>
          lidraughts.tournament.TournamentRepo.byTeamUpcoming(team.id, 3, withTeamBattle = !team.isWfd) map { tours =>
            BadRequest(html.team.admin.pmAll(team, err, tours))
          },
        msg =>
          PmAllLimitPerUser(me.id) {
            val url = s"${lidraughts.api.Env.current.Net.BaseUrl}${routes.Team.show(team.id)}"
            val full = s"""$msg
---
You received this because you are subscribed to messages of the team $url."""

            MemberRepo.subscribedUserIds(team.id) flatMap {
              Env.message.api.multiPost(me, _, team.name, full)
            } inject Redirect(routes.Team.show(team.id))
          }
      )
    }
  }

  // API

  def apiAll(page: Int) = Action.async {
    JsonFuOk {
      paginator popularTeams page flatMap { pag =>
        Env.user.lightUserApi.preloadMany(pag.currentPageResults.map(_.createdBy)) inject {
          PaginatorJson(pag mapResults jsonView.teamWrites.writes)
        }
      }
    }
  }

  def apiShow(id: String) =
    Open { ctx =>
      JsonOptionOk {
        api team id flatMap {
          _ ?? { team =>
            for {
              joined <- ctx.userId.?? { api.belongsTo(id, _) }
              requested <- ctx.userId.ifFalse(joined).?? { lidraughts.team.RequestRepo.exists(id, _) }
            } yield {
              jsonView.teamWrites.writes(team) ++ Json
                .obj(
                  "joined" -> joined,
                  "requested" -> requested
                )
            }.some
          }
        }
      }
    }

  def apiSearch(text: String, page: Int) = Action.async {
    JsonFuOk {
      val paginatorFu =
        if (text.trim.isEmpty) paginator popularTeams page
        else Env.teamSearch(text, page)
      paginatorFu map { pag =>
        PaginatorJson(pag mapResults jsonView.teamWrites.writes)
      }
    }
  }

  def apiTeamsOf(username: String) = Action.async {
    JsonFuOk {
      api teamsOf username flatMap { teams =>
        Env.user.lightUserApi.preloadMany(teams.map(_.createdBy)) inject teams.map {
          jsonView.teamWrites.writes
        }
      }
    }
  }

  private val PmAllLimitPerUser = new lidraughts.memo.RateLimit[lidraughts.user.User.ID](
    credits = 6,
    duration = 24 hours,
    name = "team pm all per user",
    key = "team.pmAll"
  )

  private def OnePerWeek[A <: Result](me: UserModel)(a: => Fu[A])(implicit ctx: Context): Fu[Result] =
    api.hasCreatedRecently(me) flatMap { did =>
      if (did && !Granter(_.ManageTeam)(me)) Forbidden(views.html.site.message.teamCreateLimit).fuccess
      else a
    }

  private def WithOwnedTeam(teamId: String)(f: TeamModel => Fu[Result])(implicit ctx: Context): Fu[Result] =
    OptionFuResult(api team teamId) { team =>
      if (ctx.userId.exists(team.isCreator) || isGranted(_.ManageTeam)) f(team)
      else renderTeam(team) map { Forbidden(_) }
    }

  private def WithWfdTeam(teamId: String)(f: TeamModel => Fu[Result])(implicit ctx: Context): Fu[Result] =
    OptionFuResult(api team teamId) { team =>
      if (team.isWfd && (ctx.userId.exists(team.isCreator) || isGranted(_.ManageWfd))) f(team)
      else renderTeam(team) map {
        Forbidden(_)
      }
    }

  private[controllers] def teamsIBelongTo(me: UserModel): Fu[List[LightTeam]] =
    api mine me map { _.filter(t => !t.isWfd || t.isCreator(me.id)).map(_.light) }
}
