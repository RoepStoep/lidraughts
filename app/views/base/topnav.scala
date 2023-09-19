package views.html.base

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object topnav {

  private def linkTitle(url: String, name: Frag)(implicit ctx: Context) =
    if (ctx.blind) h3(name) else a(href := url)(name)

  def apply()(implicit ctx: Context) = st.nav(id := "topnav", cls := "hover")(
    st.section(
      linkTitle("/", frag(
        span(cls := "play")(trans.play()),
        span(cls := "home")("lidraughts.org")
      )),
      div(role := "group")(
        if (ctx.noBot) a(href := "/?any#hook")(trans.createAGame())
        else a(href := "/?any#friend")(trans.playWithAFriend()),
        ctx.noBot option frag(
          a(href := routes.Tournament.home())(if (isGranted(_.Beta)) trans.arena.arenaTournaments() else trans.tournaments()),
          isGranted(_.Beta) option a(href := routes.Swiss.home())(trans.swiss.swissTournaments.txt() + " [BETA]"),
          a(href := routes.Simul.home)(trans.simultaneousExhibitions())
        )
      )
    ),
    st.section(
      linkTitle(routes.Puzzle.home.toString, trans.learnMenu()),
      div(role := "group")(
        ctx.noBot option frag(
          //a(href := routes.Learn.index)(trans.draughtsBasics()),
          a(href := routes.Puzzle.home)(trans.training()),
          a(href := routes.Practice.index)(trans.practice.txt()),
          a(href := routes.Coordinate.home)(trans.coordinates.coordinates())
        ),
        a(href := routes.Study.allDefault(1))(trans.studyMenu()),
        a(href := routes.Page.variantHome)(trans.rulesAndVariants())
      //a(href := routes.Coach.allDefault(1))(trans.coaches())
      )
    ),
    st.section(
      linkTitle(routes.Tv.index.toString, trans.watch()),
      div(role := "group")(
        a(href := routes.Tv.index)("Lidraughts TV"),
        a(href := routes.Tv.games)(trans.currentGames()),
        a(href := routes.Streamer.index())(trans.streamersMenu()),
        a(href := routes.Relay.index())(trans.broadcast.broadcasts())
      //ctx.noBot option a(href := routes.Video.index)(trans.videoLibrary())
      )
    ),
    st.section(
      linkTitle(routes.User.list.toString, trans.community()),
      div(role := "group")(
        a(href := routes.User.list)(trans.players()),
        a(href := routes.Team.home())(trans.team.teams()),
        NotForKids(a(href := routes.ForumCateg.index)(trans.forum())),
        a(href := routes.Main.faq)("FAQ"),
        ctx.me.exists(!_.kid) option
          a(cls := "community-patron", href := routes.Plan.index)(trans.patron.donate())
      )
    ),
    st.section(
      linkTitle(routes.UserAnalysis.index.toString, trans.tools()),
      div(role := "group")(
        a(href := routes.UserAnalysis.index)(trans.analysis()),
        isGranted(_.CreatePuzzles) option a(href := routes.UserAnalysis.puzzleEditor)("Puzzle editor"),
        //a(href := s"${routes.UserAnalysis.index}#explorer")(trans.openingExplorer()),
        a(href := routes.Editor.index)(trans.boardEditor()),
        a(href := routes.Importer.importGame)(trans.importGame()),
        a(href := routes.Search.index())(trans.search.advancedSearch())
      )
    )
  )
}
