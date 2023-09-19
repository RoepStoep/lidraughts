package views.html.search

import play.api.data.Form
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.gameSearch.{ Query, Sorting }

private object bits {

  import trans.search._

  private val dateFormatter = DateTimeFormat.forPattern("YYYY-MM-dd");
  private val dateMin = "2018-08-13"
  private def dateMinMax: List[Modifier] = List(min := dateMin, max := dateFormatter.print(DateTime.now))

  def of(form: Form[_])(implicit ctx: Context) = new {

    def dataReqs = List("winner", "loser", "white", "black").map { f =>
      data(s"req-$f") := ~form("players")(f).value
    }

    def colors(hide: Boolean) =
      draughts.Color.all.map { color =>
        tr(cls := List(s"${color.name}User user-row" -> true, "none" -> hide))(
          th(label(`for` := form3.id(form("players")(color.name)))(color.fold(trans.white, trans.black)())),
          td(cls := "single")(
            st.select(id := form3.id(form("players")(color.name)), name := form("players")(color.name).name)(
              option(cls := "blank", value := "")
            )
          )
        )
      }

    def winner(hide: Boolean) = form("players")("winner") |> { field =>
      tr(cls := List("winner user-row" -> true, "none" -> hide))(
        th(label(`for` := form3.id(field))(trans.winner())),
        td(cls := "single")(
          st.select(id := form3.id(field), name := field.name)(
            option(cls := "blank", value := "")
          )
        )
      )
    }

    def loser(hide: Boolean) = form("players")("loser") |> { field =>
      tr(cls := List("loser user-row" -> true, "none" -> hide))(
        th(label(`for` := form3.id(field))(trans.search.loser())),
        td(cls := "single")(
          st.select(id := form3.id(field), name := field.name)(
            option(cls := "blank", value := "")
          )
        )
      )
    }

    def rating = tr(
      th(label(trans.rating(), " ", span(cls := "help", title := ratingExplanation.txt())("(?)"))),
      td(
        div(cls := "half")(from(), " ", form3.select(form("ratingMin"), translatedAverageRatingChoices, "".some)),
        div(cls := "half")(to(), " ", form3.select(form("ratingMax"), translatedAverageRatingChoices, "".some))
      )
    )

    def hasAi = tr(
      th(label(`for` := form3.id(form("hasAi")))(trans.opponent(), " ", span(cls := "help", title := humanOrComputer.txt())("(?)"))),
      td(cls := "single opponent")(form3.select(form("hasAi"), translatedHasAiChoices, "".some))
    )

    def aiLevel = tr(cls := "aiLevel none")(
      th(label(trans.search.aiLevel())),
      td(
        div(cls := "half")(from(), " ", form3.select(form("aiLevelMin"), Query.aiLevels, "".some)),
        div(cls := "half")(to(), " ", form3.select(form("aiLevelMax"), Query.aiLevels, "".some))
      )
    )

    def source = tr(
      th(label(`for` := form3.id(form("source")))(trans.search.source())),
      td(cls := "single")(form3.select(form("source"), Query.sources, "".some))
    )

    def perf = tr(
      th(label(`for` := form3.id(form("perf")))(trans.variant())),
      td(cls := "single")(form3.select(form("perf"), Query.perfs, "".some))
    )

    def mode = tr(
      th(label(`for` := form3.id(form("mode")))(trans.mode())),
      td(cls := "single")(form3.select(form("mode"), translatedModeChoicesById, "".some))
    )

    def turns = tr(
      th(label(numberOfTurns(), " ", span(cls := "help", title := numberOfTurnsExplanation.txt())("(?)"))),
      td(
        div(cls := "half")(from(), " ", form3.select(form("turnsMin"), translatedTurnsChoices, "".some)),
        div(cls := "half")(to(), " ", form3.select(form("turnsMax"), translatedTurnsChoices, "".some))
      )
    )

    def duration = tr(
      tr(
        th(label(trans.duration())),
        td(
          div(cls := "half")(from(), " ", form3.select(form("durationMin"), translatedDurationChoices, "".some)),
          div(cls := "half")(to(), " ", form3.select(form("durationMax"), translatedDurationChoices, "".some))
        )
      )
    )

    def clockTime = tr(
      th(label(trans.clockInitialTime())),
      td(
        div(cls := "half")(from(), " ", form3.select(form("clock")("initMin"), translatedClockInitChoices, "".some)),
        div(cls := "half")(to(), " ", form3.select(form("clock")("initMax"), translatedClockInitChoices, "".some))
      )
    )

    def clockIncrement = tr(
      th(label(trans.clockIncrement())),
      td(
        div(cls := "half")(from(), " ", form3.select(form("clock")("incMin"), translatedClockIncChoices, "".some)),
        div(cls := "half")(to(), " ", form3.select(form("clock")("incMax"), translatedClockIncChoices, "".some))
      )
    )

    def status = tr(
      th(label(`for` := form3.id(form("status")))(result())),
      td(cls := "single")(form3.select(form("status"), Query.statuses, "".some))
    )

    def winnerColor = tr(
      th(label(`for` := form3.id(form("winnerColor")))(trans.search.winnerColor())),
      td(cls := "single")(form3.select(form("winnerColor"), translatedWinnerColorChoices, "".some))
    )

    def date = tr(cls := "date")(
      th(label(trans.search.date())),
      td(
        div(cls := "half")(from(), " ", form3.input(form("dateMin"), "date")(dateMinMax: _*)),
        div(cls := "half")(to(), " ", form3.input(form("dateMax"), "date")(dateMinMax: _*))
      )
    )

    def sort = tr(
      th(label(trans.search.sortBy())),
      td(
        div(cls := "half wide")(form3.select(form("sort")("field"), translatedSortFieldChoices)),
        div(cls := "half wide")(form3.select(form("sort")("order"), translatedSortOrderChoices))
      )
    )

    def analysed = {
      val field = form("analysed")
      tr(
        th(label(`for` := form3.id(field))(trans.search.analysis(), " ", span(cls := "help", title := onlyAnalysed.txt())("(?)"))),
        td(cls := "single")(
          st.input(
            tpe := "checkbox",
            cls := "cmn-toggle",
            id := form3.id(field),
            name := field.name,
            value := "1",
            field.value.has("1") option checked
          ),
          label(`for` := form3.id(field))
        )
      )
    }
  }
}
