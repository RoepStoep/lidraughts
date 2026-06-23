package views
package html.swiss

import controllers.routes

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.markdownLinksOrRichText
import lidraughts.common.String.html.richText
import lidraughts.swiss.{ Swiss, SwissCondition }

object side {

  private val separator = " • "

  def apply(
    s: Swiss,
    verdicts: SwissCondition.All.WithVerdicts,
    streamers: List[lidraughts.user.User.ID],
    chat: Boolean
  )(implicit ctx: Context) = frag(
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
            } else s.perfType.map(_.name),
            separator,
            if (s.settings.rated) trans.ratedTournament() else trans.casualTournament()
          ),
          p(
            span(cls := "swiss__meta__round")(trans.swiss.nbRounds.plural(s.actualNbRounds, s"${s.round}/${s.actualNbRounds}")),
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
      if (verdicts.relevant) st.section(
        dataIcon := (if (ctx.isAuth && verdicts.accepted) "E" else "L"),
        cls := List(
          "conditions" -> true,
          "accepted" -> (ctx.isAuth && verdicts.accepted),
          "refused" -> (ctx.isAuth && !verdicts.accepted)
        )
      )(
          div(
            (verdicts.list.size < 2) option p(trans.conditionOfEntry()),
            verdicts.list map { v =>
              p(
                cls := List(
                  "condition" -> true,
                  "accepted" -> (ctx.isAuth && v.verdict.accepted),
                  "refused" -> (ctx.isAuth && !v.verdict.accepted)
                ),
                title := v.verdict.reason.map(_(ctx.lang))
              )(s.perfType map v.condition.name)
            }
          )
        )
      else br,
      small(trans.by(userIdLink(s.createdBy.some, isWfd = ~s.isWfd))),
      br,
      absClientDateTime(s.startsAt)
    ),
    streamers map views.html.streamer.bits.contextual,
    chat option views.html.chat.frag
  )
}
