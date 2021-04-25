package controllers

import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.chat.Chat
import lidraughts.common.HTTPRequest
import lidraughts.hub.lightTeam._
import lidraughts.tournament.{ System, TournamentRepo, PairingRepo, VisibleTournaments, Tournament => Tour }
import lidraughts.user.{ User => UserModel }
import views._

object Tournament extends LidraughtsController {

  private def env = Env.tournament
  private def repo = TournamentRepo

  import Team.teamsIBelongTo

  private def tournamentNotFound(implicit ctx: Context) = NotFound(html.tournament.bits.notFound())

  private[controllers] val upcomingCache = Env.memo.asyncCache.single[(VisibleTournaments, List[Tour])](
    name = "tournament.home",
    for {
      visible <- env.api.fetchVisibleTournaments
      scheduled <- repo.scheduledDedup
    } yield (visible, scheduled),
    expireAfter = _.ExpireAfterWrite(3 seconds)
  )

  def home(page: Int) = Open { implicit ctx =>
    negotiate(
      html = Reasonable(page, 20) {
        val finishedPaginator = repo.finishedPaginator(lidraughts.common.MaxPerPage(15), page = page)
        if (HTTPRequest isXhr ctx.req) for {
          pag <- finishedPaginator
          _ <- Env.user.lightUserApi preloadMany pag.currentPageResults.flatMap(_.winnerId)
        } yield Ok(html.tournament.finishedPaginator(pag))
        else for {
          (visible, scheduled) <- upcomingCache.get
          finished <- finishedPaginator
          winners <- env.winners.all
          _ <- Env.user.lightUserApi preloadMany {
            finished.currentPageResults.flatMap(_.winnerId).toList :::
              scheduled.flatMap(_.winnerId) ::: winners.userIds
          }
          scheduleJson <- env apiJsonView visible
        } yield NoCache {
          Ok(html.tournament.home(scheduled, finished, winners, scheduleJson))
        }
      },
      api = _ => for {
        (visible, _) <- upcomingCache.get
        scheduleJson <- env apiJsonView visible
      } yield Ok(scheduleJson)
    )
  }

  def help(sysStr: Option[String]) = Open { implicit ctx =>
    val system = sysStr flatMap {
      case "arena" => System.Arena.some
      case _ => none
    }
    Ok(html.tournament.faq.page(system)).fuccess
  }

  def leaderboard = Open { implicit ctx =>
    for {
      winners <- env.winners.all
      _ <- Env.user.lightUserApi preloadMany winners.userIds
    } yield Ok(html.tournament.leaderboard(winners))
  }

  private[controllers] def canHaveChat(tour: Tour, json: Option[JsObject])(implicit ctx: Context): Boolean =
    !ctx.kid && // no public chats for kids
      ctx.me.fold(!tour.isHidden) { u => // anon can see public chats, except for private tournaments
        (!tour.isHidden || json.fold(true)(jsonHasMe) || ctx.userId.has(tour.createdBy) || isGranted(
          _.ChatTimeout
        )) && // private tournament that I joined or has ChatTimeout
          Env.chat.panic.allowed(u, tighter = false)
      }

  private def jsonHasMe(js: JsObject): Boolean = (js \ "me").toOption.isDefined

  def show(id: String) = Open { implicit ctx =>
    val page = getInt("page")
    repo byId id flatMap { tourOption =>
      negotiate(
        html = tourOption.fold(tournamentNotFound.fuccess) { tour =>
          (for {
            verdicts <- env.api.verdicts(tour, ctx.me, getUserTeamIds)
            version <- env.version(tour.id)
            json <- env.jsonView(
              tour = tour,
              page = page,
              me = ctx.me,
              getUserTeamIds = getUserTeamIds,
              getTeamName = Env.team.cached.name,
              playerInfoExt = none,
              socketVersion = version.some,
              partial = false,
              lang = ctx.lang,
              pref = ctx.pref.some
            )
            chat <- canHaveChat(tour, json.some) ?? Env.chat.api.userChat.cached
              .findMine(Chat.Id(tour.id), ctx.me)
              .dmap(some)
            _ <- chat ?? { c => Env.user.lightUserApi.preloadMany(c.chat.userIds) }
            _ <- tour.teamBattle ?? { b => Env.team.cached.preloadSet(b.teams) }
            streamers <- streamerCache get tour.id
            shieldOwner <- env.shieldApi currentOwner tour
          } yield Ok(html.tournament.show(tour, verdicts, json, chat, streamers, shieldOwner))).mon(_.http.response.tournament.show.website)
        }, api = _ => tourOption.fold(notFoundJson("No such tournament")) { tour =>
          get("playerInfo").?? { env.api.playerInfo(tour, _) } zip
            getBool("socketVersion").??(env version tour.id map some) flatMap {
              case (playerInfoExt, socketVersion) =>
                val partial = getBool("partial")
                lidraughts.mon.tournament.apiShowPartial(partial)()
                env.jsonView(
                  tour = tour,
                  page = page,
                  me = ctx.me,
                  getUserTeamIds = getUserTeamIds,
                  getTeamName = Env.team.cached.name,
                  playerInfoExt = playerInfoExt,
                  socketVersion = socketVersion,
                  partial = partial,
                  lang = ctx.lang,
                  pref = ctx.pref.some
                )
            } map { Ok(_) }
        }.mon(_.http.response.tournament.show.mobile)
      ) map NoCache
    }
  }

