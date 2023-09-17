package views.html
package account

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object close {

  import trans.settings._

  def apply(u: lidraughts.user.User, form: play.api.data.Form[_])(implicit ctx: Context) = account.layout(
    title = s"${u.username} - ${closeAccount.txt()}",
    active = "close"
  ) {
    div(cls := "account box box-pad")(
      h1(dataIcon := "j", cls := "text")(closeAccount()),
      postForm(cls := "form3", action := routes.Account.closeConfirm)(
        div(cls := "form-group")(closeAccountExplanation()),
        div(cls := "form-group")(cantOpenSimilarAccount()),
        form3.passwordModified(form("passwd"), trans.password())(autocomplete := "off"),
        form3.actions(frag(
          a(href := routes.User.show(u.username))(changedMindDoNotCloseAccount()),
          form3.submit(
            closeAccount(),
            icon = "j".some,
            confirm = closingIsDefinitive.txt().some,
            klass = "button-red"
          )
        ))
      )
    )
  }
}
