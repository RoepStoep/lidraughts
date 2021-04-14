package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._

import lidraughts.common.LightUser
import lidraughts.challenge.Challenge
import lidraughts.game.{ Game, Player }
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.socket.Socket.SocketVersion

final class JsonView(
    lightUserApi: lidraughts.user.LightUserApi
) {

  import JsonView._

  def apply(
    tour: ExternalTournament,
    upcoming: List[Challenge],
    ongoing: List[Game],
    finished: List[Game],
    socketVersion: Option[SocketVersion] = None
  ): JsObject =
    Json.obj(
      "id" -> tour.id,
      "name" -> tour.name,
      "upcoming" -> upcoming.map(challengeJson),
      "ongoing" -> ongoing.map(boardJson),
      "finished" -> finished.map(gameJson)
    ).add("socketVersion" -> socketVersion.map(_.value))

  def api(
    tour: ExternalTournament
  ): JsObject =
    Json.obj(
      "id" -> tour.id,
      "name" -> tour.name
    )

  def challengeJson(c: Challenge) = {
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

  def gameJson(g: Game) =
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