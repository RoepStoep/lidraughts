package controllers

import play.api.libs.json._
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.externalTournament.{ ExternalTournament => ExternalTournamentModel, ExternalPlayerRepo }
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
            players <- ExternalPlayerRepo.byTour(id)
            upcoming <- Env.challenge.api.allForExternalTournament(id)
            ongoing <- env.cached.getOngoingGames(id)
            finished <- env.cached.getFinishedGames(id)
            version <- env.version(tour.id)
            json <- env.jsonView(
              tour = tour,
              players = players,
              upcoming = upcoming,
              ongoing = ongoing,
              finished = finished,
              me = ctx.me,
              pref = ctx.pref,
              reqPage = page,
              playerInfo = none,
              socketVersion = version.some
            )
            chat <- canHaveChat(tour) ?? Env.chat.api.userChat.cached
              .findMine(lidraughts.chat.Chat.Id(tour.id), ctx.me)
              .dmap(some)
            _ <- chat ?? { c =>
              Env.user.lightUserApi.preloadMany(c.chat.userIds)
            }
          } yield html.externalTournament.show(tour, json, chat)
        },
        api = _ =>
          tourOption.fold(notFoundJson("No such tournament")) { tour =>
            for {
              players <- ExternalPlayerRepo.byTour(id)
              upcoming <- Env.challenge.api.allForExternalTournament(id)
              ongoing <- env.cached.getOngoingGames(id)
              finished <- env.cached.getFinishedGames(id)
              playerInfo <- get("playerInfo").?? { env.api.playerInfo(tour, _) }
              version <- env.version(tour.id)
              json <- env.jsonView(
                tour = tour,
                players = players,
                upcoming = upcoming,
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

  def apiCreate = ScopedBody(_.Tournament.Write) { implicit req => me =>
    if (me.isBot || me.lame) notFoundJson("This account cannot create tournaments")
    else env.forms.tournamentCreate.bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      data => api.create(data, me) map { t =>
        env.jsonView.apiTournament(t)
      } map { Ok(_) }
    )
  }

  def apiUpdate(id: String) = ScopedBody(_.Tournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      env.forms.tournamentUpdate(tour).bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data => {
          val gamesPlayedFu = for {
            finished <- env.cached.getFinishedGames(id)
            ongoing <- finished.isEmpty ?? env.cached.getOngoingGames(id)
            upcoming <- ongoing.isEmpty ?? Env.challenge.api.allForExternalTournament(id)
          } yield finished.nonEmpty || ongoing.nonEmpty || upcoming.nonEmpty
          gamesPlayedFu flatMap { gamesPlayed =>
            if (gamesPlayed && data.changedGameSettings(tour))
              fuccess(BadRequest(jsonError("Cannot change game settings once games are added to the tournament")))
            else api.update(id, data) map { opt =>
              opt.fold(BadRequest(jsonError("Could not update tournament"))) { t =>
                Ok(env.jsonView.apiTournament(t))
              }
            }
          }
        }
      )
    }
  }

  def tournamentResults(id: String) = Open { implicit ctx =>
    WithTournament(id) { tour =>
      env.cached.getFinishedGames(id) map { results =>
        Ok(html.externalTournament.results(tour, results))
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

  def playerAdd(id: String) = ScopedBody(_.Tournament.Write) { implicit req => me =>
    WithMyTournament(me, id) { tour =>
      env.forms.playerCreate.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data => api.addPlayer(tour.id, data) map {
          _.fold(jsonError("A player with this userId already exists"))(env.jsonView.apiPlayer)
        } map { Ok(_) }
      )
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

  private def canHaveChat(tour: ExternalTournamentModel)(implicit ctx: Context): Boolean =
    tour.settings.hasChat && ctx.noKid
}
