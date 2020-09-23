package lidraughts.swiss

import akka.actor._
import akka.pattern.ask

import actorApi._
import akka.actor.ActorSelection
import lidraughts.db.dsl._
import lidraughts.chat.Chat
import lidraughts.common.ApiVersion
import lidraughts.hub.lightTeam.TeamId
import lidraughts.security.Flood
import lidraughts.socket.actorApi.{ Connected => _, _ }
import lidraughts.socket.Handler
import lidraughts.socket.Socket
import lidraughts.team.TeamRepo
import lidraughts.user.User
import makeTimeout.short

private[swiss] final class SocketHandler(
    swissColl: Coll,
    hub: lidraughts.hub.Env,
    socketMap: SocketMap,
    chat: ActorSelection,
    teamOf: Swiss.Id => Fu[Option[TeamId]],
    flood: Flood
) {

  def join(
    swissId: String,
    uid: Socket.Uid,
    user: Option[User],
    version: Option[Socket.SocketVersion],
    apiVersion: ApiVersion
  ): Fu[Option[JsSocketHandler]] =
    swissColl exists $id(swissId) flatMap {
      _ ?? {
        val socket = socketMap getOrMake swissId
        socket.ask[Connected](Join(uid, user, version, _)) map {
          case Connected(enum, member) => Handler.iteratee(
            hub,
            lidraughts.chat.Socket.in(
              chatId = Chat.Id(swissId),
              member = member,
              chat = chat,
              canTimeout = Some { suspectId =>
                teamOf(Swiss.Id(swissId)) flatMap {
                  _ ?? { teamId =>
                    user ?? { u =>
                      lidraughts.team.TeamRepo.isCreator(teamId, u.id)
                    }
                  }
                }
              },
              publicSource = lidraughts.hub.actorApi.shutup.PublicSource.Swiss(swissId).some
            ),
            member,
            socket,
            uid,
            apiVersion
          ) -> enum
        } map some
      }
    }
}