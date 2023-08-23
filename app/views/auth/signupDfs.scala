package views.html
package auth

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object signupDfs {

  private val recaptchaScript = raw("""<script src="https://www.google.com/recaptcha/api.js" async defer></script>""")

  private val signupText = "Registreren voor DFS Interland Aosta 2023"

  def apply(form: Form[_], recaptcha: lidraughts.security.RecaptchaPublicConfig)(implicit ctx: Context) =
    views.html.base.layout(
      title = signupText,
      moreJs = frag(
        jsTag("signup.js"),
        recaptcha.enabled option recaptchaScript,
        fingerprintTag
      ),
      moreCss = cssTag("auth"),
      csp = defaultCsp.withRecaptcha.some
    ) {
        main(cls := "auth auth-signup box box-pad")(
          h1(signupText),
          postForm(id := "signup_form", cls := "form3", action := routes.Auth.signupDfsPost)(
            auth.bits.formFields(form("username"), form("password"), form("email").some, register = true),
            form3.split(
              form3.group(form("firstName"), trans.firstName(), half = true)(form3.input(_)(required)),
              form3.group(form("lastName"), trans.lastName(), half = true)(form3.input(_)(required))
            ),
            h2("Gegevens voor DFS"),
            hr,
            div(cls := "form-group text", dataIcon := "")(
              "Onderstaande gegevens zijn optioneel. Alles wat je hier invult wordt direct doorgestuurd naar DFS en wordt niet opgeslagen door Lidraughts."
            ),
            form3.split(
              form3.group(form("woonplaats"), "Woonplaats")(form3.input(_)),
              form3.group(form("school"), "School", help = frag("Indien van toepassing.").some)(form3.input(_))
            ),
            form3.group(form("telefoonnummer"), "Telefoonnummer")(form3.input(_)),
            form3.group(form("bankrekening"), "Bankrekening", help = frag("Voor het uitbetalen van prijzengeld.").some)(form3.input(_)),
            input(id := "signup-fp-input", name := "fp", tpe := "hidden"),
            div(cls := "form-group text", dataIcon := "")(
              "Na registratie wordt je account automatisch aangemeld bij het Team ",
              a(href := routes.Team.show("dfs-interland-aosta-2023"))("DFS Interland Aosta 2023"),
              ". Je lidmaatschap van dit team wordt zo spoedig mogelijk handmatig beoordeeld.", br,
              small(trans.byRegisteringYouAgreeToBeBoundByOur(a(href := routes.Page.tos)(trans.termsOfService())))
            ),
            if (recaptcha.enabled)
              button(
              cls := "g-recaptcha submit button text big",
              attr("data-sitekey") := recaptcha.key,
              attr("data-callback") := "signupSubmit"
            )(trans.signUp())
            else form3.submit(trans.signUp(), icon = none, klass = "big")
          )
        )
      }
}
