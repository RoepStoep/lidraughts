package views
package html.swiss

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.markdownLinksOrRichText
import lidraughts.swiss.Swiss

import controllers.routes

object side {

  private val separator = " • "

  def apply(s: Swiss, chat: Boolean)(implicit ctx: Context) = frag(
    div(cls := "swiss__meta")(
      st.section(dataIcon := s.perfType.map(_.iconChar.toString))(
        div(
          p(
            s.clock.show,
            separator,
            if (s.variant.exotic) {
              views.html.game.bits.variantLink(
                s.variant,
                s.variant.name
              )
            } else s.perfType.map(_.trans),
            separator,
            if (s.settings.rated) trans.ratedTournament() else trans.casualTournament()
          ),
          p(
            span(cls := "swiss__meta__round")(s"${s.round}/${s.actualNbRounds}"),
            trans.swiss.nbRounds.plural(s.actualNbRounds, ""),
            separator,
            a(href := routes.Swiss.home)("Swiss [BETA]"),
            (isGranted(_.ManageTournament) || (ctx.userId.has(s.createdBy) && s.isCreated)) option frag(
              " ",
              a(href := routes.Swiss.edit(s.id.value), title := trans.ratedTournament.txt())(iconTag("%"))
            )
          ),
          bits.showInterval(s)
        )
      ),
      s.settings.description map { d =>
        st.section(cls := "description")(markdownLinksOrRichText(d))
      },
      teamLink(s.teamId),
      separator,
      absClientDateTime(s.startsAt)
    ),
    chat option views.html.chat.frag
  )
}
