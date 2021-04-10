package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import play.api.libs.json._
import lidraughts.common.LightUser
import lidraughts.challenge.Challenge
import lidraughts.game.{ Game, Namer, Player }
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.{ LightUserApi, User }

final class JsonView(
    lightUserApi: LightUserApi
) {

  def apply(
    tour: ExternalTournament,
    upcoming: List[Challenge],
    ongoing: List[Game],
    finished: List[Game],
    socketVersion: Option[SocketVersion] = None
  ): Fu[JsObject] = fuccess(
    Json.obj(
      "id" -> tour.id,
      "name" -> tour.name,
      "upcoming" -> upcoming.map(challengeJson),
      "ongoing" -> ongoing.map(gameJson),
      "finished" -> finished.map(gameJson)
    )
      .add("socketVersion" -> socketVersion.map(_.value))
  )

  def api(
    tour: ExternalTournament
  ): Fu[JsObject] = fuccess(
    Json.obj(
      "id" -> tour.id,
      "name" -> tour.name
    )
  )

  def challengeJson(c: Challenge) = {
    val challenger = c.challenger.fold(
      _ => User.anonymous,
      reg => s"${usernameOrAnon(reg.id)} (${reg.rating.show})"
    )
    val destUser = c.destUser.fold(User.anonymous) { reg =>
      s"${usernameOrAnon(reg.id)} (${reg.rating.show})"
    }
    Json.obj(
      "id" -> c.id,
      "whitePlayer" -> c.finalColor.fold(challenger, destUser),
      "blackPlayer" -> c.finalColor.fold(destUser, challenger)
    ).add("startsAt", c.external.flatMap(_.startsAt).map(formatDate))
  }

  def gameJson(g: Game) =
    Json.obj(
      "id" -> g.id,
      "createdAt" -> formatDate(g.createdAt),
      "whitePlayer" -> playerText(g.whitePlayer),
      "blackPlayer" -> playerText(g.blackPlayer)
    )

  private def usernameOrAnon(userId: String) =
    lightUserApi.sync(userId).fold(User.anonymous)(_.titleName)

  private def playerText(player: Player) =
    Namer.playerText(player, withRating = true)(lightUserApi.sync)

  private def formatDate(date: DateTime) =
    ISODateTimeFormat.dateTime print date

}
