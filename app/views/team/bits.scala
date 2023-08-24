package views.html.team

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator

import controllers.routes

object bits {

  import trans.team._

  def link(teamId: lidraughts.team.Team.ID): Frag =
    a(href := routes.Team.show(teamId))(teamIdToName(teamId))

  def link(team: lidraughts.team.Team): Frag =
    a(href := routes.Team.show(team.id))(team.name)

  def menu(currentTab: Option[String])(implicit ctx: Context) = ~currentTab |> { tab =>
    st.nav(cls := "page-menu__menu subnav")(
      (ctx.teamNbRequests > 0) option
        a(cls := tab.active("requests"), href := routes.Team.requests())(
          xJoinRequests(ctx.teamNbRequests)
        ),
      ctx.me.exists(_.canTeam) option
        a(cls := tab.active("mine"), href := routes.Team.mine())(
          myTeams()
        ),
      a(cls := tab.active("all"), href := routes.Team.all())(
        allTeams()
      ),
      ctx.me.exists(_.canTeam) option
        a(cls := tab.active("form"), href := routes.Team.form())(
          newTeam()
        )
    )
  }

  private[team] def teamTr(t: lidraughts.team.Team)(implicit ctx: Context) = tr(cls := "paginated")(
    td(cls := "subject")(
      a(dataIcon := "f", cls := List(
        "team-name text" -> true,
        "mine" -> myTeam(t.id)
      ), href := routes.Team.show(t.id))(
        t.name,
        t.isWfd option span(cls := "official-wfd", title := "Official WFD team")("E")
      ),
      shorten(t.description, 200)
    ),
    td(cls := "info")(
      p(nbMembers.plural(t.nbMembers, t.nbMembers.localize))
    )
  )

  private[team] def layout(
    title: String,
    openGraph: Option[lidraughts.app.ui.OpenGraph] = None,
    css: String = "team",
    moreJs: Frag = emptyFrag
  )(
    body: Frag
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag(css),
      moreJs = frag(infiniteScrollTag, moreJs),
      openGraph = openGraph
    )(body)
}
