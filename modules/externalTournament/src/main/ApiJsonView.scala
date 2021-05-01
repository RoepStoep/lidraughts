package lidraughts.externalTournament

import play.api.libs.json._

import lidraughts.challenge.Challenge

object ApiJsonView {

  import JsonView._

  def tournament(
    tour: ExternalTournament,
    players: Option[List[ExternalPlayer]] = None,
    upcoming: Option[List[Challenge]] = None
  ): JsObject =
    Json.obj(
      "id" -> tour.id,
      "createdBy" -> tour.createdBy,
      "name" -> tour.name,
      "variant" -> tour.variant.key,
      "rated" -> tour.rated,
      "userDisplay" -> tour.settings.userDisplay.key,
      "chat" -> tour.settings.chat.key,
      "autoStartGames" -> tour.settings.autoStart,
      "microMatches" -> tour.settings.microMatches
    )
      .add("startsAt" -> tour.settings.startsAt)
      .add("clock" -> tour.clock)
      .add("days" -> tour.days)
      .add("rounds" -> tour.settings.nbRounds)
      .add("description" -> tour.settings.description)
      .add("players" -> players.map(_.map(player)))
      .add("upcoming" -> upcoming.map(_.map(challenge)))

  def player(
    player: ExternalPlayer
  ): JsObject =
    Json.obj(
      "userId" -> player.userId,
      "status" -> player.status.key
    ).add("fmjdId", player.fmjdId)

  def challenge(c: Challenge) = {
    val challenger = c.challenger.fold(_ => none[Challenge.Registered], _.some)
    Json
      .obj("id" -> c.id)
      .add("whiteUserId" -> c.finalColor.fold(challenger, c.destUser).map(_.id))
      .add("blackUserId" -> c.finalColor.fold(c.destUser, challenger).map(_.id))
      .add("startsAt", c.external.flatMap(_.startsAt))
      .add("round" -> c.round)
      .add("fen" -> c.customStartingPosition.??(c.initialFen.map(_.value)))
  }

}

