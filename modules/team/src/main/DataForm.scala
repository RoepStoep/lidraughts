package lidraughts.team

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import lidraughts.db.dsl._
import lidraughts.common.Form.cleanText

private[team] final class DataForm(
    teamColl: Coll,
    val captcher: akka.actor.ActorSelection
) extends lidraughts.hub.CaptchedForm {

  private object Fields {
    val name = "name" -> cleanText(minLength = 3, maxLength = 60)
    val location = "location" -> optional(cleanText(minLength = 3, maxLength = 80))
    val description = "description" -> cleanText(minLength = 30, maxLength = 4000)
    val open = "open" -> number
    val gameId = "gameId" -> text
    val move = "move" -> text
    val chat = "chat" -> boolean
  }

  val create = Form(mapping(
    Fields.name,
    Fields.location,
    Fields.description,
    Fields.open,
    Fields.gameId,
    Fields.move
  )(TeamSetup.apply)(TeamSetup.unapply)
    .verifying("This team already exists", d => !teamExists(d).awaitSeconds(2))
    .verifying(captchaFailMessage, validateCaptcha _))

  def edit(team: Team) = Form(mapping(
    Fields.location,
    Fields.description,
    Fields.open,
    Fields.chat
  )(TeamEdit.apply)(TeamEdit.unapply)) fill TeamEdit(
    location = team.location,
    description = team.description,
    open = if (team.open) 1 else 0,
    chat = team.chat
  )

  val request = Form(mapping(
    "message" -> cleanText(minLength = 30, maxLength = 2000)
  )(RequestSetup.apply)(RequestSetup.unapply)) fill RequestSetup(
    message = "Hello, I would like to join the team!"
  )

  val apiRequest = Form(single("message" -> optional(text(minLength = 30, maxLength = 2000))))

  val processRequest = Form(tuple(
    "process" -> nonEmptyText,
    "url" -> nonEmptyText
  ))

  val selectMember = Form(single(
    "userId" -> lidraughts.user.DataForm.historicalUsernameField
  ))

  def createWithCaptcha = withCaptcha(create)

  val pmAll = Form(
    single("message" -> cleanText(minLength = 3, maxLength = 9000))
  )

  private def teamExists(setup: TeamSetup) =
    teamColl.exists($id(Team nameToId setup.trim.name))
}

private[team] case class TeamSetup(
    name: String,
    location: Option[String],
    description: String,
    open: Int,
    gameId: String,
    move: String
) {

  def isOpen = open == 1

  def trim = copy(
    name = name.trim,
    location = location map (_.trim) filter (_.nonEmpty),
    description = description.trim
  )
}

private[team] case class TeamEdit(
    location: Option[String],
    description: String,
    open: Int,
    chat: Boolean
) {

  def isOpen = open == 1

  def trim = copy(
    location = location map (_.trim) filter (_.nonEmpty),
    description = description.trim
  )
}

case class RequestSetup(
    message: String
)
