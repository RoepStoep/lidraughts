package controllers

import play.api.mvc._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.game.Pov
import lidraughts.user.{ User => UserModel, UserRepo }
import views._

object Tv extends LidraughtsController {

  def index = onChannel(lidraughts.tv.Tv.Channel.Best.key)

  def onChannel(chanKey: String) = Open { implicit ctx =>
    (lidraughts.tv.Tv.Channel.byKey get chanKey).fold(notFound)(lidraughtsTv)
  }

  def sides(gameId: String, color: String) = Open { implicit ctx =>
    OptionFuResult(draughts.Color(color) ?? { Env.round.proxy.pov(gameId, _) }) { pov =>
      Env.game.crosstableApi.withMatchup(pov.game) map { ct =>
        Ok(html.tv.side.sides(pov.some, ct))
      }
    }
  }

  def channels = Api.ApiRequest { implicit ctx =>
    import play.api.libs.json._
    implicit val championWrites = Json.writes[lidraughts.tv.Tv.Champion]
    Env.tv.tv.getChampions map {
      _.channels map { case (chan, champ) => chan.name -> champ }
    } map { Json.toJson(_) } map Api.Data.apply
  }

  private def lidraughtsTv(channel: lidraughts.tv.Tv.Channel)(implicit ctx: Context) =
    Env.tv.tv getGameAndHistory channel flatMap {
      case Some((game, history)) =>
        val flip = getBool("flip")
        val natural = Pov naturalOrientation game
        val pov = if (flip) !natural else natural
        val onTv = lidraughts.round.OnLidraughtsTv(channel.key, flip)
        negotiate(
          html = Env.tournament.api.gameView.watcher(pov.game) flatMap { tour =>
            Env.api.roundApi.watcher(pov, tour, lidraughts.api.Mobile.Api.currentVersion, tv = onTv.some) zip
              Env.game.crosstableApi.withMatchup(game) zip
              Env.tv.tv.getChampions map {
                case data ~ cross ~ champions => NoCache {
                  Ok(html.tv.index(channel, champions, pov.some, data, cross, flip, history))
                }
              }
          },
          api = apiVersion => Env.api.roundApi.watcher(pov, none, apiVersion, tv = onTv.some) map { Ok(_) }
        )
      case _ => negotiate(
        html = Env.tv.tv.getChampions map { champions =>
          Ok(html.tv.index(channel, champions, none, play.api.libs.json.Json.obj(), none, false, Nil))
        },
        api = _ => notFoundJson("No game found")
      )
    }

  def games = gamesChannel(lidraughts.tv.Tv.Channel.Best.key)

  def gamesChannel(chanKey: String) = Open { implicit ctx =>
    (lidraughts.tv.Tv.Channel.byKey get chanKey) ?? { channel =>
      Env.tv.tv.getChampions zip Env.tv.tv.getGames(channel, 15) map {
        case (champs, games) => NoCache {
          Ok(html.tv.games(channel, games map Pov.naturalOrientation, champs))
        }
      }
    }
  }

  private val maxCollectionSize = 21

  def gamesCollection = Open { implicit ctx =>
    val gameIds = get("games") match {
      case Some(gamesStr) if gamesStr.nonEmpty =>
        gamesStr.split(",").toList.take(maxCollectionSize).map(_.split('/'))
      case _ => Nil
    }
    Env.tv.tv.getChampions zip Env.tv.tv.getGamesFromIds(gameIds.flatMap(_.headOption)) map {
      case (champs, games) => NoCache {
        val povs = games.foldRight((List[Pov](), gameIds)) {
          case (game, (povs, ids)) =>
            val gId = ids.findRight(_.headOption.contains(game.id))
            val color = gId.flatMap(_.lastOption).flatMap(draughts.Color.apply).getOrElse(draughts.White)
            val pov = color.fold(Pov.white(game), Pov.black(game))
            (pov :: povs, ids.filterNot(gId.contains))
        }
        Ok(html.tv.gamesCollection(
          povs._1,
          champs
        ))
      }
    }
  }

  def nextGames = Open { implicit ctx =>
    import play.api.libs.json._
    val userIds = get("userids") match {
      case Some(ids) if ids.nonEmpty =>
        ids.split(",").toList.distinct.take(maxCollectionSize)
      case _ => Nil
    }
    val povsFu = userIds map { userId =>
      UserRepo.named(userId) flatMap {
        _ ?? { u => lidraughts.game.GameRepo.lastPlayedPlayingId(u.id).flatMap(_ ?? { Env.round.proxy.pov(_, u) }) }
      }
    } sequenceFu
    val gamesFu = povsFu map { povs =>
      povs flatMap {
        _ map { pov =>
          Json.obj(
            pov.player.userId.getOrElse(UserModel.anonymous) -> html.game.mini(pov, withUserId = true).toString
          )
        }
      }
    }
    gamesFu map { games =>
      val gamesJson = games.foldLeft(Json.obj()) {
        case (json, game) => json ++ game
      }
      Ok(gamesJson)
    }
  }

  def feed = Action.async { req =>
    import makeTimeout.short
    import akka.pattern.ask
    import lidraughts.round.TvBroadcast
    import play.api.libs.EventSource
    Env.round.tvBroadcast ? TvBroadcast.GetEnumerator mapTo
      manifest[TvBroadcast.EnumeratorType] map { enum =>
        Ok.chunked(enum &> EventSource()).as("text/event-stream") |> noProxyBuffer
      }
  }

  /* for BC */
  def embed = Action { req =>
    Ok {
      val config = ui.EmbedConfig(req)
      val url = s"""${req.domain + routes.Tv.frame}?bg=${config.bg}&theme=${config.board}"""
      s"""document.write("<iframe src='https://$url&embed=" + document.domain + "' class='lidraughts-tv-iframe' allowtransparency='true' frameborder='0' style='width: 224px; height: 264px;' title='Lidraughts free online draughts'></iframe>");"""
    } as JAVASCRIPT withHeaders (CACHE_CONTROL -> "max-age=86400")
  }

  def frame = Action.async { implicit req =>
    Env.tv.tv.getBestGame map {
      case None => NotFound
      case Some(game) => Ok(views.html.tv.embed(Pov naturalOrientation game))
    }
  }
}
