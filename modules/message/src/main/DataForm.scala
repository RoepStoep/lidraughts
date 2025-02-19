package lidraughts.message

import play.api.data._
import play.api.data.Forms._

import lidraughts.common.Form.{ cleanNonEmptyText, cleanText }
import lidraughts.security.Granter
import lidraughts.user.{ User, UserRepo }

private[message] final class DataForm(security: MessageSecurity) {

  import DataForm._

  def thread(me: User) = Form(mapping(
    "username" -> lidraughts.user.DataForm.historicalUsernameField
      .verifying("Unknown username", { fetchUser(_).isDefined })
      .verifying("Sorry, this player doesn't accept new messages", { name =>
        Granter(_.MessageAnyone)(me) || {
          security.canMessage(me.id, User normalize name) awaitSeconds 2 // damn you blocking API
        }
      }),
    "subject" -> cleanText(minLength = 3, maxLength = 100),
    "text" -> cleanText(minLength = 3, maxLength = 8000),
    "mod" -> optional(cleanNonEmptyText)
  )({
      case (username, subject, text, mod) => ThreadData(
        user = fetchUser(username) err "Unknown username " + username,
        subject = subject,
        text = text,
        asMod = Granter(_.ModMessage)(me) ?? mod.isDefined
      )
    })(_.export.some))

  def post = Form(single(
    "text" -> cleanText(minLength = 3)
  ))

  private def fetchUser(username: String) = UserRepo named username awaitSeconds 2
}

object DataForm {

  case class ThreadData(
      user: User,
      subject: String,
      text: String,
      asMod: Boolean
  ) {

    def export = (user.username, subject, text, asMod option "1")
  }
}
