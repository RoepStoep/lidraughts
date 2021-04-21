package lidraughts.game

import draughts.format.pdn.{ Pdn, Tag, Tags, TagType, Parser, ParsedPdn }
import draughts.format.{ FEN, Forsyth, pdn => draughtsPdn }
import draughts.{ Centis, Color }

import lidraughts.common.LightUser

final class PdnDump(
    netBaseUrl: String,
    getLightUser: LightUser.Getter
) {

  import PdnDump._

  def apply(game: Game, initialFen: Option[FEN], flags: WithFlags, hideRatings: Boolean = false): Fu[Pdn] = {
    val imported = game.pdnImport.flatMap { pdni =>
      Parser.full(pdni.pdn).toOption
    }
    val boardPos = game.variant.boardSize.pos
    val algebraic = flags.algebraic && boardPos.hasAlgebraic
    val tagsFuture =
      if (flags.tags) tags(game, initialFen, imported, flags.draughtsResult, algebraic, flags.opening, flags.profileName, !hideRatings)
      else fuccess(Tags(Nil))
    tagsFuture map { ts =>
      val turns = flags.moves ?? {
        val fenSituation = ts.fen.map(_.value) flatMap { f => Forsyth.<<<@(game.variant, f) }
        val pdnMovesFull = game.pdnMovesConcat(true, true)
        val pdnMoves = draughts.Replay.unambiguousPdnMoves(
          pdnMoves = pdnMovesFull,
          initialFen = ts.fen.map(_.value),
          variant = game.variant
        ).fold(
            err => {
              logger.warn(s"Could not unambiguate moves of ${game.id}: $err")
              shortenMoves(pdnMovesFull)
            },
            moves => moves
          )
        val moves = flags keepDelayIf game.playable applyDelay pdnMoves
        val moves2 =
          if (algebraic) san2alg(moves, boardPos)
          else moves
        val moves3 =
          if (fenSituation.exists(_.situation.color.black)) ".." +: moves2
          else moves2
        makeTurns(
          moves3,
          fenSituation.map(_.fullMoveNumber) | 1,
          flags.clocks ?? ~game.bothClockStates(true),
          game.startColor
        )
      }
      Pdn(ts, turns)
    }
  }

  private def shortenMoves(moves: Seq[String]) = moves map { move =>
    val x1 = move.indexOf("x")
    if (x1 == -1) move
    else {
      val x2 = move.lastIndexOf("x")
      if (x2 == x1 || x2 == -1) move
      else move.slice(0, x1) + move.slice(x2, move.length)
    }
  }

  private def san2alg(moves: Seq[String], boardPos: draughts.BoardPos) = moves map { move =>
    val capture = move.contains('x')
    val fields = if (capture) move.split("x") else move.split("-")
    val algebraicFields = fields.flatMap { boardPos.algebraic(_) }
    val sep = if (capture) "x" else "-"
    algebraicFields mkString sep
  }

  private def gameUrl(id: String) = s"$netBaseUrl/$id"

  private def namedLightUser(userId: String) =
    lidraughts.user.UserRepo.byId(userId) map {
      _ ?? { u =>
        LightUser(
          id = u.id,
          name = u.profile.flatMap(_.nonEmptyRealName).fold(u.username)(n => s"$n (${u.username})"),
          title = u.title.map(_.value),
          isPatron = u.plan.active
        ).some
      }
    }

  private def gameLightUsers(game: Game, withProfileName: Boolean): Fu[(Option[LightUser], Option[LightUser])] =
    (game.whitePlayer.userId ?? { if (withProfileName) namedLightUser else getLightUser }) zip
      (game.blackPlayer.userId ?? { if (withProfileName) namedLightUser else getLightUser })

  private def rating(p: Player) = p.rating.fold("?")(_.toString)

  def player(p: Player, u: Option[LightUser]) =
    p.aiLevel.fold(u.fold(p.name | lidraughts.user.User.anonymous)(_.name))("Lidraughts AI level " + _)

  private val customStartPosition: Set[draughts.variant.Variant] =
    Set(draughts.variant.Russian, draughts.variant.Brazilian, draughts.variant.Frysk, draughts.variant.FromPosition)

  private def eventOf(game: Game) = {
    val perf = game.perfType.fold("Standard")(_.name)
    game.tournamentId.map { id =>
      s"${game.mode} $perf tournament https://lidraughts.org/tournament/$id"
    } orElse game.swissId.map { id =>
      s"$perf swiss https://lidraughts.org/swiss/$id"
    } orElse game.simulId.map { id =>
      s"$perf simul https://lidraughts.org/simul/$id"
    } getOrElse {
      s"${game.mode} $perf game"
    }
  }

  private def ratingDiffTag(p: Player, tag: Tag.type => TagType) =
    p.ratingDiff.map { rd => Tag(tag(Tag), s"${if (rd >= 0) "+" else ""}$rd") }

  def tags(
    game: Game,
    initialFen: Option[FEN],
    imported: Option[ParsedPdn],
    draughtsResult: Boolean,
    algebraic: Boolean,
    withOpening: Boolean,
    withProfileName: Boolean = false,
    withRatings: Boolean = true
  ): Fu[Tags] = gameLightUsers(game, withProfileName) map {
    case (wu, bu) => Tags {
      val importedDate = imported.flatMap(_.tags(_.Date))
      def convertedFen = initialFen.flatMap { fen =>
        if (algebraic) Forsyth.toAlgebraic(game.variant, fen.value) map FEN
        else fen.some
      }
      List[Option[Tag]](
        Tag(_.Event, imported.flatMap(_.tags(_.Event)) | { if (game.imported) "Import" else eventOf(game) }).some,
        Tag(_.Site, gameUrl(game.id)).some,
        Tag(_.Date, importedDate | Tag.UTCDate.format.print(game.createdAt)).some,
        Tag(_.Round, imported.flatMap(_.tags(_.Round)) | "-").some,
        Tag(_.White, player(game.whitePlayer, wu)).some,
        Tag(_.Black, player(game.blackPlayer, bu)).some,
        Tag(_.Result, result(game, draughtsResult)).some,
        importedDate.isEmpty option Tag(_.UTCDate, imported.flatMap(_.tags(_.UTCDate)) | Tag.UTCDate.format.print(game.createdAt)),
        importedDate.isEmpty option Tag(_.UTCTime, imported.flatMap(_.tags(_.UTCTime)) | Tag.UTCTime.format.print(game.createdAt)),
        withRatings option Tag(_.WhiteElo, rating(game.whitePlayer)),
        withRatings option Tag(_.BlackElo, rating(game.blackPlayer)),
        withRatings ?? ratingDiffTag(game.whitePlayer, _.WhiteRatingDiff),
        withRatings ?? ratingDiffTag(game.blackPlayer, _.BlackRatingDiff),
        wu.flatMap(_.shortTitle).map { t => Tag(_.WhiteTitle, t) },
        bu.flatMap(_.shortTitle).map { t => Tag(_.BlackTitle, t) },
        Tag(_.GameType, game.variant.gameType).some,
        Tag.timeControl(game.clock.map(_.config)).some,
        game.metadata.microMatchGameIdText.map(gameId => Tag(_.MicroMatch, gameId)),
        withOpening option Tag(_.Opening, game.opening.fold("?")(_.opening.name)),
        Tag(_.Termination, {
          import draughts.Status._
          game.status match {
            case Created | Started => "Unterminated"
            case Aborted | NoStart => "Abandoned"
            case Timeout | Outoftime => "Time forfeit"
            case Resign | Draw | Stalemate | Mate | VariantEnd => "Normal"
            case Cheat => "Rules infraction"
            case UnknownFinish => "Unknown"
          }
        }).some
      ).flatten ::: customStartPosition(game.variant).??(List(
          Tag(_.FEN, convertedFen.fold("?")(f => Forsyth.shorten(f.value)))
        ))
    }
  }

  private def makeTurns(moves: Seq[String], from: Int, clocks: Vector[Centis], startColor: Color): List[draughtsPdn.Turn] = {
    val clockOffset = startColor.fold(0, 1)
    val firstBlackClock = startColor.fold(1, 0)
    (moves grouped 2).zipWithIndex.toList map {
      case (moves, index) =>
        val whiteClock = index * 2 - clockOffset
        draughtsPdn.Turn(
          number = index + from,
          white = moves.headOption filter (".." !=) map { san =>
            draughtsPdn.Move(
              san = san,
              turn = Color.White,
              secondsLeft = (clocks lift whiteClock map (_.roundSeconds), clocks lift (whiteClock - 1).atLeast(firstBlackClock) map (_.roundSeconds))
            )
          },
          black = moves lift 1 map { san =>
            draughtsPdn.Move(
              san = san,
              turn = Color.Black,
              secondsLeft = (clocks lift whiteClock map (_.roundSeconds), clocks lift (whiteClock + 1) map (_.roundSeconds))
            )
          }
        )
    } filterNot (_.isEmpty)
  }

}

object PdnDump {

  private val delayMovesBy = 3
  private val delayKeepsFirstMoves = 5

  case class WithFlags(
      clocks: Boolean = true,
      moves: Boolean = true,
      tags: Boolean = true,
      evals: Boolean = true,
      opening: Boolean = true,
      literate: Boolean = false,
      draughtsResult: Boolean = true,
      algebraic: Boolean = false,
      profileName: Boolean = false,
      delayMoves: Boolean = false
  ) {
    def applyDelay[M](moves: Seq[M]): Seq[M] =
      if (!delayMoves) moves
      else moves.take((moves.size - delayMovesBy) atLeast delayKeepsFirstMoves)

    def keepDelayIf(cond: Boolean) = copy(delayMoves = delayMoves && cond)
  }

  def result(game: Game, draughtsResult: Boolean) =
    if (game.finished) Color.showResult(game.winnerColor, draughtsResult)
    else "*"
}
