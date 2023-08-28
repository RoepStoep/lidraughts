package views.html.team

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.team.Team
import lidraughts.user.User

import controllers.routes

object wfd {

  import trans.team._

  def profiles(t: Team, members: Iterable[User])(implicit ctx: Context) = {
    val (unnamed, named) = members.toList.sortBy(_.id).partition(_.profileWfd.flatMap(_.nonEmptyRealName).isEmpty)
    bits.layout(
      title = s"Edit ${t.name} WFD profiles",
      css = "team.wfd",
      moreJs = jsTag("wfd-profile.js")
    ) {
        main(cls := "page-menu page-small")(
          bits.menu(none),
          div(cls := "page-menu__content box box-pad")(
            h1(
              a(
                href := routes.Team.show(t.id),
                dataIcon := "I",
                cls := "text"
              ), "Edit WFD profiles"
            ),
            table(cls := "slist slist-pad")(
              thead(
                tr(
                  th("Lidraughts username"),
                  th("WFD profile name"),
                  th()
                )
              ),
              tbody(cls := "wfd-profiles")(
                (unnamed ::: named).map { u =>
                  tr(cls := "small")(
                    td(userLink(u, withOnline = false)),
                    td(u.profileWfd.flatMap(_.nonEmptyRealName)),
                    td(a(href := routes.Team.wfdProfileForm(t.id, u.id), dataIcon := "%", title := "Edit WFD profile"))
                  )
                }
              )
            )
          )
        )
      }
  }

  def profileForm(t: Team, u: User, form: play.api.data.Form[_])(implicit ctx: Context) =
    div(cls := "wfd-profile")(
      h2(s"${u.username} WFD profile"),
      postForm(cls := "form3", action := routes.Team.wfdProfileApply(t.id, u.id))(
        form3.split(
          form3.group(form("firstName"), trans.firstName(), half = true)(form3.input(_)),
          form3.group(form("lastName"), trans.lastName(), half = true)(form3.input(_))
        ),
        form3.action(form3.submit(trans.apply()))
      )
    )
}
