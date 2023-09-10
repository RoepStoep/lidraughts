package controllers

import draughts.format.Forsyth.SituationPlus
import draughts.format.{ FEN, Forsyth }
import draughts.Situation
import draughts.variant.{ Variant, Standard, FromPosition }
import play.api.libs.json.Json
import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.game.{ GameRepo, Pov }
import lidraughts.i18n.I18nKeys
import lidraughts.pref.Pref.puzzleVariants
import lidraughts.round.Forecast.{ forecastStepJsonFormat, forecastJsonWriter }
import lidraughts.round.JsonView.WithFlags
import views._

object UserAnalysis extends LidraughtsController with TheftPrevention {

  def index = load("", Standard)

  def parse(arg: String) = arg.split("/", 2) match {
    case Array(key) => Variant(key) match {
      case Some(variant) => load("", variant)
      case _ => load(arg, Standard)
    }
    case Array(key, fen) => Variant.byKey get key match {
      case Some(variant) => load(fen, variant)
      case _ if fen == Standard.initialFen => load(arg, Standard)
      case _ => load(arg, FromPosition)
    }
    case _ => load("", Standard)
  }

  def load(urlFen: String, variant: Variant) = Open { implicit ctx =>
    val decodedFen: Option[FEN] = lidraughts.common.String.decodeUriPath(urlFen)
      .map(_.replace('_', ' ').trim).filter(_.nonEmpty)
      .orElse(get("fen")) map FEN.apply
    val pov = makePov(decodedFen, variant)
    val orientation = get("color").flatMap(draughts.Color.apply) | pov.color
    Env.api.roundApi.userAnalysisJson(pov, ctx.pref, decodedFen, orientation, owner = false, me = ctx.me) map { data =>
      Ok(html.board.userAnalysis(data, pov))
    }
  }

  def puzzleEditor = loadPuzzle("", Standard)

  def parsePuzzle(arg: String) = arg.split("/", 2) match {
    case Array(key) => Variant(key) match {
      case Some(variant) if puzzleVariants.contains(variant) => loadPuzzle("", variant)
      case _ => loadPuzzle(arg, Standard)
    }
    case Array(key, fen) => Variant.byKey get key match {
      case Some(variant) if puzzleVariants.contains(variant) => loadPuzzle(fen, variant)
      case _ => loadPuzzle(arg, Standard)
    }
    case _ => loadPuzzle("", Standard)
  }

  def loadPuzzle(urlFen: String, variant: Variant) = Secure(_.CreatePuzzles) { implicit ctx => me =>
    val decodedFen: Option[FEN] = lidraughts.common.String.decodeUriPath(urlFen)
      .map(_.replace("_", " ").trim).filter(_.nonEmpty)
      .orElse(get("fen")) map FEN.apply
    val pov = makePov(decodedFen, variant)
    val orientation = get("color").flatMap(draughts.Color.apply) | pov.color
    Env.api.roundApi.puzzleEditorJson(pov, ctx.pref, decodedFen, orientation, owner = false, me = me.some) map { data =>
      Ok(html.board.puzzleEditor(data, pov))
    }
  }

  private[controllers] def makePov(fen: Option[FEN], variant: Variant): Pov = makePov {
    fen.filter(_.value.nonEmpty).flatMap { f =>
      Forsyth.<<<@(variant, f.value)
    } | SituationPlus(Situation(variant), 1)
  }

  private[controllers] def makePov(from: SituationPlus): Pov = Pov(
    lidraughts.game.Game.make(
      draughts = draughts.DraughtsGame(
        situation = from.situation,
        turns = from.turns
      ),
      whitePlayer = lidraughts.game.Player.make(draughts.White, none),
      blackPlayer = lidraughts.game.Player.make(draughts.Black, none),
      mode = draughts.Mode.Casual,
      source = lidraughts.game.Source.Api,
      pdnImport = None
    ).withId("synthetic"),
    from.situation.color
  )

  def game(id: String, color: String) = Open { implicit ctx =>
    OptionFuResult(GameRepo game id) { g =>
      Env.round.proxy updateIfPresent g flatMap { game =>
        val pov = Pov(game, draughts.Color(color == "white"))
        negotiate(
          html =
            if (game.replayable) Redirect(routes.Round.watcher(game.id, color)).fuccess
            else {
              val owner = isMyPov(pov)
              for {
                initialFen <- GameRepo initialFen game.id
                data <- Env.api.roundApi.userAnalysisJson(pov, ctx.pref, initialFen, pov.color, owner = owner, me = ctx.me)
              } yield NoCache(Ok(html.board.userAnalysis(data, pov, withForecast = owner && !pov.game.synthetic && pov.game.playable)))
            },
          api = apiVersion => mobileAnalysis(pov, apiVersion)
        )
      }
    }
  }

