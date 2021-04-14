package controllers

import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.swiss.Swiss.{ Id => SwissId }
import lidraughts.swiss.{ Swiss => SwissModel }
import views._

object Swiss extends LidraughtsController {

  private def env = Env.swiss

  private def swissNotFound(implicit ctx: Context) = NotFound(html.swiss.bits.notFound())

  def home =
    Secure(_.Beta) { implicit ctx => _ =>
      env.api.featurable map {
        case (now, soon) => Ok(html.swiss.home(now, soon))
      }
    }

  def show(id: String) = Secure(_.Beta) { implicit ctx => _ =>
    env.api.byId(SwissId(id)) flatMap { swissOption =>
      val page = getInt("page").filter(0.<)
      negotiate(
        html = swissOption.fold(swissNotFound.fuccess) { swiss =>
          for {
            version <- env.version(swiss.id)
            isInTeam <- isCtxInTheTeam(swiss.teamId)
            json <- env.json(
              swiss = swiss,
              me = ctx.me,
              pref = ctx.pref,
              reqPage = page,
              socketVersion = version.some,
              playerInfo = none,
              isInTeam = isInTeam
            )
            canChat <- canHaveChat(swiss)
            chat <- canChat ?? Env.chat.api.userChat.cached
              .findMine(lidraughts.chat.Chat.Id(swiss.id.value), ctx.me)
              .dmap(some)
            _ <- chat ?? { c =>
              Env.user.lightUserApi.preloadMany(c.chat.userIds)
            }
          } yield Ok(html.swiss.show(swiss, json, chat))
        },
        api = _ =>
          swissOption.fold(notFoundJson("No such swiss tournament")) { swiss =>
            for {
              socketVersion <- getBool("socketVersion").??(env version swiss.id dmap some)
              isInTeam <- isCtxInTheTeam(swiss.teamId)
              playerInfo <- get("playerInfo").?? { env.api.playerInfo(swiss, _) }
              json <- env.json(
                swiss = swiss,
                me = ctx.me,
                pref = ctx.pref,
                reqPage = page,
                socketVersion = socketVersion,
                playerInfo = playerInfo,
                isInTeam = isInTeam
              )
            } yield Ok(json)
          }
      )
    }
  }

  private def isCtxInTheTeam(teamId: lidraughts.team.Team.ID)(implicit ctx: Context) =
    ctx.userId.??(u => Env.team.cached.teamIds(u).dmap(_ contains teamId))

  def form(teamId: String) = Secure(_.Beta) { implicit ctx => me =>
    Ok(html.swiss.form.create(env.forms.create, teamId)).fuccess
  }

  def create(teamId: String) = SecureBody(_.Beta) { implicit ctx => me =>
    lidraughts.team.TeamRepo.isCreator(teamId, me.id) flatMap {
      case false => notFound
      case _ =>
        env.forms.create
          .bindFromRequest()(ctx.body)
          .fold(
            err => BadRequest(html.swiss.form.create(err, teamId)).fuccess,
            data =>
              Tournament.rateLimitCreation(me, false, ctx.req) {
                env.api.create(data, me, teamId) map { swiss =>
                  Redirect(routes.Swiss.show(swiss.id.value))
                }
              }
          )
    }
  }

  def apiCreate(teamId: String) =
    ScopedBody() { implicit req => me =>
      if (me.isBot || me.lame || !isGranted(_.Beta, me)) notFoundJson("This account cannot create tournaments")
      else
        lidraughts.team.TeamRepo.isCreator(teamId, me.id) flatMap {
          case false => notFoundJson("You're not a leader of that team")
          case _ =>
            env.forms.create.bindFromRequest
              .fold(
                jsonFormErrorDefaultLang,
                data =>
                  Tournament.rateLimitCreation(me, false, req) {
                    JsonOk {
                      env.api.create(data, me, teamId) map env.json.api
                    }
                  }
              )
        }
    }

  def join(id: String) = SecureBody(_.Beta) { implicit ctx => me =>
    NoLameOrBot {
      Env.team.cached.teamIds(me.id) flatMap { teamIds =>
        env.api.join(SwissId(id), me, teamIds.contains) flatMap { result =>
          negotiate(
            html = Redirect(routes.Swiss.show(id)).fuccess,
            api = _ =>
              fuccess {
                if (result) jsonOkResult
                else BadRequest(Json.obj("joined" -> false))
              }
          )
        }
      }
    }
  }

