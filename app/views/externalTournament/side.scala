package views
package html.externalTournament

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.markdownLinksOrRichText
import lidraughts.externalTournament.ExternalTournament

import controllers.routes

object side {

  private val separator = " • "

  def apply(t: ExternalTournament, roundsPlayed: Option[Int], chat: Boolean)(implicit ctx: Context) = {
    val optionalInfo = List(
      t.settings.microMatches option trans.microMatches(),
      t.settings.nbRounds map { rounds =>
        val actualRounds = math.max(~roundsPlayed, rounds)
        roundsPlayed.fold[Frag](trans.swiss.nbRounds(actualRounds)) { round =>
          frag(
            span(cls := "tour-ext__meta__round")(s"$round/$actualRounds"),
            trans.swiss.nbRounds.plural(actualRounds, "")
          )
        }
      }
    ).flatten
    frag(
      div(cls := "tour-ext__meta")(
        st.section(dataIcon := t.perfType.iconChar.toString)(
          div(
            p(
              showClock(t),
              separator,
              if (t.variant.exotic) {
                views.html.game.bits.variantLink(
                  t.variant,
                  t.variant.name
                )
              } else t.perfType.trans,
              separator,
              if (t.rated) trans.ratedTournament() else trans.casualTournament(),
              optionalInfo.nonEmpty option frag(
                br,
                optionalInfo.intersperse(separator)
              )
            )
          )
        ),
        t.settings.description map { d =>
          st.section(cls := "description")(markdownLinksOrRichText(d))
        },
        trans.by(userIdLink(t.createdBy.some))
      ),
      chat option views.html.chat.frag
    )
  }

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
