package controllers

import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc.{ Result, Results }
import scala.concurrent.duration._

import draughts.format.FEN
import draughts.variant.{ Variant, Standard }
import lidraughts.api.{ Context, BodyContext }
import lidraughts.app._
import lidraughts.common.{ HTTPRequest, LidraughtsCookie, IpAddress }
import lidraughts.game.{ GameRepo, Pov, AnonCookie }
import lidraughts.setup.Processor.HookResult
import lidraughts.setup.ValidFen
import lidraughts.socket.Socket.Uid
import lidraughts.user.UserRepo
import views._

object Setup extends LidraughtsController with TheftPrevention {

  private def env = Env.setup

  private[controllers] val PostRateLimit = new lidraughts.memo.RateLimit[IpAddress](
    credits = 5,
    duration = 1 minute,
    name = "setup post",
    key = "setup_post",
    whitelist = () => Env.lobby.whitelistIPSetting.get.value.map(IpAddress(_)),
    enforce = Env.api.Net.RateLimit
  )

  private[controllers] val PostExternalRateLimit = new lidraughts.memo.RateLimit[IpAddress](
    credits = 30,
    duration = 1 minute,
    name = "setup post external",
    key = "setup_post_external",
    enforce = Env.api.Net.RateLimit
  )

  def aiForm = Open { implicit ctx =>
    if (HTTPRequest isXhr ctx.req) {
      env.forms aiFilled get("fen").map(FEN) map { form =>
        html.setup.forms.ai(
          form,
          Env.draughtsnet.aiPerfApi.intRatings,
          form("fen").value flatMap ValidFen(draughts.variant.Standard, getBool("strict"))
        )
      }
    } else fuccess {
      Redirect(routes.Lobby.home + "#ai")
    }
  }

  def ai = process(env.forms.ai) { config => implicit ctx =>
    env.processor ai config
  }

  def friendForm(userId: Option[String]) = Open { implicit ctx =>
    if (HTTPRequest isXhr ctx.req)
      env.forms.friendFilled(get("fen").map(FEN), get("variant").flatMap(Variant.apply)) flatMap { form =>
        val fenVariant = form("fenVariant").value.flatMap(parseIntOption).flatMap(Variant.apply) | Standard
        val validFen = form("fen").value flatMap ValidFen(fenVariant, false)
        userId ?? UserRepo.named flatMap {
          case None => Ok(html.setup.forms.friend(form, none, none, validFen)).fuccess
          case Some(user) => Env.challenge.granter(ctx.me, user, none) map {
            case Some(denied) => BadRequest(lidraughts.challenge.ChallengeDenied.translated(denied))
            case None => Ok(html.setup.forms.friend(form, user.some, none, validFen))
          }
        }
      }
    else fuccess {
      Redirect(routes.Lobby.home + "#friend")
    }
  }

