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
      negotiate(
        html = tourOption.fold(tournamentNotFound.fuccess) { tour =>
          for {
            players <- ExternalPlayerRepo.byTour(id)
            upcoming <- Env.challenge.api.allForExternalTournament(id)
            ongoing <- env.cached.getOngoingGames(id)
            finished <- env.cached.getFinishedGames(id)
            version <- env.version(tour.id)
            json = env.jsonView(
              tour = tour,
              players = players,
              upcoming = upcoming,
              ongoing = ongoing,
              finished = finished,
              me = ctx.me,
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
              version <- env.version(tour.id)
              json = env.jsonView(
                tour = tour,
                players = players,
                upcoming = upcoming,
                ongoing = ongoing,
                finished = finished,
                me = ctx.me,
                socketVersion = version.some
              )
            } yield Ok(json)
          }
      )
    }
  }

  def create = ScopedBody(_.Tournament.Write) { implicit req => me =>
    if (me.isBot || me.lame) notFoundJson("This account cannot create tournaments")
    else api.tournamentForm.bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      data => api.create(data, me) map { t =>
        env.jsonView.apiTournament(t)
      } map { Ok(_) }
    )
  }

  def answer(id: String) = AuthBody(BodyParsers.parse.json) { implicit ctx => me =>
    val accept = ~ctx.body.body.\("accept").asOpt[Boolean]
    env.api.answer(id, me, accept) map { result =>
      if (result) jsonOkResult
      else BadRequest(Json.obj("joined" -> false))
    }
  }

  def playerAdd(id: String) = ScopedBody(_.Tournament.Write) { implicit req => me =>
    TournamentOwner(me, id) { tour =>
      api.playerForm.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data => api.addPlayer(tour, data) map {
          _.fold(jsonError("A player with this userId already exists"))(env.jsonView.apiPlayer)
        } map { Ok(_) }
      )
    }
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  private def TournamentOwner(me: lidraughts.user.User, tourId: ExternalTournamentModel.ID)(f: ExternalTournamentModel => Fu[Result]): Fu[Result] =
    api.byId(tourId) flatMap {
      case None => notFoundJson("No such tournament")
      case Some(tour) if me.id == tour.createdBy => f(tour)
      case _ => fuccess(Unauthorized)
    }

  private def canHaveChat(tour: ExternalTournamentModel)(implicit ctx: Context): Boolean =
    ctx.noKid
}
