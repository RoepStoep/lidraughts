package lidraughts.externalTournament

import io.lemonlabs.uri.Url
import org.joda.time.{ DateTime, DateTimeZone }
import org.joda.time.format.DateTimeFormat
import play.api.Play.current
import play.api.libs.ws.WS

import lidraughts.db.dsl._

final class FmjdPlayerApi(
    baseUrl: String,
    coll: Coll
) {

  import FmjdPlayerApi._
  import BsonHandlers._

  def byId(id: String) = coll.byId[FmjdPlayer](id)

  private[externalTournament] def refresh: Unit =
    for {
      adminOpt <- byId(adminId)
      admin = adminOpt | emptyAdmin
      baseDate = DateTime.now.withDayOfMonth(1)
      months = List(0, 1, 2).map(baseDate.minusMonths) // list is published once every 3 months
      urlOpt <- getFirstPublishedMonth(Url.parse(baseUrl), months)
    } yield urlOpt match {
      case None =>
        logger.warn(s"No FMJD player file found at $baseUrl")
      case Some(url) if admin.firstName == url =>
        logger.info(s"FMJD player file up to date")
      case Some(url) =>
        logger.info(s"Downloading new FMJD player file $url")
        WS.url(url).get() map { res =>
          val lines = res.body.lines
          if (!lines.hasNext) logger.warn("Received empty FMJD player file")
          else {
            val headers = quotedCsvRegex.split(lines.next)
            val playerData = lines.foldLeft(List.empty[Array[String]]) {
              (lines, line) => quotedCsvRegex.split(line) :: lines
            }
            val missingHeaders = Headers.all.filterNot(headers.contains)
            if (missingHeaders.nonEmpty) logger.warn(s"Missing headers: $missingHeaders")
            else if (playerData.length < 10000) logger.warn(s"Too few players: ${playerData.length}")
            else {
              logger.info(s"Received ${playerData.length} players")
              val newPlayers = createPlayers(headers, playerData)
              if (newPlayers.length != playerData.length) logger.warn(s"Parsed only ${newPlayers.length} players")
              newPlayers
                .map { newPlayer =>
                  byId(newPlayer.id) flatMap {
                    case Some(existingPlayer) =>
                      if (newPlayer == existingPlayer) fuccess(none)
                      else coll.update($id(newPlayer.id), newPlayer).void inject (false, newPlayer).some
                    case _ => fuccess((true, newPlayer).some)
                  }
                }
                .sequenceFu
                .map { _.flatten }
                .foreach { addedOrUpdated =>
                  logger.info(s"Updated ${addedOrUpdated.count(!_._1)} players")
                  val addedPlayers = addedOrUpdated flatMap {
                    case (true, p) => p.some
                    case _ => none[FmjdPlayer]
                  }
                  coll.bulkInsert(
                    documents = addedPlayers.map(FmjdPlayerBSONHandler.write).toStream,
                    ordered = false
                  ) >> {
                    logger.info(s"Inserted ${addedPlayers.length} players")
                    val newAdmin = adminOpt.getOrElse(emptyAdmin).copy(firstName = url)
                    if (adminOpt.isDefined) coll.update($id(adminId), newAdmin)
                    else coll.insert(newAdmin)
                  }
                }
            }
          }
        } recover {
          case _: java.net.ConnectException => // ignore network errors
          case e: Exception => logger.error("Exception updating FMJD players", e)
        }
    }

  private def createPlayers(headers: Array[String], data: List[Array[String]]) = {
    val idF = headers.indexOf(Headers.id)
    val userIdF = headers.indexOf(Headers.userId)
    val lastNameF = headers.indexOf(Headers.lastName)
    val firstNameF = headers.indexOf(Headers.firstName)
    val countryF = headers.indexOf(Headers.country)
    val titleF = headers.indexOf(Headers.title)
    val ratingF = headers.indexOf(Headers.rating)
    val titleWF = headers.indexOf(Headers.titleW)
    val ratingWF = headers.indexOf(Headers.ratingW)
    data.flatMap { fields =>
      for {
        id <- cleanField(fields, idF)
        firstName <- cleanField(fields, firstNameF)
        lastName <- cleanField(fields, lastNameF)
        country <- cleanField(fields, countryF)
      } yield FmjdPlayer(
        _id = id,
        firstName = firstName,
        lastName = lastName,
        country = country,
        userId = cleanField(fields, userIdF).map(_.toLowerCase),
        title = cleanField(fields, titleF).map(_.toUpperCase),
        rating = cleanField(fields, ratingF).flatMap(parseIntOption),
        titleW = cleanField(fields, titleWF).map(_.toUpperCase),
        ratingW = cleanField(fields, ratingWF).flatMap(parseIntOption)
      )
    }
  }

  private def cleanField(fields: Array[String], index: Int) =
    fields.lift(index).map(clean)

  private def clean(s: String) =
    if (s.startsWith("\"") && s.endsWith("\"")) s.slice(1, s.length - 1).trim
    else s.trim

  private def getFirstPublishedMonth(baseUrl: Url, months: Seq[DateTime]): Fu[Option[String]] = {
    months.foldLeft(fuccess(none[String])) { (fu, month) =>
      fu.flatMap {
        case None =>
          val url = baseUrl.addPathPart(s"V${fileFormat.print(month)}.DRA").toString
          WS.url(url).head() map { _.status == 200 option url }
        case result => fuccess(result)
      }
    }
  }
}

object FmjdPlayerApi {

  val quotedCsvRegex = """,(?=([^"]*"[^"]*")*[^"]*$)""".r
  val fileFormat = DateTimeFormat forPattern "yyMMdd" withZone DateTimeZone.UTC

  val adminId = "00001"
  val emptyAdmin = FmjdPlayer(
    _id = adminId,
    userId = none,
    firstName = "",
    lastName = "",
    country = "",
    title = none,
    rating = none,
    titleW = none,
    ratingW = none
  )

  object Headers {

    val id = "nr_ew"
    val userId = "nick"
    val lastName = "last_n"
    val firstName = "first_n"
    val country = "country"
    val title = "title"
    val rating = "rating"
    val titleW = "wtitle"
    val ratingW = "wrating"

    val all = List(id, userId, lastName, firstName, country, title, rating, titleW, ratingW)
  }
}

