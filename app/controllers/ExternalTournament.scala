package controllers

import play.api.libs.json._
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.externalTournament.{ ExternalTournament => ExternalTournamentModel, ExternalPlayerRepo, ApiJsonView }
import lidraughts.user.UserRepo
import views._

object ExternalTournament extends LidraughtsController {

  private def env = Env.externalTournament
  private def api = Env.externalTournament.api

  private def tournamentNotFound(implicit ctx: Context) = NotFound(html.externalTournament.bits.notFound())

  def show(id: String) = Open { implicit ctx =>
    api byId id flatMap { tourOption =>
      val page = getInt("page").filter(0.<)
      negotiate(
        html = tourOption.fold(tournamentNotFound.fuccess) { tour =>
          for {
            ongoing <- env.cached.getOngoingGames(tour.id)
            finished <- env.cached.getFinishedGames(tour.id)
            version <- env.version(tour.id)
            json <- env.jsonView(
              tour = tour,
              ongoing = ongoing,
              finished = finished,
              me = ctx.me,
              pref = ctx.pref,
              reqPage = page,
              playerInfo = none,
              socketVersion = version.some
            )
            canChat <- canHaveChat(tour)
            chat <- canChat ?? Env.chat.api.userChat.cached
              .findMine(lidraughts.chat.Chat.Id(tour.id), ctx.me)
              .dmap(some)
            _ <- chat ?? { c =>
              Env.user.lightUserApi.preloadMany(c.chat.userIds)
            }
          } yield html.externalTournament.show(tour, finished.actualRoundsPlayed(ongoing), json, chat)
        },
        api = _ =>
          tourOption.fold(notFoundJson("No such tournament")) { tour =>
            for {
              ongoing <- env.cached.getOngoingGames(tour.id)
              finished <- env.cached.getFinishedGames(tour.id)
              playerInfo <- get("playerInfo").?? { env.api.playerInfo(tour, _) }
              version <- env.version(tour.id)
              json <- env.jsonView(
                tour = tour,
                ongoing = ongoing,
                finished = finished,
                me = ctx.me,
                pref = ctx.pref,
                reqPage = page,
                playerInfo = playerInfo,
                socketVersion = version.some
              )
            } yield Ok(json)
          }
      )
    }
  }