  def withdraw(id: String) =
    Auth { implicit ctx => me =>
      env.api.withdraw(SwissId(id), me.id) flatMap { result =>
        negotiate(
          html = Redirect(routes.Swiss.show(id)).fuccess,
          api = _ => fuccess(jsonOkResult)
        )
      }
    }

  def edit(id: String) = Secure(_.Beta) { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      Ok(html.swiss.form.edit(swiss, env.forms.edit(swiss))).fuccess
    }
  }

  def update(id: String) = AuthBody { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      implicit val req = ctx.body
      env.forms.edit(swiss)
        .bindFromRequest
        .fold(
          err => BadRequest(html.swiss.form.edit(swiss, err)).fuccess,
          data => env.api.update(swiss, data) inject Redirect(routes.Swiss.show(id))
        )
    }
  }

  def scheduleNextRound(id: String) =
    AuthBody { implicit ctx => me =>
      WithEditableSwiss(id, me) { swiss =>
        implicit val req = ctx.body
        env.forms.nextRound.bindFromRequest
          .fold(
            err => Redirect(routes.Swiss.show(id)).fuccess,
            date => env.api.scheduleNextRound(swiss, date) inject Redirect(routes.Swiss.show(id))
          )
      }
    }

  def terminate(id: String) = Auth { implicit ctx => me =>
    WithEditableSwiss(id, me) { swiss =>
      env.api kill swiss inject Redirect(routes.Team.show(swiss.teamId))
    }
  }

  def standing(id: String, page: Int) = Open { implicit ctx =>
    WithSwiss(id) { swiss =>
      JsonOk {
        env.standingApi(swiss, page)
      }
    }
  }

  def pageOf(id: String, userId: String) =
    Open { implicit ctx =>
      WithSwiss(id) { swiss =>
        env.api.pageOf(swiss, lidraughts.user.User normalize userId) flatMap {
          _ ?? { page =>
            JsonOk {
              env.standingApi(swiss, page)
            }
          }
        }
      }
    }

  def player(id: String, userId: String) = Action.async {
    WithSwiss(id) { swiss =>
      env.api.playerInfo(swiss, userId) flatMap {
        _.fold(notFoundJson()) { player =>
          JsonOk(fuccess(lidraughts.swiss.SwissJson.playerJsonExt(swiss, player)))
        }
      }
    }
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  private val ExportLimitPerIP = new lidraughts.memo.RateLimit[lidraughts.common.IpAddress](
    credits = 10,
    duration = 1.minute,
    name = "swiss export per IP",
    key = "swiss.export.ip"
  )

  def exportTrf(id: String) =
    Action.async {
      env.api.byId(SwissId(id)) flatMap {
        case None => NotFound("Tournament not found").fuccess
        case Some(swiss) => env.trf(swiss, sorted = true) map { lines =>
          Ok.chunked(play.api.libs.iteratee.Enumerator(lines mkString "\n"))
            .withHeaders(CONTENT_DISPOSITION -> s"attachment; filename=lidraughts_swiss_$id.trf")
        }
      }
    }

  private def WithSwiss(id: String)(f: SwissModel => Fu[Result]): Fu[Result] =
    env.api.byId(SwissId(id)) flatMap { _ ?? f }

  private def WithEditableSwiss(id: String, me: lidraughts.user.User)(
    f: SwissModel => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    WithSwiss(id) { swiss =>
      if (swiss.createdBy == me.id && !swiss.isFinished) f(swiss)
      else if (isGranted(_.ManageTournament)) f(swiss)
      else Redirect(routes.Swiss.show(swiss.id.value)).fuccess
    }

  private def canHaveChat(swiss: SwissModel)(implicit ctx: Context): Fu[Boolean] =
    (swiss.settings.hasChat && ctx.noKid) ?? ctx.userId.?? { userId =>
      if (isGranted(_.ChatTimeout)) fuTrue
      else Env.team.api.belongsTo(swiss.teamId, userId)
    }
}
