package lidraughts.security

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints

import lidraughts.common.{ LameName, EmailAddress, Form => LidraughtsForm }
import lidraughts.user.{ User, TotpSecret, UserRepo }
import User.{ ClearPassword, TotpToken }

final class DataForm(
    val captcher: akka.actor.ActorSelection,
    authenticator: lidraughts.user.Authenticator,
    emailValidator: EmailAddressValidator
) extends lidraughts.hub.CaptchedForm {

  import DataForm._

  case class Empty(gameId: String, move: String)

  val empty = Form(mapping(
    "gameId" -> text,
    "move" -> text
  )(Empty.apply)(_ => None)
    .verifying(captchaFailMessage, validateCaptcha _))

  def emptyWithCaptcha = withCaptcha(empty)

  private val anyEmail =
    LidraughtsForm.cleanNonEmptyText(minLength = 6, maxLength = 320).verifying(Constraints.emailAddress)
  private val acceptableEmail = anyEmail.verifying(emailValidator.acceptableConstraint)
  private def acceptableUniqueEmail(forUser: Option[User]) =
    acceptableEmail.verifying(emailValidator uniqueConstraint forUser)

  private def withAcceptableDns(m: Mapping[String]) = m verifying emailValidator.withAcceptableDns

  private val preloadEmailDnsForm = Form(single("email" -> acceptableEmail))

  def preloadEmailDns(implicit req: play.api.mvc.Request[_]): Funit =
    preloadEmailDnsForm
      .bindFromRequest()
      .fold(
        _ => funit,
        email => emailValidator.preloadDns(EmailAddress(email))
      )

  object signup {

    private val username = LidraughtsForm.cleanNonEmptyText
      .verifying(
        Constraints minLength 2,
        Constraints maxLength 20,
        Constraints.pattern(
          regex = User.newUsernamePrefix,
          error = "usernamePrefixInvalid"
        ),
        Constraints.pattern(
          regex = User.newUsernameSuffix,
          error = "usernameSuffixInvalid"
        ),
        Constraints.pattern(
          regex = User.newUsernameChars,
          error = "usernameCharsInvalid"
        ),
        Constraints.pattern(
          regex = User.newUsernameLetters,
          error = "usernameCharsInvalid"
        )
      )
      .verifying("usernameUnacceptable", u => !LameName.username(u))
      .verifying("usernameAlreadyUsed", u => !UserRepo.nameExists(u).awaitSeconds(4))

    val website = Form(mapping(
      "username" -> username,
      "password" -> text(minLength = 4),
      "email" -> withAcceptableDns(acceptableUniqueEmail(none)),
      "fp" -> optional(nonEmptyText),
      "g-recaptcha-response" -> optional(nonEmptyText)
    )(SignupData.apply)(_ => None))

    val websiteDfs = Form(mapping(
      "username" -> username,
      "password" -> text(minLength = 4),
      "email" -> acceptableUniqueEmail(none),
      "firstName" -> LidraughtsForm.cleanText(minLength = 2, maxLength = 20),
      "lastName" -> LidraughtsForm.cleanText(minLength = 2, maxLength = 20),
      "woonplaats" -> optional(nonEmptyText),
      "telefoonnummer" -> optional(nonEmptyText),
      "school" -> optional(nonEmptyText),
      "bankrekening" -> optional(nonEmptyText),
      "fp" -> optional(nonEmptyText),
      "g-recaptcha-response" -> optional(nonEmptyText)
    )(DfsSignupData.apply)(_ => None))

    val mobile = Form(mapping(
      "username" -> username,
      "password" -> text(minLength = 4),
      "email" -> withAcceptableDns(acceptableUniqueEmail(none))
    )(MobileSignupData.apply)(_ => None))
  }

  val passwordReset = Form(mapping(
    "email" -> anyEmail, // allow unacceptable emails for BC
    "gameId" -> text,
    "move" -> text
  )(PasswordReset.apply)(_ => None)
    .verifying(captchaFailMessage, validateCaptcha _))

  def passwordResetWithCaptcha = withCaptcha(passwordReset)

  val newPassword = Form(single(
    "password" -> text(minLength = 4)
  ))

  case class PasswordResetConfirm(newPasswd1: String, newPasswd2: String) {
    def samePasswords = newPasswd1 == newPasswd2
  }

  val passwdReset = Form(mapping(
    "newPasswd1" -> nonEmptyText(minLength = 2),
    "newPasswd2" -> nonEmptyText(minLength = 2)
  )(PasswordResetConfirm.apply)(PasswordResetConfirm.unapply).verifying(
      "newPasswordsDontMatch",
      _.samePasswords
    ))

  def changeEmail(u: User, old: Option[EmailAddress]) = authenticator loginCandidate u map { candidate =>
    Form(mapping(
      "passwd" -> passwordMapping(candidate),
      "email" -> withAcceptableDns {
        acceptableUniqueEmail(candidate.user.some).verifying(emailValidator differentConstraint old)
      }
    )(ChangeEmail.apply)(ChangeEmail.unapply)).fill(ChangeEmail(
      passwd = "",
      email = old.??(_.value)
    ))
  }

  def setupTwoFactor(u: User) = authenticator loginCandidate u map { candidate =>
    Form(mapping(
      "secret" -> nonEmptyText,
      "passwd" -> passwordMapping(candidate),
      "token" -> nonEmptyText
    )(TwoFactor.apply)(TwoFactor.unapply).verifying(
        "invalidAuthenticationCode",
        _.tokenValid
      )).fill(TwoFactor(
      secret = TotpSecret.random.base32,
      passwd = "",
      token = ""
    ))
  }

  def disableTwoFactor(u: User) = authenticator loginCandidate u map { candidate =>
    Form(tuple(
      "passwd" -> passwordMapping(candidate),
      "token" -> text.verifying("invalidAuthenticationCode", t => u.totpSecret.??(_.verify(TotpToken(t))))
    ))
  }

  def fixEmail(old: EmailAddress) = Form(
    single(
      "email" -> withAcceptableDns {
        acceptableUniqueEmail(none).verifying(emailValidator differentConstraint old.some)
      }
    )
  ).fill(old.value)

  def modEmail(user: User) = Form(single("email" -> acceptableUniqueEmail(user.some)))

  def closeAccount(u: User) = authenticator loginCandidate u map { candidate =>
    Form(single("passwd" -> passwordMapping(candidate)))
  }

  private def passwordMapping(candidate: User.LoginCandidate) =
    text.verifying("incorrectPassword", p => candidate.check(ClearPassword(p)))
}