  def standing(id: String, page: Int) = Open { implicit ctx =>
    OptionFuResult(repo byId id) { tour =>
      JsonOk {
        env.jsonView.standing(tour, page)
      }
    }
  }

  def pageOf(id: String, userId: String) = Open { implicit ctx =>
    OptionFuResult(repo byId id) { tour =>
      env.api.pageOf(tour, UserModel normalize userId) flatMap {
        _ ?? { page =>
          JsonOk {
            env.jsonView.standing(tour, page)
          }
        }
      }
    }
  }

  def player(tourId: String, userId: String) = Action.async {
    repo byId tourId flatMap {
      _ ?? { tour =>
        JsonOk {
          env.api.playerInfo(tour, userId) flatMap {
            _ ?? { env.jsonView.playerInfoExtended(tour, _) }
          }
        }
      }
    }
  }

  def teamInfo(tourId: String, teamId: TeamId) = Open { implicit ctx =>
    repo byId tourId flatMap {
      _ ?? { tour =>
        lidraughts.team.TeamRepo mini teamId flatMap {
          _ ?? { team =>
            if (HTTPRequest isXhr ctx.req)
              env.jsonView.teamInfo(tour, teamId) map { _ ?? JsonOk }
            else
              env.api.teamBattleTeamInfo(tour, teamId) map {
                _ ?? { info =>
                  Ok(views.html.tournament.teamBattle.teamInfo(tour, team, info))
                }
              }
          }
        }
      }
    }
  }

  def join(id: String) = AuthBody(BodyParsers.parse.json) { implicit ctx => implicit me =>
    NoLameOrBot {
      NoPlayban {
        val password = ctx.body.body.\("p").asOpt[String]
        val teamId = ctx.body.body.\("team").asOpt[String]
        env.api.joinWithResult(id, me, password, teamId, getUserTeamIds) flatMap { result =>
          negotiate(
            html = Redirect(routes.Tournament.show(id)).fuccess,
            api = _ => fuccess {
              if (result) jsonOkResult
              else BadRequest(Json.obj("joined" -> false))
            }
          )
        }
      }
    }
  }

  def pause(id: String) = Auth { implicit ctx => me =>
    OptionResult(repo byId id) { tour =>
      env.api.selfPause(tour.id, me.id)
      if (HTTPRequest.isXhr(ctx.req)) jsonOkResult
      else Redirect(routes.Tournament.show(tour.id))
    }
  }

  def form = Auth { implicit ctx => me =>
    NoLameOrBot {
      teamsIBelongTo(me) map { teams =>
        Ok(html.tournament.form.create(env.forms.create(me), env.forms, me, teams))
      }
    }
  }

  def teamBattleForm(teamId: TeamId) = Auth { implicit ctx => me =>
    NoLameOrBot {
      Env.team.api.owns(teamId, me.id) map {
        _ ?? {
          Ok(html.tournament.form.create(env.forms.create(me, teamId.some), env.forms, me, Nil))
        }
      }
    }
  }

  private val CreateLimitPerUser = new lidraughts.memo.RateLimit[lidraughts.user.User.ID](
    credits = 24,
    duration = 24 hour,
    name = "tournament per user",
    key = "tournament.user"
  )

  private val CreateLimitPerIP = new lidraughts.memo.RateLimit[lidraughts.common.IpAddress](
    credits = 48,
    duration = 24 hour,
    name = "tournament per IP",
    key = "tournament.ip"
  )

  private val rateLimited = ornicar.scalalib.Zero.instance[Fu[Result]] {
    fuccess(Redirect(routes.Tournament.home(1)))
  }

