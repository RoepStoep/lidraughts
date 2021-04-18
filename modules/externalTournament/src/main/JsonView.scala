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
    lightUserApi: lidraughts.user.LightUserApi,
    fmjdPlayerApi: FmjdPlayerApi,
    cached: Cached
) {

  import JsonView._

  def apply(
    tour: ExternalTournament,
    players: List[ExternalPlayer],
    upcoming: List[Challenge],
    ongoing: List[Game],
    finished: List[Game],
    me: Option[User],
    pref: lidraughts.pref.Pref,
    reqPage: Option[Int] = None, // None = focus on me
    playerInfo: Option[PlayerInfo],
    socketVersion: Option[SocketVersion] = None
  ): Fu[JsObject] = {
    def myPlayer = me.flatMap(u => players.find(_.userId == u.id))
    def myGame = me.flatMap(u => ongoing.find(_.player(u).isDefined))
    val page = reqPage orElse myPlayer.map(_.page) getOrElse 1
    for {
      standing <- cached.getStandingPage(tour.id, page)
      createdByMe = me.exists(_.id == tour.createdBy)
      userIds = players.foldLeft(Set.empty[String])((s, p) => s + p.userId)
      _ <- lightUserApi.preloadSet(userIds)
      playerInfoJson <- playerInfo.fold(fuccess(none[JsObject])) { playerInfoJson(_).map(_.some) }
    } yield Json.obj(
      "id" -> tour.id,
      "createdBy" -> tour.createdBy,
      "name" -> tour.name,
      "nbPlayers" -> players.count(_.joined),
      "nbUpcoming" -> upcoming.take(5).length,
      "nbFinished" -> finished.length,
      "standing" -> standing,
      "upcoming" -> upcoming.map(challengeJson),
      "ongoing" -> ongoing.map(boardJson(_, players)),
      "finished" -> finished.take(5).map(gameJson),
      "draughtsResult" -> pref.draughtsResult
    )
      .add("rounds" -> tour.rounds)
      .add("invited" -> createdByMe.option(players.filter(!_.joined).map(invitedPlayerJson)))
      .add("me" -> me.map(myInfoJson(_, myPlayer, myGame)))
      .add("playerInfo" -> playerInfoJson)
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

  def playerInfoJson(
    info: PlayerInfo
  ): Fu[JsObject] =
    for {
      baseJson <- basePlayerJsonAsync(info.player)
      fmjdPlayer <- info.player.fmjdId ?? fmjdPlayerApi.byId
    } yield {
      baseJson ++ Json.obj(
        "points" -> info.player.points,
        "sheet" -> info.results.map(resultJson)
      ).add("fmjd" -> fmjdPlayer.map(fmjdPlayerJson))
    }

  private def resultJson(result: PlayerInfo.Result) =
    basePlayerJson(result.game.player(!result.color)) ++
      Json.obj(
        "g" -> result.game.id,
        "c" -> (result.color == draughts.White)
      ).add("w" -> result.win)

  private def myInfoJson(me: User, player: Option[ExternalPlayer], game: Option[Game]) =
    Json
      .obj("userId" -> me.id)
      .add("canJoin" -> player.exists(p => p.invited).option(true))
      .add("rank" -> player.flatMap(_.rank))
      .add("gameId" -> game.map(_.id))

  private def challengeJson(c: Challenge) = {
    val challenger = c.challenger.fold(_ => none[Challenge.Registered], _.some)
    Json
      .obj(
        "id" -> c.id,
        "variant" -> c.variant
      )
      .add("white" -> c.finalColor.fold(challenger, c.destUser).map(basePlayerJson))
      .add("black" -> c.finalColor.fold(c.destUser, challenger).map(basePlayerJson))
      .add("startsAt", c.external.flatMap(_.startsAt).map(formatDate))
  }

  private def gameJson(g: Game) =
    Json.obj(
      "id" -> g.id,
      "variant" -> g.variant,
      "white" -> basePlayerJson(g.whitePlayer),
      "black" -> basePlayerJson(g.blackPlayer),
      "createdAt" -> formatDate(g.createdAt)
    ).add("winner" -> g.winnerColor.map(_.name))

  private def boardJson(g: Game, players: List[ExternalPlayer]) =
    Json
      .obj(
        "id" -> g.id,
        "variant" -> g.variant,
        "fen" -> draughts.format.Forsyth.boardAndColor(g.situation),
        "lastMove" -> ~g.lastMoveKeys,
        "orientation" -> g.naturalOrientation.name,
        "white" -> boardPlayerJson(g.whitePlayer, players),
        "black" -> boardPlayerJson(g.blackPlayer, players)
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

  private def fmjdPlayerJson(p: FmjdPlayer): JsObject =
    Json.obj(
      "id" -> p.id,
      "name" -> p.displayName,
      "country" -> p.country,
      "picUrl" -> fmjdPlayerApi.profilePicUrl(p.id)
    ).add("title" -> p.title)
      .add("rating" -> p.rating)

  private def invitedPlayerJson(p: ExternalPlayer): JsObject =
    basePlayerJson(p) ++
      Json.obj(
        "status" -> p.status.id
      )

  private def boardPlayerJson(player: Player, players: List[ExternalPlayer]): JsObject = {
    val playerExt = players.find(p => player.userId.contains(p.userId))
    basePlayerJson(player)
      .add("rank" -> playerExt.flatMap(_.rank))
  }

  private def basePlayerJson(p: Player): JsObject =
    Json.obj()
      .add("rating" -> p.rating)
      .add("user" -> p.userId.fold(none[LightUser])(lightUserApi.sync))
      .add("provisional" -> p.provisional)

  private def basePlayerJson(p: Challenge.Registered): JsObject =
    Json
      .obj("rating" -> p.rating.int)
      .add("user" -> lightUserApi.sync(p.id))
      .add("provisional" -> p.rating.provisional.option(true))

  private def basePlayerJson(p: ExternalPlayer): JsObject =
    basePlayerJsonWithoutUser(p) ++
      Json.obj("user" -> lightUserApi.sync(p.userId))

  private def basePlayerJsonAsync(p: ExternalPlayer): Fu[JsObject] =
    lightUserApi.async(p.userId) map { u =>
      basePlayerJsonWithoutUser(p) ++
        Json.obj("user" -> u)
    }

  private def basePlayerJsonWithoutUser(p: ExternalPlayer): JsObject =
    Json
      .obj("rating" -> p.rating)
      .add("provisional" -> p.provisional.option(true))
      .add("rank" -> p.rank)
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