  def friend(userId: Option[String]) = OpenBody { implicit ctx =>
    implicit val req = ctx.body
    PostRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
      env.forms.friend(ctx).bindFromRequest.fold(
        err => negotiate(
          html = Lobby.renderHome(Results.BadRequest),
          api = _ => jsonFormError(err)
        ),
        config => userId ?? UserRepo.enabledById flatMap { destUser =>
          destUser ?? { Env.challenge.granter(ctx.me, _, config.perfType) } flatMap {
            case Some(denied) =>
              val message = lidraughts.challenge.ChallengeDenied.translated(denied)
              negotiate(
                html = BadRequest(html.site.message.challengeDenied(message)).fuccess,
                api = _ => BadRequest(jsonError(message)).fuccess
              )
            case None =>
              import lidraughts.challenge.Challenge._
              val challenge = lidraughts.challenge.Challenge.make(
                variant = config.variant,
                initialFen = config.variant.fromPosition ?? config.fen,
                fenVariant = config.variant.fromPosition ?? config.fenVariant,
                timeControl = config.makeClock map { c =>
                  TimeControl.Clock(c)
                } orElse config.makeDaysPerTurn.map {
                  TimeControl.Correspondence.apply
                } getOrElse TimeControl.Unlimited,
                mode = config.mode,
                color = config.color.name,
                challenger = (ctx.me, HTTPRequest sid req) match {
                  case (Some(user), _) => Right(user)
                  case (_, Some(sid)) => Left(sid)
                  case _ => Left("no_sid")
                },
                destUser = destUser,
                rematchOf = none,
                microMatch = config.microMatch
              )
              env.processor.saveFriendConfig(config) >>
                (Env.challenge.api create challenge) flatMap {
                  case true => negotiate(
                    html = fuccess(Redirect(routes.Round.watcher(challenge.id, "white"))),
                    api = _ => Challenge showChallenge challenge
                  )
                  case false => negotiate(
                    html = fuccess(Redirect(routes.Lobby.home)),
                    api = _ => fuccess(BadRequest(jsonError("Challenge not created")))
                  )
                }
          }
        }
      )
    }
  }

  def hookForm = Open { implicit ctx =>
    NoBot {
      if (HTTPRequest isXhr ctx.req) NoPlaybanOrCurrent {
        env.forms.hookFilled(timeModeString = get("time")) map { html.setup.forms.hook(_) }
      }
      else fuccess {
        Redirect(routes.Lobby.home + "#hook")
      }
    }
  }

  private def hookResponse(res: HookResult) = res match {
    case HookResult.Created(id) => Ok(Json.obj(
      "ok" -> true,
      "hook" -> Json.obj("id" -> id)
    )) as JSON
    case HookResult.Refused => BadRequest(jsonError("Game was not created"))
  }

  private val hookSaveOnlyResponse = Ok(Json.obj("ok" -> true))

  def hook(uid: String) = OpenBody { implicit ctx =>
    NoBot {
      implicit val req = ctx.body
      PostRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
        NoPlaybanOrCurrent {
          env.forms.hook(ctx).bindFromRequest.fold(
            jsonFormError,
            userConfig => {
              val config = userConfig withinLimits ctx.me
              //if (getBool("pool")) env.processor.saveHookConfig(config) inject hookSaveOnlyResponse
              //else
              (ctx.userId ?? Env.relation.api.fetchBlocking) flatMap {
                blocking =>
                  env.processor.hook(config, Uid(uid), HTTPRequest sid req, blocking) map hookResponse
              }
            }
          )
        }
      }
    }
  }

  def like(uid: String, gameId: String) = Open { implicit ctx =>
    NoBot {
      PostRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
        NoPlaybanOrCurrent {
          for {
            config <- env.forms.hookConfig
            game <- GameRepo game gameId
            blocking <- ctx.userId ?? Env.relation.api.fetchBlocking
            hookConfig = game.fold(config)(config.updateFrom)
            sameOpponents = game.??(_.userIds)
            hookResult <- env.processor.hook(hookConfig, Uid(uid), HTTPRequest sid ctx.req, blocking ++ sameOpponents)
          } yield hookResponse(hookResult)
        }
      }
    }
  }

  def filterForm = Open { implicit ctx =>
    env.forms.filterFilled map {
      case (form, filter) => html.setup.filter(form, filter)
    }
  }

  def filter = OpenBody { implicit ctx =>
    implicit val req = ctx.body
    env.forms.filter(ctx).bindFromRequest.fold[Fu[Result]](
      f => {
        controllerLogger.branch("setup").warn(f.errors.toString)
        BadRequest(()).fuccess
      },
      config => JsonOk(env.processor filter config inject config.render)
    )
  }

  def validateFen = Open { implicit ctx =>
    val v = get("variant").flatMap(Variant.apply) | Standard
    get("fen") flatMap ValidFen(v, getBool("strict")) match {
      case None => BadRequest.fuccess
      case Some(v) if getBool("kings") && v.tooManyKings => BadRequest.fuccess
      case Some(v) => Ok(html.board.bits.miniSpan(v.fen, v.boardSize, v.color)).fuccess
    }
  }

  def validateFenOk = Open { implicit ctx =>
    import lidraughts.i18n.{ I18nKeys => trans }
    val v = get("variant").flatMap(Variant.apply) | Standard
    val strict = getBool("strict")
    val fen = get("fen")
    fen flatMap ValidFen(v, strict) match {
      case None =>
        val errorText =
          if (fen.flatMap(draughts.format.Forsyth.makeBoard(v, _)).??(_.pieceCount) != 0) trans.invalidPosition.txt()
          else trans.invalidFen.txt()
        BadRequest("<p class=\"errortext\">" + errorText + "</p>").fuccess
      case Some(v) if getBool("kings") && v.tooManyKings => BadRequest("<p class=\"errortext\">" + trans.tooManyKings.txt() + "</p>").fuccess
      case Some(v) => Ok(html.board.bits.miniSpan(v.fen, v.boardSize, v.color)).fuccess
    }
  }

  private def process[A](form: Context => Form[A])(op: A => BodyContext[_] => Fu[Pov]) =
    OpenBody { implicit ctx =>
      PostRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
        implicit val req = ctx.body
        form(ctx).bindFromRequest.fold(
          err => negotiate(
            html = Lobby.renderHome(Results.BadRequest),
            api = _ => jsonFormError(err)
          ),
          config => op(config)(ctx) flatMap { pov =>
            negotiate(
              html = fuccess(redirectPov(pov)),
              api = apiVersion => Env.api.roundApi.player(pov, none, apiVersion) map { data =>
                Created(data) as JSON
              }
            )
          }
        )
      }
    }

  private[controllers] def redirectPov(pov: Pov)(implicit ctx: Context) = {
    val redir = Redirect(routes.Round.watcher(pov.gameId, "white"))
    if (ctx.isAuth) redir
    else redir withCookies LidraughtsCookie.cookie(
      AnonCookie.name,
      pov.playerId,
      maxAge = AnonCookie.maxAge.some,
      httpOnly = false.some
    )
  }
}