  def tournamentJson(id: String) = Scoped(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      for {
        players <- ExternalPlayerRepo.byTour(tour.id)
        upcoming <- env.cached.getUpcomingGames(tour.id)
        json = ApiJsonView.tournament(tour, players.some, upcoming.some)
      } yield JsonOk(json)
    }
  }

  def apiCreate = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    if (me.isBot || me.lame) notFoundJson("This account cannot create tournaments")
    else env.forms.tournamentCreate.bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      data => api.create(data, me) map { t =>
        ApiJsonView.tournament(t)
      } map { Ok(_) }
    )
  }

  def apiUpdate(id: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      env.forms.tournamentUpdate(tour).bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data =>
          api.update(tour.id, data).fold(
            err => BadRequest(jsonError(err.getMessage)),
            tour => tour.fold(BadRequest(jsonError("Could not update tournament"))) { t =>
              Ok(ApiJsonView.tournament(t))
            }
          )
      )
    }
  }

  def tournamentResults(id: String) = Open { implicit ctx =>
    WithTournament(id) { tour =>
      env.cached.getFinishedGames(id) map { finished =>
        Ok(html.externalTournament.results(tour, finished))
      }
    }
  }

  def answer(id: String) = AuthBody(BodyParsers.parse.json) { implicit ctx => me =>
    WithTournament(id) { tour =>
      val accept = ~ctx.body.body.\("accept").asOpt[Boolean]
      env.api.answer(tour.id, me, accept) map { result =>
        if (result) jsonOkResult
        else BadRequest(Json.obj("ok" -> false))
      }
    }
  }

  def playerCreate(id: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      env.forms.playerCreate.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data =>
          UserRepo.enabledByName(data.userId) flatMap { userOpt =>
            userOpt.fold(badRequestJson("Invalid userId")) { user =>
              api.addPlayer(tour.id, data, user) map {
                _.fold(BadRequest(jsonError("A player with this userId already exists"))) { p =>
                  JsonOk(ApiJsonView.player(p))
                }
              }
            }
          }
      )
    }
  }

  def playerDelete(id: String, userId: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      ExternalPlayerRepo.find(tour.id, userId.toLowerCase) flatMap { playerOpt =>
        playerOpt.fold(badRequestJson("User not found")) { player =>
          api.deletePlayer(tour.id, player) map { deleted =>
            if (deleted) jsonOkResult
            else BadRequest(jsonError("Cannot delete a player with upcoming / ongoing / finished games"))
          }
        }
      }
    }
  }

  def playerBye(tourId: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, tourId) { tour =>
      env.forms.playerBye.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data =>
          api.processBye(tour.id, data).fold(
            err => BadRequest(jsonError(err.getMessage)),
            _ => jsonOkResult
          )
      )
    }
  }

  def gameCreate(tourId: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, tourId) { tour =>
      env.forms.gameCreate.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data =>
          api.addChallenge(tour.id, data).fold(
            err => BadRequest(jsonError(err.getMessage)),
            challenge => challenge.fold(BadRequest(jsonError("Could not create game"))) { c =>
              JsonOk(ApiJsonView.challenge(c))
            }
          )
      )
    }
  }

  def gameDelete(tourId: String, gameId: String) = ScopedBody(_.ExternalTournament.Write) { implicit req => me =>
    WithMyTournament(me, tourId) { tour =>
      Env.challenge.api.externalById(gameId) flatMap { challengeOpt =>
        challengeOpt.fold(notFoundJson("No such game")) { challenge =>
          if (!challenge.externalTournamentId.contains(tour.id)) notFoundJson("No such game")
          else api.deleteChallenge(tour.id, challenge) inject jsonOkResult
        }
      }
    }
  }

  def playerInfo(id: String, userId: String) = Action.async {
    WithTournament(id) { tour =>
      env.api.playerInfo(tour, userId) flatMap {
        _.fold(notFoundJson()) { info =>
          ExternalPlayerRepo.byTour(id) flatMap { players =>
            JsonOk(env.jsonView.playerInfoJson(tour, info, players))
          }
        }
      }
    }
  }

  def pageOf(id: String, userId: String) = Open { implicit ctx =>
    WithTournament(id) { tour =>
      env.api.pageOf(tour, lidraughts.user.User normalize userId) flatMap {
        _ ?? { page =>
          JsonOk {
            env.cached.getStandingPage(tour.id, page)
          }
        }
      }
    }
  }

  def standing(id: String, page: Int) = Open { implicit ctx =>
    WithTournament(id) { tour =>
      JsonOk {
        env.cached.getStandingPage(tour.id, page.atLeast(1))
      }
    }
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  private def WithTournament(id: String)(f: ExternalTournamentModel => Fu[Result]): Fu[Result] =
    env.api.byId(id) flatMap { _ ?? f }

  private def WithMyTournament(me: lidraughts.user.User, id: String)(f: ExternalTournamentModel => Fu[Result]): Fu[Result] =
    api.byId(id) flatMap {
      case None => notFoundJson("No such tournament")
      case Some(tour) if me.id == tour.createdBy => f(tour)
      case _ => fuccess(Unauthorized)
    }

  private def canHaveChat(tour: ExternalTournamentModel)(implicit ctx: Context): Fu[Boolean] = {
    import ExternalTournamentModel.ChatVisibility._
    (tour.settings.chat != Nobody && ctx.noKid) ?? ctx.userId.fold(fuccess(tour.settings.chat == Everyone)) { userId =>
      if (tour.settings.chat == Everyone || tour.createdBy == userId || isGranted(_.ChatTimeout)) fuTrue // moderators and creator
      else ExternalPlayerRepo.find(tour.id, userId).dmap(_ ?? { !_.status.is(_.Rejected) }) // players unless rejected
    }
  }
}
