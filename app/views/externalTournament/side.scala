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

  def apply(tour: ExternalTournament, chat: Boolean)(implicit ctx: Context) = frag(
    div(cls := "tour-ext__meta")(
      st.section(
        div(
          p("Tournament side...?")
        )
      ),
      trans.by(userIdLink(tour.createdBy.some))
    ),
    chat option views.html.chat.frag
  )
}
