package views.html.auth

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object checkYourEmail {

  def apply(
    userEmail: Option[lidraughts.security.EmailConfirm.UserEmail],
    form: Option[Form[_]] = None
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.checkYourEmail.txt(),
      moreCss = cssTag("email-confirm")
    ) {
        main(cls := s"page-small box box-pad email-confirm ${if (form.exists(_.hasErrors)) "error" else "anim"}")(
          h1(cls := "is-green text", dataIcon := "E")(trans.checkYourEmail()),
          p(trans.weHaveSentYouAnEmailClickTheLink()),
          h2(trans.notReceivingIt()),
          ol(
            li(h3(trans.ifYouDoNotSeeTheEmailCheckOtherPlaces())),
            userEmail.map(_.email).map { email =>
              li(
                h3(trans.makeSureYourEmailAddressIsCorrect()),
                br, br,
                postForm(action := routes.Auth.fixEmail)(
                  input(
                    id := "new-email",
                    tpe := "email",
                    required,
                    name := "email",
                    value := form.flatMap(_("email").value).getOrElse(email.value),
                    pattern := s"^((?!^${email.value}$$).)*$$"
                  ),
                  embedJsUnsafe(s"""
var email = document.getElementById("new-email");
var currentError = "${trans.thisIsAlreadyYourCurrentEmail.txt()}";
email.setCustomValidity(currentError);
email.addEventListener("input", function() {
email.setCustomValidity(email.validity.patternMismatch ? currentError : "");
      });"""),
                  submitButton(cls := "button")(trans.changeIt()),
                  form.map { f =>
                    errMsg(f("email"))
                  }
                )
              )
            },
            li(
              h3(trans.waitUpToFiveMinutes()), br,
              trans.itCanTakeAWhileToArrive(),
              userEmail.exists(_.email.isHotmail) option frag(
                br,
                trans.hotmailIsNotoriouslyBad(), br,
                trans.considerUsingADifferentOne()
              )
            ),
            li(
              h3(trans.stillNotGettingIt()), br,
              trans.didYoumakeSureYourEmailAddressIsCorrect(), br,
              trans.didYouWaitFiveMinutes(), br,
              trans.ifSoX(
                a(href := routes.Account.emailConfirmHelp)(trans.proceedToThisPage())
              )
            )
          )
        )
      }
}
