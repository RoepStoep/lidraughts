package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._

import lidraughts.common.{ LightUser, LightFmjdUser }
import lidraughts.challenge.Challenge
import lidraughts.game.{ Game, Player }
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.{ Countries, User }

final class JsonView(
    lightUserApi: lidraughts.user.LightUserApi,
    lightFmjdUserApi: LightFmjdUserApi,
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
    def fetchFmjd: FetchFmjdSync = fetchLightFmjdUserSync(players, _)
    val fetch = if (tour.settings.displayFmjd) fetchFmjd else fetchNone
    val page = reqPage orElse myPlayer.map(_.page) getOrElse 1
    for {
      standing <- cached.getStandingPage(tour.id, page)
      createdByMe = me.exists(_.id == tour.createdBy)
      userIds = players.foldLeft(Set.empty[String])((s, p) => s + p.userId)
      _ <- lightUserApi.preloadSet(userIds)
      playerInfoJson <- playerInfo.fold(fuccess(none[JsObject])) { playerInfoJson(tour, _, players).map(_.some) }
    } yield Json.obj(
      "id" -> tour.id,
      "createdBy" -> tour.createdBy,
      "name" -> tour.name,
      "nbPlayers" -> players.count(_.joined),
      "nbUpcoming" -> upcoming.take(5).length,
      "nbFinished" -> finished.length,
      "standing" -> standing,
      "upcoming" -> upcoming.map(challengeJson(_, fetch)),
      "ongoing" -> ongoing.map(boardJson(_, players)),
      "finished" -> finished.take(5).map(gameJson(_, fetch)),
      "draughtsResult" -> pref.draughtsResult,
      "displayFmjd" -> tour.settings.displayFmjd
    )
      .add("rounds" -> tour.settings.nbRounds)
      .add("invited" -> createdByMe.option(players.filter(!_.joined).map(invitedPlayerJson)))
      .add("me" -> me.map(myInfoJson(_, myPlayer, myGame)))
      .add("playerInfo" -> playerInfoJson)
      .add("socketVersion" -> socketVersion.map(_.value))
  }

  private def fetchLightFmjdUserSync(players: List[ExternalPlayer], userId: Option[User.ID]) =
    userId.flatMap(id => players.find(_.userId == id)).flatMap(_.fmjdId).flatMap(lightFmjdUserApi.sync)

  private def fetchLightFmjdUserSync(player: Option[ExternalPlayer]) =
    player.flatMap(_.fmjdId).flatMap(lightFmjdUserApi.sync)

  def apiTournament(
    tour: ExternalTournament
  ): JsObject =
    Json.obj(
      "id" -> tour.id,
      "createdBy" -> tour.createdBy,
      "name" -> tour.name,
      "variant" -> tour.variant.key,
      "rated" -> tour.rated,
      "displayFmjd" -> tour.settings.displayFmjd,
      "hasChat" -> tour.settings.hasChat
    )
      .add("clock" -> tour.clock)
      .add("days" -> tour.days)
      .add("rounds" -> tour.settings.nbRounds)
      .add("description" -> tour.settings.description)

  def apiPlayer(
    player: ExternalPlayer
  ): JsObject =
    Json.obj(
      "id" -> player.id,
      "userId" -> player.userId,
      "joined" -> player.joined
    )

  def playerInfoJson(
    tour: ExternalTournament,
    info: PlayerInfo,
    allPlayers: List[ExternalPlayer]
  ): Fu[JsObject] =
    for {
      baseJson <- basePlayerJsonNoFmjdAsync(info.player)
      fmjdPlayer <- info.player.fmjdId ?? fmjdPlayerApi.byId
      sheet <- info.results.map(resultJson(tour, _, allPlayers)).sequenceFu
    } yield {
      baseJson ++ Json.obj(
        "points" -> info.player.points,
        "sheet" -> sheet
      ).add("fmjd" -> fmjdPlayer.map(fmjdPlayerJson))
    }

  private def resultJson(tour: ExternalTournament, result: PlayerInfo.Result, players: List[ExternalPlayer]) = {
    val opponent = result.game.player(!result.color)
    for {
      lightUser <- opponent.userId ?? lightUserApi.async
      externalPlayer = tour.settings.displayFmjd ?? opponent.userId.flatMap(id => players.find(_.userId == id))
      fmjdPlayer <- externalPlayer.flatMap(_.fmjdId) ?? fmjdPlayerApi.byId
    } yield {
      minimalPlayerJson(result.game.player(!result.color)) ++
        Json
        .obj(
          "g" -> result.game.id,
          "c" -> (result.color == draughts.White)
        )
        .add("w" -> result.win)
        .add("user" -> lightUser)
        .add("fmjd" -> fmjdPlayer.map(fmjdPlayerJson))
    }
  }

  private def myInfoJson(me: User, player: Option[ExternalPlayer], game: Option[Game]) =
    Json
      .obj("userId" -> me.id)
      .add("canJoin" -> player.exists(p => p.invited).option(true))
      .add("rank" -> player.flatMap(_.rank))
      .add("gameId" -> game.map(_.id))

  private def countryJson(cc: String) =
    Countries.info(cc) match {
      case Some(c) =>
        Json.obj(
          "code" -> c.code,
          "name" -> c.name
        )
      case _ =>
        Json.obj(
          "code" -> Countries.unknown,
          "name" -> "Unknown"
        )
    }

  private def challengeJson(c: Challenge, fetch: FetchFmjdSync) = {
    val challenger = c.challenger.fold(_ => none[Challenge.Registered], _.some)
    Json
      .obj(
        "id" -> c.id,
        "variant" -> c.variant
      )
      .add("white" -> c.finalColor.fold(challenger, c.destUser).map(basePlayerJsonSync(_, fetch)))
      .add("black" -> c.finalColor.fold(c.destUser, challenger).map(basePlayerJsonSync(_, fetch)))
      .add("startsAt", c.external.flatMap(_.startsAt).map(formatDate))
  }

  private def gameJson(g: Game, fetch: FetchFmjdSync) =
    Json.obj(
      "id" -> g.id,
      "variant" -> g.variant,
      "white" -> basePlayerJsonSync(g.whitePlayer, fetch),
      "black" -> basePlayerJsonSync(g.blackPlayer, fetch),
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
        "white" -> boardPlayerJsonSync(g.whitePlayer, players),
        "black" -> boardPlayerJsonSync(g.blackPlayer, players)
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
      "country" -> countryJson(p.country),
      "picUrl" -> fmjdPlayerApi.profilePicUrl(p.id)
    ).add("title" -> p.bestTitle)
      .add("rating" -> p.rating)

  private def invitedPlayerJson(p: ExternalPlayer): JsObject =
    basePlayerJsonSync(p) ++
      Json.obj(
        "status" -> p.status.id
      )

  private def boardPlayerJsonSync(player: Player, players: List[ExternalPlayer]): JsObject = {
    val playerExt = players.find(p => player.userId.contains(p.userId))
    minimalPlayerJson(player)
      .add("rank" -> playerExt.flatMap(_.rank))
      .add("user" -> player.userId.fold(none[LightUser])(lightUserApi.sync))
      .add("fmjd" -> fetchLightFmjdUserSync(playerExt))
  }

  private def basePlayerJsonSync(p: Player, fetch: FetchFmjdSync): JsObject =
    Json.obj()
      .add("rating" -> p.rating)
      .add("provisional" -> p.provisional)
      .add("user" -> p.userId.fold(none[LightUser])(lightUserApi.sync))
      .add("fmjd" -> fetch(p.userId))

  private def basePlayerJsonSync(p: Challenge.Registered, fetch: FetchFmjdSync): JsObject =
    Json
      .obj("rating" -> p.rating.int)
      .add("user" -> lightUserApi.sync(p.id))
      .add("provisional" -> p.rating.provisional.option(true))
      .add("fmjd" -> fetch(p.id.some))

  private def basePlayerJsonSync(p: ExternalPlayer): JsObject =
    minimalPlayerJson(p)
      .add("user" -> lightUserApi.sync(p.userId))
      .add("fmjd" -> fetchLightFmjdUserSync(p.some))

  private def basePlayerJsonNoFmjdAsync(p: ExternalPlayer): Fu[JsObject] =
    lightUserApi.async(p.userId) map { u =>
      minimalPlayerJson(p) ++
        Json.obj("user" -> u)
    }

  private def minimalPlayerJson(p: ExternalPlayer): JsObject =
    Json
      .obj("rating" -> p.rating)
      .add("provisional" -> p.provisional.option(true))
      .add("rank" -> p.rank)

  private def minimalPlayerJson(p: Player): JsObject =
    Json.obj()
      .add("rating" -> p.rating)
      .add("provisional" -> p.provisional)
}

object JsonView {

  private type FetchFmjdSync = Option[User.ID] => Option[LightFmjdUser]

  private def fetchNone: FetchFmjdSync = (_: Option[User.ID]) => none[LightFmjdUser]

  private def formatDate(date: DateTime) =
    ISODateTimeFormat.dateTime print date

  implicit val variantWriter: OWrites[draughts.variant.Variant] = OWrites { v =>
    Json.obj(
      "key" -> v.key,
      "board" -> v.boardSize
    )
  }

  implicit val clockWriter: OWrites[draughts.Clock.Config] = OWrites { clock =>
    Json.obj(
      "limit" -> clock.limitSeconds,
      "increment" -> clock.incrementSeconds
    )
  }
}