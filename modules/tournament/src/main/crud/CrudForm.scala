package lidraughts.tournament
package crud

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import draughts.StartingPosition
import draughts.variant.Variant
import lidraughts.common.Form._

object CrudForm {

  import DataForm._
  import lidraughts.common.Form.UTCDate._

  val maxHomepageHours = 72

  lazy val apply = Form(mapping(
    "name" -> text(minLength = 3, maxLength = 40),
    "homepageHours" -> number(min = 0, max = maxHomepageHours),
    "clockTime" -> numberInDouble(clockTimeChoices),
    "clockIncrement" -> numberIn(clockIncrementChoices),
    "minutes" -> number(min = 20, max = 1440),
    "variant" -> number.verifying(Variant exists _),
    "position_standard" -> optional(nonEmptyText),
    "position_russian" -> optional(nonEmptyText),
    "position_brazilian" -> optional(nonEmptyText),
    "date" -> utcDate,
    "image" -> stringIn(imageChoices),
    "headline" -> text(minLength = 5, maxLength = 30),
    "description" -> text(minLength = 10, maxLength = 1200),
    "conditions" -> Condition.DataForm.all,
    "password" -> optional(nonEmptyText),
    "berserkable" -> boolean,
    "streakable" -> boolean,
    "hasChat" -> boolean,
    "teamBattle" -> boolean,
    "drawLimit" -> text(minLength = 0, maxLength = 2)
      .verifying("Enter a value between 0 and 99, or leave empty", mvs => mvs.length == 0 || parseIntOption(mvs).??(m => m >= 0 && m <= 99)),
    "excludeUserIds" -> optional(nonEmptyText),
    "excludeReason" -> optional(nonEmptyText)
  )(CrudForm.Data.apply)(CrudForm.Data.unapply)
    .verifying("Invalid clock", _.validClock)
    .verifying("Increase tournament duration, or decrease game clock", _.validTiming)) fill empty

  case class Data(
      name: String,
      homepageHours: Int,
      clockTime: Double,
      clockIncrement: Int,
      minutes: Int,
      variant: Int,
      positionStandard: Option[String],
      positionRussian: Option[String],
      positionBrazilian: Option[String],
      date: DateTime,
      image: String,
      headline: String,
      description: String,
      conditions: Condition.DataForm.AllSetup,
      password: Option[String],
      berserkable: Boolean,
      streakable: Boolean,
      hasChat: Boolean,
      teamBattle: Boolean,
      drawLimit: String,
      excludeUserIds: Option[String],
      excludeReason: Option[String]
  ) {

    def realVariant = Variant orDefault variant

    def validClock = (clockTime + clockIncrement) > 0

    def validTiming = password.isDefined || (minutes * 60) >= (3 * estimatedGameDuration)

    private def estimatedGameDuration = 60 * clockTime + 30 * clockIncrement

    private def positionKey = realVariant match {
      case draughts.variant.Standard => positionStandard
      case draughts.variant.Russian => positionRussian
      case draughts.variant.Brazilian => positionBrazilian
      case _ => none
    }

    def startingPosition =
      positionKey.flatMap { key =>
        val parts = key.split('|')
        parts.headOption.flatMap(draughts.OpeningTable.byKey).flatMap { table =>
          parts.lastOption.flatMap(table.openingByFen)
        }
      } | realVariant.startingPosition

    def openingTable =
      positionKey.flatMap { key =>
        key.split('|').headOption.flatMap(draughts.OpeningTable.byKey)
      } filter realVariant.openingTables.contains
  }

  val imageChoices = List(
    "" -> "Lidraughts",
    "draughts64.logo.png" -> "Draughts 64"
  )
  val imageDefault = ""

  val empty = CrudForm.Data(
    name = "",
    homepageHours = 0,
    clockTime = clockTimeDefault,
    clockIncrement = clockIncrementDefault,
    minutes = minuteDefault,
    variant = draughts.variant.Standard.id,
    positionStandard = draughts.variant.Standard.initialFen.some,
    positionRussian = draughts.variant.Russian.initialFen.some,
    positionBrazilian = draughts.variant.Brazilian.initialFen.some,
    date = DateTime.now plusDays 7,
    image = "",
    headline = "",
    description = "",
    conditions = Condition.DataForm.AllSetup.default,
    password = None,
    berserkable = true,
    streakable = true,
    hasChat = true,
    teamBattle = false,
    drawLimit = "",
    excludeUserIds = None,
    excludeReason = None
  )
}
