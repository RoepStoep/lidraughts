package views
package html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.richText
import lidraughts.externalTournament.ExternalTournament

import controllers.routes

object side {

  private val separator = " • "

  def apply(t: ExternalTournament, chat: Boolean)(implicit ctx: Context) = frag(
    div(cls := "tour-ext__meta")(
      st.section(dataIcon := t.perfType.map(_.iconChar.toString))(
        div(
          p(
            showClock(t),
            separator,
            if (t.variant.exotic) {
              views.html.game.bits.variantLink(
                t.variant,
                t.variant.name
              )
            } else t.perfType.map(_.name),
            separator,
            trans.ratedTournament()
          )
        )
      ),
      trans.by(userIdLink(t.createdBy.some))
    ),
    chat option views.html.chat.frag
  )

  private def showClock(t: ExternalTournament)(implicit ctx: Context) = t.clock.map { config =>
    frag(config.show)
  } getOrElse {
    t.days.map { days =>
      span(title := trans.correspondence.txt())(
        if (days == 1) trans.oneDay() else trans.nbDays.pluralSame(days)
      )
    }.getOrElse {
      span(title := trans.unlimited.txt())("∞")
    }
  }
}
