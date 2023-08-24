package lidraughts.game

import java.security.MessageDigest
import lidraughts.db.ByteArray
import org.joda.time.DateTime

private[game] case class Metadata(
    source: Option[Source],
    pdnImport: Option[PdnImport],
    tournamentId: Option[String],
    swissId: Option[String],
    simulId: Option[String],
    simulPairing: Option[Int],
    timeOutUntil: Option[DateTime],
    drawLimit: Option[Int],
    microMatch: Option[String],
    isWfd: Boolean,
    analysed: Boolean
) {

  def needsMicroRematch = microMatch.contains("micromatch")

  def microMatchGameNr = microMatch ?? { mm =>
    if (mm == "micromatch" || mm.startsWith("2:")) 1.some
    else if (mm.startsWith("1:")) 2.some
    else none
  }

  def microMatchGameId = microMatch.map { mm =>
    if (mm.startsWith("2:") || mm.startsWith("1:")) mm.drop(2)
    else "*"
  }

  def pdnDate = pdnImport flatMap (_.date)

  def pdnUser = pdnImport flatMap (_.user)

  def isEmpty = this == Metadata.empty
}

private[game] object Metadata {

  val empty = Metadata(None, None, None, None, None, None, None, None, None, false, false)
}

case class PdnImport(
    user: Option[String],
    date: Option[String],
    pdn: String,
    // hashed PDN for DB unicity
    h: Option[ByteArray]
)

object PdnImport {

  def hash(pdn: String) = ByteArray {
    MessageDigest getInstance "MD5" digest
      pdn.lines.map(_.replace(" ", "")).filter(_.nonEmpty).mkString("\n").getBytes("UTF-8") take 12
  }

  def make(
    user: Option[String],
    date: Option[String],
    pdn: String
  ) = PdnImport(
    user = user,
    date = date,
    pdn = pdn,
    h = hash(pdn).some
  )

  import reactivemongo.bson.Macros
  import ByteArray.ByteArrayBSONHandler
  implicit val pdnImportBSONHandler = Macros.handler[PdnImport]
}
