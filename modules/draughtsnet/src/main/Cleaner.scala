package lidraughts.draughtsnet

import org.joda.time.DateTime
import reactivemongo.bson._
import scala.concurrent.duration._

import lidraughts.db.BSON.BSONJodaDateTimeHandler
import lidraughts.db.dsl._

private final class Cleaner(
    repo: DraughtsnetRepo,
    moveDb: MoveDB,
    commentDb: CommentDB,
    analysisColl: Coll,
    monitor: Monitor,
    scheduler: lidraughts.common.Scheduler
) {

  import BSONHandlers._

  private def analysisTimeout(plies: Int) = plies * 6.seconds + 3.seconds
  private def analysisTimeoutBase = analysisTimeout(20)

  private def durationAgo(d: FiniteDuration) = DateTime.now.minusSeconds(d.toSeconds.toInt)

  private def cleanCommentary: Unit = commentDb.clean map { moves =>
    moves foreach { comment =>
      logger.info(s"Timeout comment $comment")
      comment.acquired foreach { ack =>
        logger.info(s"Timeout acquired ${ack.userId}")
      }
    }
  }

  private def cleanMoves: Unit = moveDb.clean map { moves =>
    moves foreach { move =>
      logger.info(s"Timeout move $move")
      move.acquired foreach { ack =>
        Monitor.timeout(move, ack.userId)
      }
    }
  }

  private def cleanAnalysis: Funit = analysisColl.find(BSONDocument(
    "acquired.date" -> BSONDocument("$lt" -> durationAgo(analysisTimeoutBase))
  )).sort(BSONDocument("acquired.date" -> 1)).cursor[Work.Analysis]().gather[List](100).flatMap {
    _.filter { ana =>
      ana.acquiredAt.??(_ isBefore durationAgo(analysisTimeout(ana.nbMoves)))
    }.map { ana =>
      repo.updateOrGiveUpAnalysis(ana.timeout) >>-
        logger.info(s"Timeout analysis $ana") >>-
        ana.acquired.foreach { ack => Monitor.timeout(ana, ack.userId) }
    }.sequenceFu.void
  }

  scheduler.effect(3 seconds, "draughtsnet clean moves")(cleanMoves)
  scheduler.effect(5 seconds, "draughtsnet clean commentary")(cleanCommentary)
  scheduler.effect(10 seconds, "draughtsnet clean analysis")(cleanAnalysis)
}
