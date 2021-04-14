package controllers

import play.api.libs.json._
import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.externalTournament.{ ExternalTournament => ExternalTournamentModel, PlayerRepo }
import views._

object ExternalTournament extends LidraughtsController {

  private def env = Env.externalTournament
  private def api = Env.externalTournament.api

  def show(id: String) = Open { implicit ctx =>
    api byId id flatMap { tourOption =>
      negotiate(
        html = tourOption.fold(notFound) { tour =>
          for {
            players <- PlayerRepo.byTour(tour.id)
            upcoming <- Env.challenge.api.allForExternalTournament(tour.id)
            ongoing <- env.cached.getOngoingGames(id)
            finished <- env.cached.getFinishedGames(id)
            version <- env.version(tour.id)
            json = env.jsonView(
              tour = tour,
              players = players,
              upcoming = upcoming,
              ongoing = ongoing,
              finished = finished,
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
          tourOption.fold(notFoundJson("No such external tournament")) { tour =>
            for {
              upcoming <- Env.challenge.api.allForExternalTournament(tour.id)
              ongoing <- env.cached.getOngoingGames(id)
              finished <- env.cached.getFinishedGames(id)
              version <- env.version(tour.id)
              json = env.jsonView(
                tour = tour,
                players = players,
                upcoming = upcoming,
                ongoing = ongoing,
                finished = finished,
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

  def playerAdd(id: String) = ScopedBody(_.Tournament.Write) { implicit req => me =>
    TournamentOwner(me, id) { tour =>
      api.playerForm.bindFromRequest.fold(
        jsonFormErrorDefaultLang,
        data => api.addPlayer(tour, data) map { p =>
          env.jsonView.apiPlayer(p)
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
      case None => notFoundJson("No such external tournament")
      case Some(tour) if me.id == tour.createdBy => f(tour)
      case _ => fuccess(Unauthorized)
    }

  private def canHaveChat(tour: ExternalTournamentModel)(implicit ctx: Context): Boolean =
    ctx.noKid
}
