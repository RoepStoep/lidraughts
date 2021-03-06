package lidraughts.mod

import play.api.libs.json._

import draughts.format.FEN
import lidraughts.analyse.{ Analysis, AnalysisRepo }
import lidraughts.evaluation._
import lidraughts.game.JsonView.blursWriter
import lidraughts.game.{ Game, GameRepo }
import lidraughts.user.User

final class JsonView(
    assessApi: AssessApi,
    relationApi: lidraughts.relation.RelationApi,
    reportApi: lidraughts.report.ReportApi,
    userJson: lidraughts.user.JsonView
) {

  def apply(user: User): Fu[Option[JsObject]] =
    assessApi.getPlayerAggregateAssessmentWithGames(user.id) flatMap {
      _ ?? {
        case PlayerAggregateAssessment.WithGames(pag, games) => for {
          gamesWithFen <- GameRepo withInitialFens games.filter(_.clockHistory.isDefined)
          moreGames <- GameRepo.extraGamesForIrwin(user.id, 25) map {
            _.filter { g => !games.exists(_.id == g.id) } take 20
          }
          moreGamesWithFen <- GameRepo withInitialFens moreGames
          allGamesWithFen = gamesWithFen ::: moreGamesWithFen
          analysis <- AnalysisRepo byIds allGamesWithFen.map(_._1.id)
          allGamesWithFenAndAnalysis = allGamesWithFen zip analysis map {
            case ((game, fen), analysis) => (game, fen, analysis)
          }
          reportScore <- reportApi.currentCheatScore(lidraughts.report.Suspect(user))
        } yield Json.obj(
          "user" -> userJson(user),
          "assessment" -> pag,
          "games" -> JsObject(allGamesWithFenAndAnalysis.map { g =>
            g._1.id -> {
              gameWithFenWrites.writes(g) ++ Json.obj(
                "color" -> g._1.player(user).map(_.color.name)
              )
            }
          })
        ).add("reportScore" -> reportScore.map(_.value)).some
      }
    }

  import lidraughts.user.JsonView.modWrites

  private implicit val playerFlagsWrites = OWrites[PlayerFlags] { f =>
    Json.obj()
      .add("ser" -> f.suspiciousErrorRate)
      .add("aha" -> f.alwaysHasAdvantage)
      .add("hbr" -> f.highBlurRate)
      .add("mbr" -> f.moderateBlurRate)
      .add("cmt" -> f.moderatelyConsistentMoveTimes)
      .add("nfm" -> f.noFastMoves)
      .add("sha" -> f.suspiciousHoldAlert)
  }
  private implicit val gameAssWrites = Writes[GameAssessment] { a =>
    JsNumber(a.id)
  }
  private implicit val playerAssWrites = Json.writes[PlayerAssessment]
  private implicit val playerAggAssWrites = OWrites[PlayerAggregateAssessment] { a =>
    Json.obj(
      "user" -> a.user,
      "relatedCheaters" -> JsArray()
    )
  }

  private implicit val gameWithFenWrites = OWrites[(Game, Option[FEN], Option[Analysis])] {
    case (g, fen, analysis) => Json.obj(
      "initialFen" -> fen.map(_.value),
      // "createdAt" -> g.createdAt.getDate,
      "pdn" -> g.pdnMoves.mkString(" "),
      "variant" -> g.variant.exotic.option(g.variant.key),
      "emts" -> g.clockHistory.isDefined ?? g.moveTimes.map(_.map(_.centis)),
      "blurs" -> Json.obj(
        "white" -> g.whitePlayer.blurs,
        "black" -> g.blackPlayer.blurs
      ),
      "analysis" -> analysis.map { a =>
        JsArray(a.infos.map { info =>
          info.cp.map { cp => Json.obj("cp" -> cp.value) } orElse
            info.win.map { win => Json.obj("win" -> win.value) } getOrElse
            JsNull
        })
      }
    ).noNull
  }
}

object JsonView {

  private[mod] implicit val modlogWrites: Writes[Modlog] = Json.writes[Modlog]
}
