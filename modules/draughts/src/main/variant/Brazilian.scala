package draughts
package variant

case object Brazilian extends Variant(
  id = 12,
  gameType = 26,
  key = "brazilian",
  name = "Brazilian",
  shortName = "Brazilian",
  title = "Same rules as international draughts, played on an 8x8 board.",
  standardInitialPosition = false,
  boardSize = Board.D64
) {

  def pieces = Russian.pieces
  def initialFen = Russian.initialFen
  def startingPosition = Russian.startingPosition
  override val openingTables = List(OpeningTable.tableFmjdBrazilian, OpeningTable.tableIDFBasic)

  def captureDirs = Standard.captureDirs
  def moveDirsColor = Standard.moveDirsColor
  def moveDirsAll = Standard.moveDirsAll

  override def finalizeBoard(board: Board, uci: format.Uci.Move, captured: Option[List[Piece]], situationBefore: Situation, finalSquare: Boolean): Board = {
    val remainingCaptures = finalSquare.fold(0, situationBefore.captureLengthFrom(uci.orig).getOrElse(0) - 1)
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

  def maxDrawingMoves(board: Board): Option[Int] = Russian.maxDrawingMoves(board)
  def updatePositionHashes(board: Board, move: Move, hash: draughts.PositionHash): PositionHash = Russian.updatePositionHashes(board, move, hash)

  override def validSide(board: Board, strict: Boolean)(color: Color) = {
    val roles = board rolesOf color
    (roles.count(_ == Man) > 0 || roles.count(_ == King) > 0) &&
      (!strict || roles.size <= 12) &&
      (!menOnPromotionRank(board, color) || board.ghosts != 0)
  }
}