package lidraughts.api

import play.api.libs.json.{ Json, JsObject, JsArray }

import lidraughts.game.Pov
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.lobby.SeekApi
import lidraughts.pool.JsonView.poolConfigJsonWriter
import lidraughts.setup.FilterConfig
import lidraughts.user.UserContext

final class LobbyApi(
    getFilter: UserContext => Fu[FilterConfig],
    lightUserApi: lidraughts.user.LightUserApi,
    seekApi: SeekApi,
    pools: List[lidraughts.pool.PoolConfig],
    urgentGames: lidraughts.user.User => Fu[List[Pov]]
) {

  val poolsJson = Json toJson pools

  def apply(implicit ctx: Context): Fu[(JsObject, List[Pov])] =
    ctx.me.fold(seekApi.forAnon)(seekApi.forUser) zip
      (ctx.me ?? urgentGames) zip
      getFilter(ctx) flatMap {
        case seeks ~ povs ~ filter =>
          val displayedPovs = povs take 9
          lightUserApi.preloadMany(displayedPovs.flatMap(_.opponent.userId)) inject {
            Json.obj(
              "me" -> ctx.me.map { u =>
                Json.obj("username" -> u.username).add("isBot" -> u.isBot)
              },
              "seeks" -> JsArray(seeks map (_.render)),
              "nowPlaying" -> JsArray(displayedPovs.map(p => nowPlaying(p, withTitle = true))),
              "nbNowPlaying" -> povs.size,
              "filter" -> filter.render
            ) -> displayedPovs
          }
      }

  def nowPlaying(pov: Pov, withTitle: Boolean = false) = Json.obj(
    "fullId" -> pov.fullId,
    "gameId" -> pov.gameId,
    "fen" -> draughts.format.Forsyth.exportBoard(pov.game.board),
    "color" -> pov.color.name,
    "lastMove" -> ~pov.game.lastMoveKeys,
    "variant" -> Json.obj(
      "key" -> pov.game.variant.key,
      "name" -> pov.game.variant.name,
      "board" -> pov.game.variant.boardSize
    ),
    "speed" -> pov.game.speed.key,
    "perf" -> lidraughts.game.PerfPicker.key(pov.game),
    "rated" -> pov.game.rated,
    "opponent" -> opponentJson(pov.opponent, withTitle),
    "isMyTurn" -> pov.isMyTurn
  ).add("secondsLeft" -> pov.remainingSeconds)
    .add("tournamentId" -> pov.game.tournamentId)
    .add("swissId" -> pov.game.tournamentId)

  private def opponentJson(p: lidraughts.game.Player, withTitle: Boolean) =
    Json.obj("id" -> p.userId)
      .add("rating" -> p.rating)
      .add("ai" -> p.aiLevel) ++ {
        if (withTitle && p.aiLevel.isEmpty) {
          p.userId.flatMap(lightUserApi.sync).fold(
            Json.obj("username" -> (p.name | "Anon."))
          ) { u =>
              Json.obj(
                "username" -> u.name,
                "title" -> u.title
              )
            }
        } else Json.obj(
          "username" -> lidraughts.game.Namer.playerText(p)(lightUserApi.sync)
        )
      }
}
