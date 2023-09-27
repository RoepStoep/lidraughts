package views.html
package account

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.security.EmailConfirm.Help._

import controllers.routes

object emailConfirmHelp {

  def apply(form: Form[_], status: Option[Status])(implicit ctx: Context) = views.html.base.layout(
    title = trans.emailConfirmHelp.txt(),
    moreCss = cssTag("email-confirm"),
    moreJs = jsTag("emailConfirmHelp.js")
  )(frag(
      main(cls := "page-small box box-pad email-confirm-help")(
        h1(trans.emailConfirmHelp()),
        p(trans.emailConfirmNotReceived()),
        st.form(cls := "form3", action := routes.Account.emailConfirmHelp, method := "get")(
          form3.split(
            form3.group(
              form("username"),
              trans.username(),
              help = trans.whatSignupUsername().some
            ) { f =>
                form3.input(f)(pattern := lidraughts.user.User.newUsernameRegex.regex)
              },
            div(cls := "form-group")(
              form3.submit(trans.apply())
            )
          )
        ),
        div(cls := "replies")(
          status map {
            case NoSuchUser(name) => frag(
              p(trans.usernameNotFound(strong(name))),
              p(
                a(href := routes.Auth.signup)(
                  trans.usernameCanBeUsedForNewAccount()
                )
              )
            )
            case EmailSent(name, email) => frag(
              p(trans.emailSent(email.conceal)),
              p(
                trans.emailCanTakeSomeTime(), br,
                strong(trans.refreshInboxAfterFiveMinutes())
              ),
              p(trans.checkSpamFolder()),
              p(trans.emailForSignupHelp()),
              hr,
              p(i(s"Hello, please confirm my account: $name")),
              hr,
              p(
                trans.copyTextToEmail(
                  a(href := s"mailto:$contactEmail?subject=Confirm account $name")(
                    contactEmail
                  )
                )
              ),
              p(trans.waitForSignupHelp())
            )
            case Confirmed(name) => frag(
              p(trans.accountConfirmed(strong(name))),
              p(trans.accountCanLogin(a(href := routes.Auth.login)(name))),
              p(trans.accountConfirmationEmailNotNeeded())
            )
            case Closed(name) =>
              p(trans.accountClosed(strong(name)))
            case NoEmail(name) =>
              p(trans.accountRegisteredWithoutEmail(strong(name)))
          }
        )
      )
    ))
}
