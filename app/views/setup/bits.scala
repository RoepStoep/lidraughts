package views.html.setup

import play.api.data.{ Form, Field }

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

private object bits {

  val prefix = "sf_"

  def fieldId(field: Field): String = s"$prefix${field.id}"

  def fenInput(form: Form[_], strict: Boolean, limitKings: Boolean, validFen: Option[lidraughts.setup.ValidFen], fenVariants: Option[List[SelectChoice]])(implicit ctx: Context) = {
    val field = form("fen")
    val fenVariant = validFen.map(_.variant) | draughts.variant.Standard
    val url = field.value.fold(routes.Editor.parse(fenVariant.key))(v => routes.Editor.parse(s"${fenVariant.key}/$v")).url
    div(cls := "fen_position optional_config")(
      frag(
        div(cls := "fen_form", dataValidateUrl := s"""${routes.Setup.validateFenOk()}${strict.??("?strict=1")}${limitKings.??((if (strict) "&" else "?") + "kings=1")}""")(
          form3.input(field)(st.placeholder := trans.pasteTheFenStringHere.txt()),
          a(cls := "button button-empty editor_button", dataIcon := "m", title := trans.boardEditor.txt(), href := url)
        ),
        a(cls := "board_editor", href := url)(
          span(cls := "preview")(
            validFen map { vf =>
              if (limitKings && vf.tooManyKings)
                p(cls := "errortext")(trans.tooManyKings())
              else views.html.board.bits.mini(vf.fen, vf.situation.board.boardSize, vf.color)(div)
            }
          )
        ),
        fenVariants.map(renderFromPositionVariant(form, _))
      )
    )
  }

  def renderVariant(form: Form[_], variants: List[SelectChoice])(implicit ctx: Context) =
    div(cls := "variant label_select")(
      renderLabel(form("variant"), trans.variant()),
      renderSelect(form("variant"), variants)
    )

  def renderFromPositionVariant(form: Form[_], variants: List[SelectChoice])(implicit ctx: Context) =
    div(cls := "fen_variant hidden")(
      renderSelect(form("fenVariant"), variants)
    )

  def renderSelect(
    field: Field,
    options: Seq[SelectChoice],
    compare: (String, String) => Boolean = (a, b) => a == b
  ) = select(id := fieldId(field), name := field.name)(
    options.map {
      case (value, name, title) => option(
        st.value := value,
        st.title := title,
        field.value.exists(v => compare(v, value)) option selected
      )(name)
    }
  )

  def renderRadios(field: Field, options: Seq[SelectChoice]) =
    st.group(cls := "radio")(
      options.map {
        case (key, name, hint) => div(
          input(
            `type` := "radio",
            id := s"${fieldId(field)}_${key}",
            st.name := field.name,
            value := key,
            field.value.has(key) option checked
          ),
          label(
            cls := "required",
            title := hint,
            `for` := s"${fieldId(field)}_$key"
          )(name)
        )
      }
    )

  def renderInput(field: Field) =
    input(name := field.name, value := field.value, `type` := "hidden")

  def renderLabel(field: Field, content: Frag) =
    label(`for` := fieldId(field))(content)

  def renderCheckbox(field: Field, labelContent: Frag) = div(
    span(cls := "form-check-input")(
      form3.cmnToggle(fieldId(field), field.name, field.value.has("true"))
    ),
    renderLabel(field, labelContent)
  )

  def renderMicroMatch(form: Form[_])(implicit ctx: Context) =
    div(cls := "micro_match", title := trans.microMatchExplanation.txt())(
      renderCheckbox(form("microMatch"), trans.microMatch())
    )

  def renderTimeMode(form: Form[_], config: lidraughts.setup.BaseConfig)(implicit ctx: Context) =
    div(cls := "time_mode_config optional_config")(
      div(cls := "label_select")(
        renderLabel(form("timeMode"), trans.timeControl()),
        renderSelect(form("timeMode"), translatedTimeModeChoices)
      ),
      if (ctx.blind) frag(
        div(cls := "time_choice")(
          renderLabel(form("time"), trans.minutesPerSide()),
          renderSelect(form("time"), clockTimeChoices, (a, b) => a.replace(".0", "") == b)
        ),
        div(cls := "increment_choice")(
          renderLabel(form("increment"), trans.incrementInSeconds()),
          renderSelect(form("increment"), clockIncrementChoices)
        )
      )
      else frag(
        div(cls := "time_choice slider")(
          trans.minutesPerSide(),
          ": ",
          span(draughts.Clock.Config(~form("time").value.map(x => (x.toDouble * 60).toInt), 0).limitString.toString),
          renderInput(form("time"))
        ),
        div(cls := "increment_choice slider")(
          trans.incrementInSeconds(),
          ": ",
          span(form("increment").value),
          renderInput(form("increment"))
        )
      ),
      div(cls := "correspondence")(
        if (ctx.blind) div(cls := "days_choice")(
          renderLabel(form("days"), trans.daysPerTurn()),
          renderSelect(form("days"), corresDaysChoices)
        )
        else div(cls := "days_choice slider")(
          trans.daysPerTurn(),
          ": ",
          span(form("days").value),
          renderInput(form("days"))
        )
      )
    )

  val dataRandomColorVariants =
    attr("data-random-color-variants") := lidraughts.game.Game.variantsWhereWhiteIsBetter.map(_.id).mkString(",")

  val dataAnon = attr("data-anon")
  val dataMin = attr("data-min")
  val dataMax = attr("data-max")
  val dataValidateUrl = attr("data-validate-url")
  val dataResizable = attr("data-resizable")
  val dataType = attr("data-type")
}
