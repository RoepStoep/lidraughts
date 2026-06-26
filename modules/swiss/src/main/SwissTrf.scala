package lidraughts.swiss

import reactivemongo.bson._

import lidraughts.db.dsl._
import lidraughts.user.User

// https://www.fide.com/FIDE/handbook/C04Annex2_TRF16.pdf
final class SwissTrf(
    playerColl: Coll,
    sheetApi: SwissSheetApi,
    baseUrl: String
) {

  private type Bits = List[(Int, String)]

  def apply(swiss: Swiss, sorted: Boolean, forPairings: Boolean): Fu[List[String]] =
    fetchPlayerIds(swiss) flatMap { apply(swiss, _, sorted, forPairings) }

  def apply(swiss: Swiss, playerIds: PlayerIds, sorted: Boolean, forPairings: Boolean): Fu[List[String]] =
    SwissPlayer.fields { f =>
      sheetApi
        .source(swiss, sort = sorted.??($doc(f.rating -> -1)))
        .map { lines =>
          tournamentLines(swiss, forPairings) ::: forbiddenPairings(swiss, playerIds) ::: lines
            .map((playerLine(swiss, playerIds) _).tupled)
            .map(formatLine)
        }
    }

  private def tournamentLines(swiss: Swiss, forPairings: Boolean) =
    List(
      s"012 ${swiss.name}",
      s"022 ${baseUrl}/swiss/${swiss.id}",
      s"032 Lidraughts",
      s"042 ${dateFormatter print swiss.startsAt}",
      s"052 ${swiss.finishedAt ?? dateFormatter.print}",
      s"062 ${swiss.nbPlayers}",
      s"092 Individual: Swiss-System",
      s"102 ${baseUrl}/swiss",
      s"XXR ${swiss.settings.nbRounds}",
      s"XXC ${draughts.Color(swiss.id.value(0).toInt % 2 == 0).name}1"
    ) ::: (
        if (forPairings) List(s"XXF 1") else Nil
      )

  private def playerLine(
    swiss: Swiss,
    playerIds: PlayerIds
  )(p: SwissPlayer, pairings: Map[SwissRound.Number, SwissPairing], sheet: SwissSheet): Bits =
    List(
      3 -> "001",
      8 -> playerIds.getOrElse(p.userId, 0).toString,
      (15 + p.userId.size) -> p.userId,
      52 -> p.rating.toString,
      84 -> "%1.1f".formatLocal(java.util.Locale.US, sheet.points.value)
    ) ::: {
        swiss.allRounds.zip(sheet.outcomes).flatMap {
          case (rn, outcome) =>
            val pairing = pairings get rn
            List(
              95 -> pairing.map(_ opponentOf p.userId).flatMap(playerIds.get).??(_.toString),
              97 -> pairing.map(_ colorOf p.userId).??(_.fold("w", "b")),
              99 -> {
                import SwissSheet._
                outcome match {
                  case Absent => "-"
                  case Late => "H"
                  case Bye => "U"
                  case Draw => "="
                  case Win => "1"
                  case Loss => "0"
                  case Ongoing => "Z"
                }
              }
            ).map { case (l, s) => (l + (rn.value - 1) * 10, s) }
        }
      } ::: {
        p.absent && swiss.round.value < swiss.settings.nbRounds
      }.?? {
        List( // http://www.rrweb.org/javafo/aum/JaVaFo2_AUM.htm#_Unusual_info_extensions
          95 -> "0000",
          97 -> "",
          99 -> "-"
        ).map { case (l, s) => (l + swiss.round.value * 10, s) }
      }

  private def formatLine(bits: Bits): String =
    bits.foldLeft("") {
      case (acc, (pos, txt)) => s"""$acc${" " * (pos - txt.size - acc.size)}$txt"""
    }

  private val dateFormatter = org.joda.time.format.DateTimeFormat forStyle "M-"

  def fetchPlayerIds(swiss: Swiss): Fu[PlayerIds] =
    SwissPlayer
      .fields { p =>
        import BsonHandlers._
        import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
        playerColl
          .aggregateOne(
            Match($doc(p.swissId -> swiss.id)),
            List(
              Sort(Descending(p.rating)),
              Group(BSONNull)("us" -> PushField(p.userId))
            )
          )
          .map {
            ~_.flatMap(_.getAs[List[User.ID]]("us"))
          }
          .map {
            _.view.zipWithIndex.map {
              case (userId, index) =>
                (userId, index + 1)
            }.toMap
          }
      }

  private def forbiddenPairings(swiss: Swiss, playerIds: PlayerIds): List[String] =
    if (swiss.settings.forbiddenPairings.isEmpty) List.empty[String]
    else
      swiss.settings.forbiddenPairings.lines.flatMap {
        _.trim.toLowerCase.split(' ').map(_.trim) match {
          case Array(u1, u2) if u1 != u2 =>
            for {
              id1 <- playerIds.get(u1)
              id2 <- playerIds.get(u2)
            } yield s"XXP $id1 $id2"
          case _ => None
        }
      }.toList
}
