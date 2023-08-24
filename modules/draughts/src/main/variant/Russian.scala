package draughts
package variant

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.collection.mutable.ArrayBuffer

case object Russian extends Variant(
  id = 11,
  gameType = 25,
  key = "russian",
  name = "Russian",
  shortName = "Russian",
  title = "Choice of capture, promotion during a multi-capture.",
  standardInitialPosition = false,
  boardSize = Board.D64
) {

  val pieces = Variant.symmetricThreeRank(Vector(Man, Man, Man, Man), boardSize)
  val initialFen = "W:W21,22,23,24,25,26,27,28,29,30,31,32:B1,2,3,4,5,6,7,8,9,10,11,12:H0:F1"
  val startingPosition = StartingPosition("---", initialFen, "", "Initial position".some)
  override val openingTables = List(OpeningTable.tableFmjd, OpeningTable.tableIDFBasic)

  def captureDirs = Standard.captureDirs
  def moveDirsColor = Standard.moveDirsColor
  def moveDirsAll = Standard.moveDirsAll

  override def validMoves(situation: Situation, finalSquare: Boolean = false): Map[Pos, List[Move]] = {
    val captures: Map[Pos, List[Move]] = situation.actors.collect {
      case actor if actor.getCaptures(finalSquare).nonEmpty =>
        actor.pos -> actor.getCaptures(finalSquare)
    }(breakOut)

    if (captures.nonEmpty) captures
    else situation.actors.collect {
      case actor if actor.noncaptures.nonEmpty =>
        actor.pos -> actor.noncaptures
    }(breakOut)
  }

  override def shortRangeCaptures(actor: Actor, finalSquare: Boolean): List[Move] = {
    val buf = new ArrayBuffer[Move]
    val color = actor.color

    def walkCaptures(walkDir: Direction, curBoard: Board, curPos: PosMotion, firstSquare: Option[PosMotion], firstBoard: Option[Board], allSquares: List[PosMotion], allTaken: List[PosMotion]): Int =
      walkDir._2(curPos).fold(0) {
        nextPos =>
          curBoard(nextPos) match {
            case Some(captPiece) if captPiece.isNot(color) && !captPiece.isGhost =>
              walkDir._2(nextPos) match {
                case Some(landingPos) if curBoard(landingPos).isEmpty =>
                  val takingBoard = curBoard.takingUnsafe(curPos, landingPos, actor.piece, nextPos, captPiece)
                  val promotion = promotablePos(landingPos, color)
                  val boardAfter = if (promotion) takingBoard.promote(landingPos).getOrElse(takingBoard) else takingBoard
                  val promoted = if (promotion) Some(King) else None
                  val newSquares = landingPos :: allSquares
                  val newTaken = nextPos :: allTaken
                  val opposite = Variant.oppositeDirs(walkDir._1)
                  val newDest = if (firstSquare.isDefined) firstSquare else landingPos.some
                  val newBoard = if (firstBoard.isDefined) firstBoard else boardAfter.some
                  val extraCaptures = captureDirs.foldLeft(0) {
                    case (total, captDir) =>
                      if (captDir._1 == opposite) total
                      else {
                        total + promotion.fold(
                          innerLongRangeCaptures(buf, actor, boardAfter, landingPos, captDir, finalSquare, newDest, newBoard, newSquares, newTaken, promoted),
                          walkCaptures(captDir, boardAfter, landingPos, newDest, newBoard, newSquares, newTaken)
                        )
                      }
                  }
                  if (extraCaptures == 0) {
                    val newMove =
                      if (finalSquare) actor.move(landingPos, boardAfter.withoutGhosts, newSquares, newTaken, promoted)
                      else actor.move(firstSquare.getOrElse(landingPos), firstBoard.getOrElse(boardAfter), newSquares, newTaken, promoted)
                    buf += newMove
                  }
                  extraCaptures + 1
                case _ => 0
              }
            case _ => 0
          }
      }

    captureDirs.foreach {
      walkCaptures(_, actor.board, actor.pos, None, None, Nil, Nil)
    }
    buf.toList
  }

  override def longRangeCaptures(actor: Actor, finalSquare: Boolean): List[Move] = {
    val buf = new ArrayBuffer[Move]
    captureDirs.foreach {
      innerLongRangeCaptures(buf, actor, actor.board, actor.pos, _, finalSquare, None, None, Nil, Nil, None)
    }
    buf.toList
  }

  private def innerLongRangeCaptures(buf: ArrayBuffer[Move], actor: Actor, initBoard: Board, initPos: PosMotion, initDir: Direction, finalSquare: Boolean, initFirstSquare: Option[PosMotion], initFirstBoard: Option[Board], initAllSquares: List[PosMotion], initAllTaken: List[PosMotion], promoted: Option[PromotableRole]): Int = {
    val newPiece = promoted match {
      case Some(promotedRole) => actor.piece.copy(role = promotedRole)
      case _ => actor.piece
    }

    @tailrec
    def walkUntilCapture(walkDir: Direction, curBoard: Board, curPos: PosMotion, firstSquare: Option[PosMotion], firstBoard: Option[Board], allSquares: List[Pos], allTaken: List[Pos]): Int =
      walkDir._2(curPos) match {
        case Some(nextPos) =>
          curBoard(nextPos) match {
            case None =>
              walkUntilCapture(walkDir, curBoard.moveUnsafe(curPos, nextPos, newPiece), nextPos, firstSquare, firstBoard, allSquares, allTaken)
            case Some(captPiece) if captPiece.isNot(actor.color) && !captPiece.isGhost =>
              walkDir._2(nextPos) match {
                case Some(landingPos) if curBoard(landingPos).isEmpty =>
                  val boardAfter = curBoard.takingUnsafe(curPos, landingPos, newPiece, nextPos, captPiece)
                  walkAfterCapture(walkDir, boardAfter, landingPos, firstSquare, firstBoard, allSquares, nextPos :: allTaken, true, 0)
                case _ => 0
              }
            case _ => 0
          }
        case _ => 0
      }

    def walkAfterCapture(walkDir: Direction, curBoard: Board, curPos: PosMotion, firstSquare: Option[PosMotion], firstBoard: Option[Board], allSquares: List[Pos], newTaken: List[Pos], justTaken: Boolean, currentCaptures: Int): Int = {
      val newSquares = curPos :: allSquares
      val opposite = Variant.oppositeDirs(walkDir._1)
      val newDest = if (firstSquare.isDefined) firstSquare else curPos.some
      val newBoard = if (firstBoard.isDefined) firstBoard else curBoard.some
      val extraCaptures = captureDirs.foldLeft(0) {
        case (total, captDir) =>
          if (captDir._1 == opposite) total
          else total + walkUntilCapture(captDir, curBoard, curPos, newDest, newBoard, newSquares, newTaken)
      }
      val moreExtraCaptures = walkDir._2(curPos) match {
        case Some(nextPos) if curBoard(nextPos).isEmpty =>
          walkAfterCapture(walkDir, curBoard.moveUnsafe(curPos, nextPos, newPiece), nextPos, firstSquare, firstBoard, allSquares, newTaken, false, currentCaptures + extraCaptures)
        case _ => 0
      }
      val totalCaptures = currentCaptures + extraCaptures + moreExtraCaptures
      if (totalCaptures == 0) {
        if (finalSquare)
          buf += actor.move(curPos, curBoard.withoutGhosts, newSquares, newTaken, promoted)
        else
          buf += actor.move(firstSquare.getOrElse(curPos), firstBoard.getOrElse(curBoard), newSquares, newTaken, promoted)
      }
      if (justTaken) totalCaptures + 1
      else totalCaptures
    }

    walkUntilCapture(initDir, initBoard, initPos, initFirstSquare, initFirstBoard, initAllSquares, initAllTaken)
  }

  override def finalizeBoard(board: Board, uci: format.Uci.Move, captured: Option[List[Piece]], situationBefore: Situation, finalSquare: Boolean): Board = {
    val remainingCaptures = board.actorAt(uci.dest).map(_.captureLength).getOrElse(0)
    if (remainingCaptures > 0) board
    else {
      val whiteActors = board.actorsOf(Color.White)
      val blackActors = board.actorsOf(Color.Black)
      val whiteKings = whiteActors.count(_.piece is King)
      val blackKings = blackActors.count(_.piece is King)
      val whitePieces = whiteActors.size
      val blackPieces = blackActors.size
      def loneKing(strongPieces: Int, strongKings: Int, weakKing: Actor) =
        strongPieces == 3 && strongKings >= 1 && weakKing.onLongDiagonal && board.piecesOnLongDiagonal == 1
      val whiteLoneKing =
        if (whiteKings == 1 && whitePieces == 1 && blackKings >= 1) {
          loneKing(blackPieces, blackKings, whiteActors.head)
        } else false
      val blackLoneKing =
        if (blackKings == 1 && blackPieces == 1 && whiteKings >= 1) {
          loneKing(whitePieces, whiteKings, blackActors.head)
        } else false
      if (whiteLoneKing || blackLoneKing) {
        board updateHistory { h =>
          // "abuse" kingmove counter to count the amount of moves made on the long
          // diagonal by the side with a lone king against 3 (see 7.2.7)
          h.withKingMove(Color(whiteLoneKing), None, true)
        } withoutGhosts
      } else board.withoutGhosts
    }
  }

  def maxDrawingMoves(board: Board): Option[Int] =
    drawingMoves(board, none).map(_._1)

  // (drawingMoves, resetOnNonKingMove, allowPromotion, first promotion: promotes this turn and has only one king)
  private def drawingMoves(board: Board, move: Option[Move]): Option[(Int, Boolean, Boolean, Boolean)] = {
    val whiteActors = board.actorsOf(Color.White).filterNot(_.piece.isGhost)
    val blackActors = board.actorsOf(Color.Black).filterNot(_.piece.isGhost)
    val whiteKings = whiteActors.count(_.piece is King)
    val blackKings = blackActors.count(_.piece is King)
    val whitePieces = whiteActors.size
    val blackPieces = blackActors.size
    def firstPromotion = move.exists(m => m.promotes && m.color.fold(whiteKings == 1, blackKings == 1))

    def singleKing(strongPieces: Int, strongKings: Int, weakKing: Actor, weakColor: Color) = {
      // weak side:   pieces == 1, kings == 1
      // strong side: pieces <= 2, kings >= 1
      //    7.2.8 => 5
      // strong side: pieces == 3, kings >= 1
      //    weak side on long diagonal => 7.2.7 => 5
      // strong side: pieces >= 3, kings == pieces
      //    7.2.4 => 15
      // strong side: kings >= 1
      //    7.2.5 => 15
      if (strongPieces <= 2 && strongKings >= 1) Some(10, false, true, firstPromotion) // 7.2.8: never reset, except on first promotion
      else if (strongPieces == 3 && strongKings >= 1 && weakKing.onLongDiagonal && board.piecesOnLongDiagonal == 1) {
        if (board.history.kingMoves(weakColor) >= 10) Some(10, false, true, firstPromotion) // 7.2.7: only draw after 5 kingmoves on the long diagonal have been recorded
        else Some(30, false, true, firstPromotion) // 7.2.7: right combination, awaiting 5th move, do not reset on promotion!
      } else if (strongPieces >= 3 && strongKings == strongPieces) Some(30, false, false, false) // 7.2.4
      else None
    }
    val singleKingDraw =
      if (whiteKings == 1 && whitePieces == 1 && blackKings >= 1) {
        singleKing(blackPieces, blackKings, whiteActors.head, Color.white)
      } else if (blackKings == 1 && blackPieces == 1 && whiteKings >= 1) {
        singleKing(whitePieces, whiteKings, blackActors.head, Color.black)
      } else None

    if (singleKingDraw.isDefined) singleKingDraw
    else if (blackKings >= 1 && whiteKings >= 1) {
      val totalPieces = blackPieces + whitePieces
      if (totalPieces == 6 || totalPieces == 7) Some(120, false, false, false) // 7.2.6: "6-and 7-pieces endings"
      else if (totalPieces == 4 || totalPieces == 5) Some(60, false, false, false) // 7.2.6: "4, and 5-pieces endings"
      else Some(30, true, false, false) // 7.2.5: "the players made 15 moves only kings without moving of men"
    } else None
  }

  /**
   * Update position hashes for Russian drawing rules (https://fmjd64.org/rules-of-the-game/):
   * 7.2.3. If three (or more) times the same position is repeated, and each time the same player having to move.
   * 7.2.4. If a player has three kings (or more) against a single enemy king, the game is drawn if his 15th move does not capture the enemy king
   *        (counting from the time of establishing the correlation of forces).
   * 7.2.5. If within 15 moves the players made moves only kings without moving of men and not making the capture.
   * 7.2.6. If the position in which the both opponents having kings have not changed the balance of pieces (ie, there was no capture and man did not become a king) for:
   *          – To 4-and 5-pieces endings – 30 moves;
   *          – In 6, and 7-pieces endings – 60 moves.
   * 7.2.7. If a player having in the party three kings, two kings and one man, one king and two men against one enemy king, located on the long diagonal, his 5th move will not be able to achieve a winning position.
   * 7.2.8. If a player having in the party two kings, one king and man, one king against enemy king to their 5th move will not be able to achieve a winning position.
   * 7.2.9. ... excluding case when the game is obvious and the player can continue to demonstrate the victory :S ...
   */
  def updatePositionHashes(board: Board, move: Move, hash: draughts.PositionHash): PositionHash = {
    val newHash = Hash(Situation(board, !move.piece.color))
    drawingMoves(board, move.some) match {
      case Some((drawingMoves, resetOnNonKingMove, allowPromotion, firstPromotion)) =>
        if (drawingMoves == 30 && (move.captures || (!allowPromotion && move.promotes) || (resetOnNonKingMove && move.piece.isNot(King))))
          newHash // 7.2.4 + 7.2.5 reset on capture (by which 7.2.4 becomes 7.2.8), and 7.2.5 on non-king move. A promotion resets to exclude the move that generates 7.2.4 (and implies a moved man for 7.2.5)
        else if (firstPromotion || (drawingMoves >= 60 && (move.captures || move.promotes)))
          newHash // 7.2.6 resets on capture or promotion
        else {
          def piecesBefore = board.pieces.count(!_._2.isGhost) + Math.max(board.ghosts, move.taken.map(_.size).getOrElse(0))
          if (drawingMoves == 10 && move.captures && board.pieces.count(!_._2.isGhost) <= 3 && piecesBefore > 3)
            newHash // 7.2.8 does reset on the capture that creates the piece configuration
          else // 7.2.7 is unclear - we count total moves on long diagonal from start of piece configuration, so reentering long diagonal enough times before ply 30 still draws (leaving the diagonal is dumb anyway)
            newHash ++ hash // 7.2.8 never resets once activated
        }
      case _ => newHash
    }
  }

  override def validSide(board: Board, strict: Boolean)(color: Color) = {
    val roles = board rolesOf color
    (roles.count(_ == Man) > 0 || roles.count(_ == King) > 0) &&
      (!strict || roles.size <= 12) &&
      !menOnPromotionRank(board, color)
  }
}