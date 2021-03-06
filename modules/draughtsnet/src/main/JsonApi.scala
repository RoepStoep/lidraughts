package lidraughts.draughtsnet

import org.joda.time.DateTime
import play.api.libs.json._

import draughts.format.{ Uci, FEN, Forsyth }
import draughts.variant.Variant

import lidraughts.common.{ Maths, IpAddress }
import lidraughts.draughtsnet.{ Work => W }
import lidraughts.tree.Eval.JsonHandlers._
import lidraughts.tree.Eval.{ Cp, Win }

object JsonApi {

  sealed trait Request {
    val draughtsnet: Request.Draughtsnet
    val scan: Request.Engine

    def instance(ip: IpAddress) = Client.Instance(
      draughtsnet.version,
      draughtsnet.python | Client.Python(""),
      Client.Engines(
        scan = Client.Engine(scan.name)
      ),
      ip,
      DateTime.now
    )
  }

  object Request {

    sealed trait Result

    case class Draughtsnet(
        version: Client.Version,
        python: Option[Client.Python],
        apikey: Client.Key
    )

    sealed trait Engine {
      def name: String
    }

    case class BaseEngine(name: String) extends Engine

    case class FullEngine(
        name: String,
        options: EngineOptions
    ) extends Engine

    case class EngineOptions(
        threads: Option[String],
        hash: Option[String]
    ) {
      def threadsInt = threads flatMap parseIntOption
      def hashInt = hash flatMap parseIntOption
    }

    case class Acquire(
        draughtsnet: Draughtsnet,
        scan: BaseEngine
    ) extends Request

    case class PostMove(
        draughtsnet: Draughtsnet,
        scan: BaseEngine,
        move: MoveResult
    ) extends Request with Result

    case class MoveResult(bestmove: String, taken: String) {
      def uci: Option[Uci] = Uci(bestmove)
    }

    case class PostCommentary(
        draughtsnet: Draughtsnet,
        scan: BaseEngine,
        commentary: Evaluation
    ) extends Request with Result

    case class PostAnalysis(
        draughtsnet: Draughtsnet,
        scan: FullEngine,
        analysis: List[Option[Evaluation.OrSkipped]]
    ) extends Request with Result {

      def completeOrPartial =
        if (analysis.headOption.??(_.isDefined)) CompleteAnalysis(draughtsnet, scan, analysis.flatten)
        else PartialAnalysis(draughtsnet, scan, analysis)
    }

    case class CompleteAnalysis(
        draughtsnet: Draughtsnet,
        scan: FullEngine,
        analysis: List[Evaluation.OrSkipped]
    ) {

      def evaluations = analysis.collect { case Right(e) => e }

      def medianNodes = Maths.median {
        evaluations
          .filterNot(_.winFound)
          .filterNot(_.deadDraw)
          .flatMap(_.nodes)
      }

      def strong = medianNodes.fold(true)(_ > Evaluation.acceptableNodes)
      def weak = !strong
    }

    case class PartialAnalysis(
        draughtsnet: Draughtsnet,
        scan: FullEngine,
        analysis: List[Option[Evaluation.OrSkipped]]
    )

    case class Evaluation(
        pv: List[Uci],
        score: Evaluation.Score,
        time: Option[Int],
        nodes: Option[Int],
        nps: Option[Int],
        depth: Option[Int]
    ) {
      val cappedNps = nps.map(_ min Evaluation.npsCeil)

      val cappedPv = pv take lidraughts.analyse.Info.LineMaxPlies

      def isWin = score.win has Win(0)
      def winFound = score.win.isDefined
      def deadDraw = score.cp has Cp(0)
    }

    object Evaluation {

      object Skipped

      type OrSkipped = Either[Skipped.type, Evaluation]

      case class Score(cp: Option[Cp], win: Option[Win]) {
        def invert = copy(cp.map(_.invert), win.map(_.invert))
        def invertIf(cond: Boolean) = if (cond) invert else this
      }

      val npsCeil = 10 * 1000 * 1000

      val desiredNodes = 2 * 1000 * 1000
      val acceptableNodes = desiredNodes * 0.9
    }
  }

  case class Game(
      game_id: String,
      position: FEN,
      variant: Variant,
      moves: String
  )

