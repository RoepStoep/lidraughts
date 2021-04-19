package views.html.challenge

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.challenge.Challenge
import lidraughts.common.String.html.safeJsonValue

import controllers.routes

object bits {

  def js(c: Challenge, json: play.api.libs.json.JsObject, owner: Boolean)(implicit ctx: Context) =
    frag(
      jsTag("challenge.js", defer = true),
      embedJsUnsafe(s"""lidraughts=window.lidraughts||{};customWs=true;lidraughts_challenge = ${
        safeJsonValue(
          Json.obj(
            "socketUrl" -> routes.Challenge.websocket(c.id, apiVersion.value).url,
            "xhrUrl" -> routes.Challenge.show(c.id).url,
            "owner" -> owner,
            "data" -> json
          ).add("i18n" -> c.isExternal.option(jsI18n()))
        )
      }""")
    )

  def details(c: Challenge)(implicit ctx: Context) = frag(
    div(cls := "details")(
      div(cls := "variant", dataIcon := (if (c.variant.fromPosition) '*' else c.perfType.iconChar))(
        div(
          if (c.variant.exotic) {
            frag(
              views.html.game.bits.variantLink(c.variant, translatedVariantName(c.variant), c.initialFen),
              (!c.variant.fromPosition && c.customStartingPosition) option {
                span(cls := "variant_info")(
                  " ", translatedVariantName(draughts.variant.FromPosition)
                )
              }
            )
          } else
            c.perfType.trans,
          br,
          span(cls := "clock")(
            c.daysPerTurn map { days =>
              if (days == 1) trans.oneDay()
              else trans.nbDays.pluralSame(days)
            } getOrElse shortClockName(c.clock.map(_.config))
          )
        )
      ),
      div(cls := "mode")(modeName(c.mode))
    ),
    c.isMicroMatch option div(cls := "micro-match")(
      trans.microMatchChallenge(), " ",
      trans.microMatchExplanation()
    )
  )

  private def jsI18n()(implicit ctx: Context) = i18nJsObject(translations)
  private val translations = List(
    trans.nbDays,
    trans.nbHours,
    trans.nbMinutes,
    trans.nbSeconds
  )
}