object DataForm {

  case class SignupData(
      username: String,
      password: String,
      email: String,
      fp: Option[String],
      `g-recaptcha-response`: Option[String]
  ) {
    def recaptchaResponse = `g-recaptcha-response`

    def realEmail = EmailAddress(email)

    def fingerPrint = fp.filter(_.nonEmpty) map FingerPrint.apply
  }

  case class DfsSignupData(
      username: String,
      password: String,
      email: String,
      firstName: String,
      lastName: String,
      woonplaats: Option[String],
      school: Option[String],
      bankrekening: Option[String],
      telefoonnummer: Option[String],
      fp: Option[String],
      `g-recaptcha-response`: Option[String]
  ) {
    def recaptchaResponse = `g-recaptcha-response`

    def realEmail = EmailAddress(email)

    def fingerPrint = fp.filter(_.nonEmpty) map FingerPrint.apply
  }

  case class MobileSignupData(
      username: String,
      password: String,
      email: String
  ) {
    def realEmail = EmailAddress(email)
  }

  case class PasswordReset(
      email: String,
      gameId: String,
      move: String
  ) {
    def realEmail = EmailAddress(email)
  }

  case class ChangeEmail(passwd: String, email: String) {
    def realEmail = EmailAddress(email)
  }

  case class TwoFactor(secret: String, passwd: String, token: String) {
    def tokenValid = TotpSecret(secret).verify(User.TotpToken(token))
  }
}