  private[controllers] def rateLimitCreation(me: UserModel, isPrivate: Boolean, req: RequestHeader)(
    create: => Fu[Result]
  ): Fu[Result] = {
    val cost = if (me.hasTitle ||
      Env.streamer.liveStreamApi.isStreaming(me.id) ||
      isGranted(_.ManageTournament, me) ||
      me.isVerified ||
      isPrivate) 1 else 2
    CreateLimitPerUser(me.id, cost = cost) {
      CreateLimitPerIP(HTTPRequest lastRemoteAddress req, cost = cost) {
        create
      }(rateLimited)
    }(rateLimited)
  }

  def create = AuthBody { implicit ctx => me =>
    NoLameOrBot {
      teamsIBelongTo(me) flatMap { teams =>
        implicit val req = ctx.body
        negotiate(
          html = env.forms.create(me).bindFromRequest.fold(
            err => BadRequest(html.tournament.form.create(err, env.forms, me, teams)).fuccess,
            setup => {
              rateLimitCreation(me, setup.password.isDefined, ctx.req) {
                env.api.createTournament(setup, me, teams, getUserTeamIds) map { tour =>
                  Redirect {
                    if (tour.isTeamBattle) routes.Tournament.teamBattleEdit(tour.id)
                    else routes.Tournament.show(tour.id)
                  }
                }
              }
            }
          ),
          api = _ => doApiCreate(me, teams)
        )
      }
    }
  }

  def apiCreate = ScopedBody(_.Tournament.Write) { implicit req => me =>
    if (me.isBot || me.lame) notFoundJson("This account cannot create tournaments")
    else teamsIBelongTo(me) flatMap { teams => doApiCreate(me, teams) }
  }

