package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._

import lidraughts.common.LightUser
import lidraughts.challenge.Challenge
import lidraughts.game.{ Game, Player }
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.User

final class JsonView(
    lightUserApi: lidraughts.user.LightUserApi
) {

  import JsonView._

  def apply(
    tour: ExternalTournament,
    players: List[ExternalPlayer],
    upcoming: List[Challenge],
    ongoing: List[Game],
    finished: List[Game],
    me: Option[User],
    socketVersion: Option[SocketVersion] = None
  ): JsObject = {
    val playersForMe =
      if (me.exists(_.id == tour.createdBy)) players
      else players.filter(_.joined)
    Json
      .obj(
        "id" -> tour.id,
        "createdBy" -> tour.createdBy,
        "name" -> tour.name,
        "players" -> playersForMe.map(externalPlayerJson),
        "upcoming" -> upcoming.map(challengeJson),
        "ongoing" -> ongoing.map(boardJson),
        "finished" -> finished.map(gameJson)
      )
      .add("me" -> me.map(myInfo(_, players)))
      .add("socketVersion" -> socketVersion.map(_.value))
  }

  def apiTournament(
    tour: ExternalTournament
  ): JsObject =
    Json.obj(
      "id" -> tour.id,
      "name" -> tour.name
    )

  def apiPlayer(
    player: ExternalPlayer
  ): JsObject =
    Json.obj(
      "id" -> player.id,
      "userId" -> player.userId,
      "joined" -> player.joined
    )

  private def myInfo(me: User, players: List[ExternalPlayer]) =
    Json
      .obj("userId" -> me.id)
      .add("canJoin" -> players.exists(p => p.invited && p.userId == me.id).option(true))

  private def challengeJson(c: Challenge) = {
    val challenger = c.challenger.fold(_ => none[Challenge.Registered], _.some)
    Json
      .obj(
        "id" -> c.id,
        "variant" -> c.variant
      )
      .add("white" -> c.finalColor.fold(challenger, c.destUser).map(playerJson))
      .add("black" -> c.finalColor.fold(c.destUser, challenger).map(playerJson))
      .add("startsAt", c.external.flatMap(_.startsAt).map(formatDate))
  }

  private def gameJson(g: Game) =
    Json.obj(
      "id" -> g.id,
      "variant" -> g.variant,
      "white" -> playerJson(g.whitePlayer),
      "black" -> playerJson(g.blackPlayer),
      "createdAt" -> formatDate(g.createdAt)
    ).add("winner" -> g.winnerColor.map(_.name))

  private def boardJson(g: Game) =
    Json
      .obj(
        "id" -> g.id,
        "variant" -> g.variant,
        "fen" -> draughts.format.Forsyth.boardAndColor(g.situation),
        "lastMove" -> ~g.lastMoveKeys,
        "orientation" -> g.naturalOrientation.name,
        "white" -> playerJson(g.whitePlayer),
        "black" -> playerJson(g.blackPlayer)
      )
      .add(
        "clock" -> g.clock.ifTrue(g.isBeingPlayed).map { c =>
          Json.obj(
            "white" -> c.remainingTime(draughts.White).roundSeconds,
            "black" -> c.remainingTime(draughts.Black).roundSeconds
          )
        }
      )
      .add("winner" -> g.winnerColor.map(_.name))

  private def playerJson(p: Player): JsObject =
    Json.obj()
      .add("rating" -> p.rating)
      .add("user" -> p.userId.fold(none[LightUser])(lightUserApi.sync))
      .add("provisional" -> p.provisional)

  private def playerJson(p: Challenge.Registered): JsObject =
    Json
      .obj("rating" -> p.rating.int)
      .add("user" -> lightUserApi.sync(p.id))
      .add("provisional" -> p.rating.provisional.option(true))

  private def externalPlayerJson(p: ExternalPlayer): JsObject =
    Json.obj(
      "user" -> lightUserApi.sync(p.userId),
      "joined" -> p.joined
    )
}

object JsonView {

  private def formatDate(date: DateTime) =
    ISODateTimeFormat.dateTime print date

  implicit val variantWriter: OWrites[draughts.variant.Variant] = OWrites { v =>
    Json.obj(
      "key" -> v.key,
      "board" -> v.boardSize
    )
  }
}