package views.html
package externalTournament

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.externalTournament.ExternalTournament
import lidraughts.user.User

import controllers.routes

object show {

  def apply(
    tour: ExternalTournament,
    data: play.api.libs.json.JsObject,
    chatOption: Option[lidraughts.chat.UserChat.Mine]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = tour.name,
      moreJs = frag(
        jsAt(s"compiled/lidraughts.externalTournament${isProd ?? (".min")}.js"),
        embedJsUnsafe(s"""LidraughtsExternalTournament.start(${
          safeJsonValue(
            Json.obj(
              "data" -> data,
              "i18n" -> bits.jsI18n,
              "userId" -> ctx.userId,
              "chat" -> chatOption.map { c =>
                chat.json(
                  c.chat,
                  name = trans.chatRoom.txt(),
                  timeout = c.timeout,
                  public = true,
                  resourceId = lidraughts.chat.Chat.ResourceId(s"tour-ext/${c.chat.id}")
                )
              }
            )
          )
        })""")
      ),
      moreCss = cssTag("tournament.external.show"),
      draughtsground = false
    )(
        main(cls := "tour-ext")(
          st.aside(cls := "tour-ext__side")(
            side(tour, chatOption.isDefined)
          ),
          div(cls := "tour-ext__main")(div(cls := "box box-pad"))
        )
      )
}