  private def doApiCreate(me: UserModel, teams: List[lidraughts.hub.lightTeam.LightTeam])(implicit req: Request[_]): Fu[Result] =
    env.forms.create(me).bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      setup => rateLimitCreation(me, setup.password.isDefined, req) {
        env.api.createTournament(setup, me, teams, getUserTeamIds, andJoin = false) flatMap { tour =>
          val lang = me.lang flatMap lidraughts.i18n.I18nLangPicker.byStr getOrElse lidraughts.i18n.defaultLang
          env.jsonView(tour, none, none, getUserTeamIds, Env.team.cached.name, none, none, partial = false, lang, none)
        } map { Ok(_) }
      }
    )

  def teamBattleEdit(id: String) = Auth { implicit ctx => me =>
    repo byId id flatMap {
      _ ?? {
        case tour if (tour.createdBy == me.id && !tour.isFinished) || isGranted(_.ManageTournament) =>
          tour.teamBattle ?? { battle =>
            lidraughts.team.TeamRepo.byOrderedIds(battle.sortedTeamIds) flatMap { teams =>
              Env.user.lightUserApi.preloadMany(teams.map(_.createdBy)) >> {
                val form = lidraughts.tournament.TeamBattle.DataForm.edit(teams.map { t =>
                  s"""${t.id} "${t.name}" by ${Env.user.lightUserApi.sync(t.createdBy).fold(t.createdBy)(_.name)}"""
                }, battle.nbLeaders)
                Ok(html.tournament.teamBattle.edit(tour, form)).fuccess
              }
            }
          }
        case tour => Redirect(routes.Tournament.show(tour.id)).fuccess
      }
    }
  }

  def teamBattleUpdate(id: String) = AuthBody { implicit ctx => me =>
    repo byId id flatMap {
      _ ?? {
        case tour if (tour.createdBy == me.id || isGranted(_.ManageTournament)) && !tour.isFinished =>
          implicit val req = ctx.body
          lidraughts.tournament.TeamBattle.DataForm.empty.bindFromRequest.fold(
            err => BadRequest(html.tournament.teamBattle.edit(tour, err)).fuccess,
            res => env.api.teamBattleUpdate(tour, res, Env.team.api.filterExistingIds) inject
              Redirect(routes.Tournament.show(tour.id))
          )
        case tour => Redirect(routes.Tournament.show(tour.id)).fuccess
      }
    }
  }

  def apiTeamBattleUpdate(id: String) =
    ScopedBody(_.Tournament.Write) { implicit req => me =>
      repo byId id flatMap {
        _ ?? {
          case tour if (tour.createdBy == me.id || isGranted(_.ManageTournament, me)) && !tour.isFinished =>
            lidraughts.tournament.TeamBattle.DataForm.empty
              .bindFromRequest()
              .fold(
                jsonFormErrorDefaultLang,
                res =>
                  env.api.teamBattleUpdate(tour, res, Env.team.api.filterExistingIds) >> {
                    repo byId tour.id map (_ | tour) flatMap { tour =>
                      val lang = me.lang flatMap lidraughts.i18n.I18nLangPicker.byStr getOrElse lidraughts.i18n.defaultLang
                      env.jsonView(tour, none, none, getUserTeamIds, Env.team.cached.name, none, none, partial = false, lang, none)
                    } map { Ok(_) }
                  }
              )
          case _ => BadRequest(jsonError("Can't update that tournament.")).fuccess
        }
      }
    }

  def limitedInvitation = Auth { implicit ctx => me =>
    for {
      (tours, _) <- upcomingCache.get
      res <- lidraughts.tournament.TournamentInviter.findNextFor(me, tours, env.verify.canEnter(me, getUserTeamIds))
    } yield res.fold(Redirect(routes.Tournament.home(1))) { t =>
      Redirect(routes.Tournament.show(t.id))
    }
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  def featured = Open { implicit ctx =>
    negotiate(
      html = notFound,
      api = _ =>
        env.cached.promotable.get.nevermind map {
          lidraughts.tournament.Spotlight.select(_, ctx.me, 4)
        } flatMap env.apiJsonView.featured map { Ok(_) }
    )
  }

  def shields = Open { implicit ctx =>
    for {
      history <- env.shieldApi.history(5.some)
      _ <- Env.user.lightUserApi preloadMany history.userIds
    } yield html.tournament.shields(history)
  }

  def categShields(k: String) = Open { implicit ctx =>
    OptionFuOk(env.shieldApi.byCategKey(k)) {
      case (categ, awards) =>
        Env.user.lightUserApi preloadMany awards.map(_.owner.value) inject
          html.tournament.shields.byCateg(categ, awards)
    }
  }

  def calendar = Open { implicit ctx =>
    env.api.calendar map { tours =>
      Ok(html.tournament.calendar(env.apiJsonView calendar tours))
    }
  }

  def edit(id: String) = Auth { implicit ctx => me =>
    WithEditableTournament(id, me) { tour =>
      teamsIBelongTo(me) map { teams =>
        Ok(html.tournament.form.edit(tour, env.forms.edit(me, tour), env.forms, me, teams))
      }
    }
  }

  def update(id: String) = AuthBody { implicit ctx => me =>
    WithEditableTournament(id, me) { tour =>
      implicit val req = ctx.body
      teamsIBelongTo(me) flatMap { teams =>
        env.forms.edit(me, tour).bindFromRequest
          .fold(
            err => BadRequest(html.tournament.form.edit(tour, err, env.forms, me, teams)).fuccess,
            data => env.api.update(tour, data, me, teams) inject Redirect(routes.Tournament.show(id))
          )
      }
    }
  }

  def terminate(id: String) = Auth { implicit ctx => me =>
    WithEditableTournament(id, me) { tour =>
      env.api kill tour
      Env.mod.logApi.terminateTournament(me.id, tour.fullName)
      Redirect(routes.Tournament.home(1)).fuccess
    }
  }

  def battleTeams(id: String) =
    Open { implicit ctx =>
      repo byId id flatMap {
        _ ?? { tour =>
          tour.isTeamBattle ?? {
            env.cached.battle.teamStanding.get(tour.id) map { standing =>
              Ok(views.html.tournament.teamBattle.standing(tour, standing))
            }
          }
        }
      }
    }

  private def WithEditableTournament(id: String, me: UserModel)(
    f: Tour => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    repo byId id flatMap {
      case Some(t) if (t.createdBy == me.id && t.isCreated) || isGranted(_.ManageTournament) =>
        f(t)
      case Some(t) => Redirect(routes.Tournament.show(t.id)).fuccess
      case _ => notFound
    }

  private val streamerCache = Env.memo.asyncCache.multi[Tour.ID, List[UserModel.ID]](
    name = "tournament.streamers",
    f = tourId => Env.streamer.liveStreamApi.all.flatMap {
      _.streams.map { stream =>
        env.hasUser(tourId, stream.streamer.userId) map (_ option stream.streamer.userId)
      }.sequenceFu.map(_.flatten)
    },
    expireAfter = _.ExpireAfterWrite(15.seconds)
  )

  private def getUserTeamIds(user: lidraughts.user.User): Fu[List[TeamId]] =
    Env.team.cached.teamIdsList(user.id)
}
