package lidraughts.externalTournament

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._

import Cached.FinishedGames
import lidraughts.common.{ LightUser, LightFmjdUser }
import lidraughts.challenge.Challenge
import lidraughts.game.Player
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.socket.Socket.SocketVersion
import lidraughts.user.{ Countries, User }

final class JsonView(
    lightUserApi: lidraughts.user.LightUserApi,
    lightFmjdUserApi: LightFmjdUserApi,
    fmjdPlayerApi: FmjdPlayerApi,
    gameMetaApi: GameMetaApi,
    cached: Cached
) {

  import JsonView._

  def apply(
    tour: ExternalTournament,
    players: List[ExternalPlayer],
    upcoming: List[Challenge],
    ongoing: List[GameWithMeta],
    finished: FinishedGames,
    me: Option[User],
    pref: lidraughts.pref.Pref,
    reqPage: Option[Int] = None, // None = focus on me
    playerInfo: Option[PlayerInfo],
    socketVersion: Option[SocketVersion] = None
  ): Fu[JsObject] = {
    def myPlayer = me.flatMap(u => players.find(_.userId == u.id))
    def myGame = me.flatMap(u => ongoing.find(_.game.userIds.contains(u.id)))
    def fetchFmjd: FetchFmjdSync = fetchLightFmjdUserSync(players, _)
    val fetch = if (tour.settings.userDisplay.fmjd) fetchFmjd else fetchNone
    val page = reqPage orElse myPlayer.map(_.page) getOrElse 1
    for {
      standing <- cached.getStandingPage(tour.id, page)
      createdByMe = me.exists(_.id == tour.createdBy)
      userIds = players.foldLeft(Set.empty[String])((s, p) => s + p.userId) + tour.createdBy
      _ <- lightUserApi.preloadSet(userIds)
      playerInfoJson <- playerInfo.fold(fuccess(none[JsObject])) { playerInfoJson(tour, _, players).dmap(_.some) }
      upcomingMeta <- upcoming.take(5).map(gameMetaApi.withMeta).sequenceFu
    } yield Json.obj(
      "id" -> tour.id,
      "createdBy" -> lightUserApi.sync(tour.createdBy),
      "name" -> tour.name,
      "nbPlayers" -> players.count(_.accepted),
      "nbUpcoming" -> upcoming.length,
      "nbFinished" -> finished.games.length,
      "standing" -> standing,
      "upcoming" -> upcomingMeta.map(challengeJson(_, fetch)),
      "ongoing" -> ongoing.map(boardJson(_, players)),
      "finished" -> finished.games.take(5).map(gameJson(_, fetch)),
      "draughtsResult" -> pref.draughtsResult,
      "displayFmjd" -> tour.settings.userDisplay.fmjd,
      "autoStart" -> tour.settings.autoStart
    )
      .add("rounds" -> tour.settings.nbRounds)
      .add("roundsPlayed" -> finished.rounds)
      .add("invited" -> createdByMe.option(players.filter(!_.accepted).map(invitedPlayerJson)))
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
      "displayFmjd" -> tour.settings.userDisplay.fmjd,
      "hasChat" -> tour.settings.hasChat,
      "autoStart" -> tour.settings.autoStart
    )
      .add("clock" -> tour.clock)
      .add("days" -> tour.days)
      .add("rounds" -> tour.settings.nbRounds)
      .add("description" -> tour.settings.description)

  def apiPlayer(
    player: ExternalPlayer
  ): JsObject =
    Json.obj(
      "userId" -> player.userId,
      "status" -> player.status.key
    ).add("fmjdId", player.fmjdId)

  def apiPlayers(
    players: List[ExternalPlayer]
  ): JsArray =
    JsArray(players.map(apiPlayer))

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
    val game = result.game.game
    val opponent = game.player(!result.color)
    for {
      lightUser <- opponent.userId ?? lightUserApi.async
      externalPlayer = tour.settings.userDisplay.fmjd ?? opponent.userId.flatMap(id => players.find(_.userId == id))
      fmjdPlayer <- externalPlayer.flatMap(_.fmjdId) ?? fmjdPlayerApi.byId
    } yield {
      minimalPlayerJson(game.player(!result.color)) ++
        Json
        .obj(
          "g" -> game.id,
          "c" -> (result.color == draughts.White)
        )
        .add("w" -> result.win)
        .add("r" -> result.game.round)
        .add("user" -> lightUser)
        .add("fmjd" -> fmjdPlayer.map(fmjdPlayerJson))
    }
  }

  private def myInfoJson(me: User, player: Option[ExternalPlayer], gm: Option[GameWithMeta]) =
    Json
      .obj("userId" -> me.id)
      .add("fmjdId" -> player.flatMap(_.fmjdId))
      .add("canJoin" -> player.exists(p => p.invited).option(true))
      .add("rank" -> player.flatMap(_.rank))
      .add("gameId" -> gm.map(_.game.id))

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

  private def challengeJson(withMeta: ChallengeWithMeta, fetch: FetchFmjdSync) = {
    val c = withMeta.challenge
    val challenger = c.challenger.fold(_ => none[Challenge.Registered], _.some)
    Json
      .obj(
        "id" -> c.id,
        "variant" -> c.variant
      )
      .add("white" -> c.finalColor.fold(challenger, c.destUser).map(basePlayerJsonSync(_, fetch)))
      .add("black" -> c.finalColor.fold(c.destUser, challenger).map(basePlayerJsonSync(_, fetch)))
      .add("startsAt", c.external.flatMap(_.startsAt).map(formatDate))
      .add("round" -> withMeta.round)
  }

  private def gameJson(gameMeta: GameWithMeta, fetch: FetchFmjdSync): JsObject = {
    val g = gameMeta.game
    Json
      .obj(
        "id" -> g.id,
        "variant" -> g.variant,
        "white" -> basePlayerJsonSync(g.whitePlayer, fetch),
        "black" -> basePlayerJsonSync(g.blackPlayer, fetch),
        "createdAt" -> formatDate(g.createdAt)
      )
      .add("winner" -> g.winnerColor.map(_.name))
      .add("round" -> gameMeta.round)
  }

  private def boardJson(gameMeta: GameWithMeta, players: List[ExternalPlayer]): JsObject = {
    val g = gameMeta.game
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
      .add("round" -> gameMeta.round)
  }

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
      Json.obj("status" -> p.status.id)

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