  def fromGame(g: W.Game) = {
    val initialFen = g.initialFen.fold(g.variant.initialFen)(_.value)
    val moves = if (g.moves.nonEmpty) draughts.Replay.exportScanMoves(g.moves, initialFen, g.variant, if (g.studyId.isDefined) s"Study ${g.studyId} ${g.id}" else s"Game ${g.id}", g.finalSquare) mkString " " else zero[String]
    Game(
      game_id = if (g.studyId.isDefined) "" else g.id,
      position = FEN(Forsyth.exportScanPosition(Forsyth << initialFen)),
      variant = g.variant,
      moves = moves
    )
  }

  sealed trait Work {
    val id: String
    val game: Game
  }

  case class Move(
      id: String,
      level: Int,
      game: Game,
      clock: Option[Work.Clock]
  ) extends Work

  case class Commentary(
      id: String,
      game: Game,
      nodes: Int
  ) extends Work

  case class Analysis(
      id: String,
      game: Game,
      nodes: Int,
      skipPositions: List[Int]
  ) extends Work

  def moveFromWork(m: Work.Move) = Move(m.id.value, m.level, fromGame(m.game), m.clock)

  def commentaryFromWork(nodes: Int)(m: Work.Commentary) = Commentary(
    id = m.id.value,
    game = fromGame(m.game),
    nodes = nodes
  )

  def analysisFromWork(nodes: Int)(m: Work.Analysis) = Analysis(
    id = m.id.value,
    game = fromGame(m.game),
    nodes = nodes,
    skipPositions = m.skipPositions
  )

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientVersionReads = Reads.of[String].map(new Client.Version(_))
    implicit val ClientPythonReads = Reads.of[String].map(new Client.Python(_))
    implicit val ClientKeyReads = Reads.of[String].map(new Client.Key(_))
    implicit val EngineOptionsReads = Json.reads[Request.EngineOptions]
    implicit val BaseEngineReads = Json.reads[Request.BaseEngine]
    implicit val FullEngineReads = Json.reads[Request.FullEngine]
    implicit val DraughtsnetReads = Json.reads[Request.Draughtsnet]
    implicit val AcquireReads = Json.reads[Request.Acquire]
    implicit val MoveResultReads = Json.reads[Request.MoveResult]
    implicit val PostMoveReads = Json.reads[Request.PostMove]
    implicit val ScoreReads = Json.reads[Request.Evaluation.Score]
    implicit val uciListReads = Reads.of[String] map { str =>
      ~Uci.readList(str)
    }

    implicit val EvaluationReads: Reads[Request.Evaluation] = (
      (__ \ "pv").readNullable[List[Uci]].map(~_) and
      (__ \ "score").read[Request.Evaluation.Score] and
      (__ \ "time").readNullable[Int] and
      (__ \ "nodes").readNullable[Long].map(Maths.toInt) and
      (__ \ "nps").readNullable[Long].map(Maths.toInt) and
      (__ \ "depth").readNullable[Int]
    )(Request.Evaluation.apply _)
    implicit val EvaluationOptionReads = Reads[Option[Request.Evaluation.OrSkipped]] {
      case JsNull => JsSuccess(None)
      case obj =>
        if (~(obj boolean "skipped")) JsSuccess(Left(Request.Evaluation.Skipped).some)
        else EvaluationReads reads obj map Right.apply map some
    }
    implicit val PostAnalysisReads: Reads[Request.PostAnalysis] = Json.reads[Request.PostAnalysis]
    implicit val PostCommentReads = Json.reads[Request.PostCommentary]
  }

  object writers {
    implicit val VariantWrites = Writes[Variant] { v => JsString(v.key) }
    implicit val FENWrites = Writes[FEN] { fen => JsString(fen.value) }
    implicit val GameWrites: Writes[Game] = Json.writes[Game]
    implicit val ClockWrites: Writes[Work.Clock] = Json.writes[Work.Clock]
    implicit val WorkIdWrites = Writes[Work.Id] { id => JsString(id.value) }
    implicit val WorkWrites = OWrites[Work] { work =>
      (work match {
        case a: Analysis => Json.obj(
          "work" -> Json.obj("type" -> "analysis", "id" -> a.id),
          "nodes" -> a.nodes,
          "skipPositions" -> a.skipPositions
        )
        case c: Commentary => Json.obj(
          "work" -> Json.obj(
            "type" -> "commentary",
            "id" -> c.id
          )
        )
        case m: Move => Json.obj(
          "work" -> Json.obj(
            "type" -> "move",
            "id" -> m.id,
            "level" -> m.level,
            "clock" -> m.clock
          )
        )
      }) ++ Json.toJson(work.game).as[JsObject]
    }
  }
}
