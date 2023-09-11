package lidraughts.api

import play.api.libs.json._
import draughts.format.FEN
import lidraughts.analyse.{ Analysis, JsonView => analysisJson }
import lidraughts.common.ApiVersion
import lidraughts.game.{ Game, GameRepo, Pov }
import lidraughts.pref.Pref
import lidraughts.round.JsonView.WithFlags
import lidraughts.round.{ Forecast, JsonView, logger }
import lidraughts.security.Granter
import lidraughts.simul.Simul
import lidraughts.swiss.{ GameView => SwissView }
import lidraughts.tournament.{ GameView => TourView }
import lidraughts.tree.Node.partitionTreeJsonWriter
import lidraughts.user.User

private[api] final class RoundApi(
    jsonView: JsonView,
    noteApi: lidraughts.round.NoteApi,
    forecastApi: lidraughts.round.ForecastApi,
    bookmarkApi: lidraughts.bookmark.BookmarkApi,
    swissApi: lidraughts.swiss.SwissApi,
    tourApi: lidraughts.tournament.TournamentApi,
    getSimul: Simul.ID => Fu[Option[Simul]],
    getTeamName: lidraughts.team.Team.ID => Option[String],
    getLightUser: lidraughts.common.LightUser.GetterSync
) {

  def player(pov: Pov, tour: Option[TourView], apiVersion: ApiVersion)(implicit ctx: Context): Fu[JsObject] =
    GameRepo.initialFen(pov.game).flatMap { initialFen =>
      jsonView.playerJson(pov, ctx.pref, apiVersion, ctx.me,
        withFlags = WithFlags(blurs = ctx.me ?? Granter(_.ViewBlurs)),
        initialFen = initialFen,
        nvui = ctx.blind,
        isWfd = pov.game.isWfd || ~tour.map(_.tour.isWfd)) zip
        (pov.game.simulId ?? getSimul) zip
        swissApi.gameView(pov) zip
        (ctx.me.ifTrue(ctx.isMobileApi) ?? (me => noteApi.get(pov.gameId, me.id))) zip
        forecastApi.loadForDisplay(pov) zip
        bookmarkApi.exists(pov.game, ctx.me) map {
          case json ~ simulOption ~ swissOption ~ note ~ forecast ~ bookmarked => (
            withTournament(pov, tour) _ compose
            withSimul(pov, simulOption, true) _ compose
            withSwiss(swissOption) _ compose
            withSteps(pov, initialFen) _ compose
            withNote(note) _ compose
            withBookmark(bookmarked) _ compose
            withForecastCount(forecast.map(_.steps.size)) _
          )(json)
        }
    }.mon(_.round.api.player)

  def watcher(pov: Pov, tour: Option[TourView], apiVersion: ApiVersion, tv: Option[lidraughts.round.OnTv],
    initialFenO: Option[Option[FEN]] = None)(implicit ctx: Context): Fu[JsObject] =
    initialFenO.fold(GameRepo initialFen pov.game)(fuccess).flatMap { initialFen =>
      jsonView.watcherJson(pov, ctx.pref, apiVersion, ctx.me, tv,
        initialFen = initialFen,
        withFlags = WithFlags(blurs = ctx.me ?? Granter(_.ViewBlurs)),
        isWfd = pov.game.isWfd || ~tour.map(_.tour.isWfd)) zip
        (pov.game.simulId ?? getSimul) zip
        swissApi.gameView(pov) zip
        (ctx.me.ifTrue(ctx.isMobileApi) ?? (me => noteApi.get(pov.gameId, me.id))) zip
        bookmarkApi.exists(pov.game, ctx.me) map {
          case json ~ simulOption ~ swissOption ~ note ~ bookmarked => (
            withTournament(pov, tour) _ compose
            withSimul(pov, simulOption, false) _ compose
            withSwiss(swissOption) _ compose
            withNote(note) _ compose
            withBookmark(bookmarked)_ compose
            withSteps(pov, initialFen) _
          )(json)
        }
    }.mon(_.round.api.watcher)

  def review(
    pov: Pov,
    apiVersion: ApiVersion,
    tv: Option[lidraughts.round.OnTv] = None,
    analysis: Option[Analysis] = None,
    initialFenO: Option[Option[FEN]] = None,
    withFlags: WithFlags,
    owner: Boolean = false
  )(implicit ctx: Context): Fu[JsObject] =
    initialFenO.fold(GameRepo initialFen pov.game)(fuccess).flatMap { initialFen =>
      tourApi.gameView.analysis(pov.game) flatMap { tour =>
        jsonView.watcherJson(pov, ctx.pref, apiVersion, ctx.me, tv,
          initialFen = initialFen,
          withFlags = withFlags.copy(blurs = ctx.me ?? Granter(_.ViewBlurs)),
          isWfd = pov.game.isWfd || ~tour.map(_.tour.isWfd)) zip
          (pov.game.simulId ?? getSimul) zip
          swissApi.gameView(pov) zip
          ctx.userId.ifTrue(ctx.isMobileApi).?? { noteApi.get(pov.gameId, _) } zip
          (owner.??(forecastApi loadForDisplay pov)) zip
          bookmarkApi.exists(pov.game, ctx.me) map {
            case json ~ simulOption ~ swissOption ~ note ~ fco ~ bookmarked => (
              withTournament(pov, tour) _ compose
              withSimul(pov, simulOption, false) _ compose
              withSwiss(swissOption) _ compose
              withNote(note) _ compose
              withBookmark(bookmarked) _ compose
              withTree(pov, analysis, initialFen, withFlags, pov.game.metadata.pdnImport.isDefined) _ compose
              withAnalysis(pov.game, analysis, ctx.me ?? Granter(_.Hunter)) _ compose
              withForecast(pov, owner, fco) _
            )(json)
          }
      }
    }.mon(_.round.api.watcher)

  def embed(pov: Pov, apiVersion: ApiVersion,
    analysis: Option[Analysis] = None,
    initialFenO: Option[Option[FEN]] = None,
    withFlags: WithFlags): Fu[JsObject] =
    initialFenO.fold(GameRepo initialFen pov.game)(fuccess).flatMap { initialFen =>
      jsonView.watcherJson(pov, Pref.default, apiVersion, none, none,
        initialFen = initialFen,
        withFlags = withFlags) map { json =>
        (
          withTree(pov, analysis, initialFen, withFlags)_ compose
          withAnalysis(pov.game, analysis)_
        )(json)
      }
    }.mon(_.round.api.embed)

  def userAnalysisJson(pov: Pov, pref: Pref, initialFen: Option[FEN], orientation: draughts.Color, owner: Boolean, me: Option[User], iteratedCapts: Boolean = false) =
    owner.??(forecastApi loadForDisplay pov).map { fco =>
      withForecast(pov, owner, fco) {
        withTree(pov, analysis = none, initialFen, WithFlags(opening = true), iteratedCapts) {
          jsonView.userAnalysisJson(pov, pref, initialFen, orientation, owner = owner, me = me)
        }
      }
    }

  def puzzleEditorJson(pov: Pov, pref: Pref, initialFen: Option[FEN], orientation: draughts.Color, owner: Boolean, me: Option[User], iteratedCapts: Boolean = false) =
    owner.??(forecastApi loadForDisplay pov).map { fco =>
      withForecast(pov, owner, fco) {
        withTree(pov, analysis = none, initialFen, WithFlags(opening = true), iteratedCapts) {
          jsonView.puzzleEditorJson(pov, pref, initialFen, orientation, owner = owner, me = me)
        }
      }
    }

  def freeStudyJson(pov: Pov, pref: Pref, initialFen: Option[FEN], orientation: draughts.Color, me: Option[User]) =
    withTree(pov, analysis = none, initialFen, WithFlags(opening = true))(
      jsonView.userAnalysisJson(pov, pref, initialFen, orientation, owner = false, me = me)
    )

  private def withTree(pov: Pov, analysis: Option[Analysis], initialFen: Option[FEN], withFlags: WithFlags, iteratedCapts: Boolean = false)(obj: JsObject) =
    obj + ("treeParts" -> partitionTreeJsonWriter.writes(lidraughts.round.TreeBuilder(
      id = pov.gameId,
      pdnmoves = pov.game.pdnMoves,
      variant = pov.game.variant,
      analysis = analysis,
      initialFen = initialFen | FEN(pov.game.variant.initialFen),
      withFlags = withFlags,
      clocks = withFlags.clocks ?? pov.game.bothClockStates(iteratedCapts),
      iteratedCapts
    )))

  private def withSteps(pov: Pov, initialFen: Option[FEN])(obj: JsObject) =
    obj + ("steps" -> lidraughts.round.StepBuilder(
      id = pov.gameId,
      pdnmoves = pov.game.pdnMoves,
      variant = pov.game.variant,
      initialFen = initialFen.fold(pov.game.variant.initialFen)(_.value)
    ))

  private def withNote(note: String)(json: JsObject) =
    if (note.isEmpty) json else json + ("note" -> JsString(note))

  private def withBookmark(v: Boolean)(json: JsObject) =
    json.add("bookmarked" -> v)

  private def withForecastCount(count: Option[Int])(json: JsObject) =
    count.filter(0 !=).fold(json) { c =>
      json + ("forecastCount" -> JsNumber(c))
    }

  private def withForecast(pov: Pov, owner: Boolean, fco: Option[Forecast])(json: JsObject) =
    if (pov.game.forecastable && owner) json + (
      "forecast" -> {
        if (pov.forecastable) fco.fold[JsValue](Json.obj("none" -> true)) { fc =>
          import Forecast.forecastJsonWriter
          Json toJson fc
        }
        else Json.obj("onMyTurn" -> true)
      }
    )
    else json

  private def withAnalysis(g: Game, o: Option[Analysis], modStats: Boolean = false)(json: JsObject) =
    json.add("analysis", o.map { a => analysisJson.bothPlayers(g, a, modStats) })

  private def withTournament(pov: Pov, viewO: Option[TourView])(json: JsObject) =
    json.add("tournament" -> viewO.map { v =>
      Json.obj(
        "id" -> v.tour.id,
        "name" -> v.tour.name,
        "running" -> v.tour.isStarted
      ).add("secondsToFinish" -> v.tour.isStarted.option(v.tour.secondsToFinish))
        .add("berserkable" -> v.tour.isStarted.option(v.tour.berserkable))
        // mobile app API BC / should use game.expiration instead
        .add("nbSecondsForFirstMove" -> v.tour.isStarted.option {
          pov.game.timeForFirstMove.toSeconds
        })
        .add("ranks" -> v.ranks.map { r =>
          Json.obj(
            "white" -> r.whiteRank,
            "black" -> r.blackRank
          )
        })
        .add("top", v.top.map {
          lidraughts.tournament.JsonView.top(_, getLightUser)
        })
        .add("team", v.teamVs.map(_.teams(pov.color)) map { id =>
          Json.obj("name" -> getTeamName(id))
        })
        .add("isWfd" -> v.tour.isWfd.option(true))
    })

  def withSwiss(sv: Option[SwissView])(json: JsObject) =
    json.add("swiss" -> sv.map { s =>
      Json
        .obj(
          "id" -> s.swiss.id.value,
          "running" -> s.swiss.isStarted
        )
        .add("ranks" -> s.ranks.map { r =>
          Json.obj(
            "white" -> r.whiteRank,
            "black" -> r.blackRank
          )
        })
    })

  private def withSimul(pov: Pov, simulOption: Option[Simul], player: Boolean)(json: JsObject) =
    json.add("simul", simulOption.map { simul =>
      Json.obj(
        "id" -> simul.id,
        "hostId" -> simul.hostId,
        "name" -> simul.name,
        "nbPlaying" -> simul.ongoing
      ).add("timeOutUntil" -> pov.game.isWithinTimeOut ?? pov.game.metadata.timeOutUntil)
        .add("isUnique" -> simul.isUnique.option(true))
        .add("noAssistance" -> simul.spotlight.flatMap(_.noAssistance).ifTrue(player))
    })
}