  private def mobileAnalysis(pov: Pov, apiVersion: lidraughts.common.ApiVersion)(implicit ctx: Context): Fu[Result] =
    GameRepo initialFen pov.gameId flatMap { initialFen =>
      val owner = isMyPov(pov)
      Game.preloadUsers(pov.game) zip
        (Env.analyse.analyser get pov.game) zip
        Env.game.crosstableApi(pov.game) zip
        Env.bookmark.api.exists(pov.game, ctx.me) flatMap {
          case _ ~ analysis ~ crosstable ~ bookmarked =>
            import lidraughts.game.JsonView.crosstableWrites
            Env.api.roundApi.review(pov, apiVersion,
              tv = none,
              analysis,
              initialFenO = initialFen.some,
              withFlags = WithFlags(division = true, opening = true, clocks = true, movetimes = true),
              owner = owner) map { data =>
                Ok(data.add("crosstable", crosstable))
              }
        }
    }

  def socket(apiVersion: Int) = SocketOption { implicit ctx =>
    getSocketUid("sri") ?? { uid =>
      Env.analyse.socketHandler.join(uid, ctx.me, apiVersion) map some
    }
  }

  // XHR only
  def pdn = OpenBody { implicit ctx =>
    implicit val req = ctx.body
    Env.importer.forms.importForm.bindFromRequest.fold(
      jsonFormError,
      data => Env.importer.importer.inMemory(data).fold(
        err => BadRequest(jsonError(err.shows)).fuccess, {
          case (game, fen) =>
            val pov = Pov(game, draughts.White)
            Env.api.roundApi.userAnalysisJson(pov, ctx.pref, initialFen = fen, pov.color, owner = false, me = ctx.me, iteratedCapts = true) map { data =>
              Ok(data)
            }
        }
      )
    ).map(_ as JSON)
  }

  def pdnToPuzzle = SecureBody(_.CreatePuzzles) { implicit ctx => me =>
    implicit val req = ctx.body
    Env.importer.forms.importForm.bindFromRequest.fold(
      failure => BadRequest(errorsAsJson(failure)).fuccess,
      data => Env.importer.importer.inMemory(data).fold(
        err => BadRequest(jsonError(err.shows)).fuccess, {
          case (game, fen) =>
            val color = fen match {
              case Some(f) => if (f.value.head.toLower == 'w') draughts.White else draughts.Black
              case _ => draughts.White
            }
            val pov = Pov(game, color)
            Env.api.roundApi.puzzleEditorJson(pov, ctx.pref, initialFen = fen, pov.color, owner = false, me = me.some, iteratedCapts = true) map { data =>
              Ok(html.board.puzzleEditor(data, pov))
            }
        }
      )
    )
  }

  def forecasts(fullId: String) = AuthBody(BodyParsers.parse.json) { implicit ctx => me =>
    import lidraughts.round.Forecast
    OptionFuResult(Env.round.proxy pov fullId) { pov =>
      if (isTheft(pov)) fuccess(theftResponse)
      else ctx.body.body.validate[Forecast.Steps].fold(
        err => BadRequest(err.toString).fuccess,
        forecasts => Env.round.forecastApi.save(pov, forecasts) >>
          Env.round.forecastApi.loadForDisplay(pov) map {
            case None => Ok(Json.obj("none" -> true))
            case Some(fc) => Ok(Json toJson fc) as JSON
          } recover {
            case Forecast.OutOfSync => Ok(Json.obj("reload" -> true))
          }
      )
    }
  }

  def forecastsOnMyTurn(fullId: String, uci: String) = AuthBody(BodyParsers.parse.json) { implicit ctx => me =>
    import lidraughts.round.Forecast
    OptionFuResult(Env.round.proxy pov fullId) { pov =>
      if (isTheft(pov)) fuccess(theftResponse)
      else ctx.body.body.validate[Forecast.Steps].fold(
        err => BadRequest(err.toString).fuccess,
        forecasts => {
          val wait = 250 + (Forecast maxPlies forecasts min 10) * 50
          Env.round.forecastApi.playAndSave(pov, uci, forecasts) >>
            Env.current.scheduler.after(wait.millis) {
              Ok(Json.obj("reload" -> true))
            }
        }
      )
    }
  }

  def help = Open { implicit ctx =>
    Ok(html.analyse.help(getBool("study"))).fuccess
  }
}
