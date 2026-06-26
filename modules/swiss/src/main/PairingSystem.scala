package lidraughts.swiss

import java.io.{ File, PrintWriter }
import scala.concurrent.blocking

final private class PairingSystem(
    trf: SwissTrf,
    executable: String
) {

  def apply(swiss: Swiss): Fu[List[SwissPairing.ByeOrPending]] =
    trf.fetchPlayerIds(swiss) flatMap { playerIds =>
      trf(swiss, playerIds, sorted = false, forPairings = true).map {
        invoke(swiss, _) |> reader(playerIds.map(_.swap))
      }
    }

  private def invoke(swiss: Swiss, input: List[String]): List[String] =
    withTempFile(swiss, input) { file =>
      import scala.sys.process._
      val flavour =
        if (swiss.nbPlayers < 400) "dutch"
        else if (swiss.nbPlayers < 750) "burstein"
        else "fast"
      val command = s"$executable --$flavour $file -p"
      val stdout = new collection.mutable.ListBuffer[String]
      val stderr = new StringBuilder
      val status = blocking {
        command ! ProcessLogger(stdout append _, stderr append _)
      }
      if (status != 0) {
        val error = stderr.toString
        if (error contains "No valid pairing exists") Nil
        else throw new PairingSystem.BBPairingException(error, swiss)
      } else stdout.toList
    }

  private def reader(idsToPlayers: IdPlayers)(output: List[String]): List[SwissPairing.ByeOrPending] =
    output
      .drop(1) // first line is the number of pairings
      .map(_ split ' ')
      .collect {
        case Array(p, "0") =>
          parseIntOption(p) flatMap idsToPlayers.get map { userId =>
            Left(SwissPairing.Bye(userId))
          }
        case Array(w, b) =>
          for {
            white <- parseIntOption(w) flatMap idsToPlayers.get
            black <- parseIntOption(b) flatMap idsToPlayers.get
          } yield Right(SwissPairing.Pending(white, black))
      }
      .flatten

  def withTempFile[A](swiss: Swiss, contents: List[String])(f: File => A): A = {
    // NOTE: The prefix and suffix must be at least 3 characters long,
    // otherwise this function throws an IllegalArgumentException.
    val file = File.createTempFile(s"lidraughts-swiss-${swiss.id}-${swiss.round}-", s"-bbp")
    val p = new PrintWriter(file, "UTF-8")
    try {
      p.write(contents.mkString("\n"))
      p.flush()
      val res = f(file)
      res
    } finally {
      p.close()
      file.delete()
    }
  }

}

private object PairingSystem {
  case class BBPairingException(val message: String, val swiss: Swiss) extends lidraughts.base.LidraughtsException
}
