package controllers

import play.api.libs.json._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.externalTournament.{ ExternalTournament => ExternalTournamentModel }
import lidraughts.game.GameRepo
import views._

object ExternalTournament extends LidraughtsController {

  private def env = Env.externalTournament
  private def api = Env.externalTournament.api

  def show(id: String) = Open { implicit ctx =>
    api byId id flatMap { tourOption =>
      negotiate(
        html = tourOption.fold(notFound) { tour =>
          for {
            upcoming <- Env.challenge.api.allForExternalTournament(tour.id)
            ongoing <- env.cached.getOngoingGames(id)
            finished <- env.cached.getFinishedGames(id)
            version <- env.version(tour.id)
            json = env.jsonView(
              tour = tour,
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
    else api.createForm.bindFromRequest.fold(
      jsonFormErrorDefaultLang,
      data => api.create(data, me.id) map { tour =>
        env.jsonView.api(tour)
      } map { Ok(_) }
    )
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      env.socketHandler.join(id, uid, ctx.me, getSocketVersion, apiVersion)
    }
  }

  private def canHaveChat(tour: ExternalTournamentModel)(implicit ctx: Context): Boolean =
    ctx.noKid
}
