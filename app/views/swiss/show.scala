package views.html
package swiss

import controllers.routes
import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.swiss.{ Swiss, SwissCondition }

object show {

  def apply(
    s: Swiss,
    verdicts: SwissCondition.All.WithVerdicts,
    data: play.api.libs.json.JsObject,
    chatOption: Option[lidraughts.chat.UserChat.Mine],
    isLocalMod: Boolean
  )(implicit ctx: Context): Frag = {
    val isDirector = ctx.userId.has(s.createdBy)
    val hasScheduleInput = isDirector && s.settings.manualRounds && s.isNotFinished
    views.html.base.layout(
      title = s"${s.name} #${s.id}",
      moreJs = frag(
        jsAt(s"compiled/lidraughts.swiss${isProd ?? (".min")}.js"),
        hasScheduleInput option flatpickrTag,
        embedJsUnsafe(s"""LidraughtsSwiss.start(${
          safeJsonValue(
            Json
              .obj(
                "data" -> data,
                "i18n" -> bits.jsI18n,
                "userId" -> ctx.userId,
                "chat" -> chatOption.map { c =>
                  chat.json(
                    c.chat,
                    name = trans.chatRoom.txt(),
                    timeout = c.timeout,
                    public = true,
                    resourceId = lidraughts.chat.Chat.ResourceId(s"swiss/${c.chat.id}"),
                    localMod = isLocalMod
                  )
                }
              )
              .add("schedule" -> hasScheduleInput)
          )
        })""")
      ),
      moreCss = frag(
        cssTag("swiss.show"),
        hasScheduleInput option cssTag("flatpickr")
      ),
      draughtsground = false,
      openGraph = lidraughts.app.ui
        .OpenGraph(
          title = s"${s.name}: ${s.variant.name} ${s.clock.show} #${s.id}",
          url = s"$netBaseUrl${routes.Swiss.show(s.id.value).url}",
          description = s"${s.nbPlayers} players compete in the ${showEnglishDate(s.startsAt)} ${s.name} swiss tournament " +
            s"organized by ${teamIdToName(s.teamId)}. " +
            s.winnerId.fold("Winner is not yet decided.") { winnerId =>
              s"${usernameOrId(winnerId)} takes the prize home!"
            }
        )
        .some
    )(
        main(cls := "swiss")(
          st.aside(cls := "swiss__side")(
            swiss.side(s, verdicts, chatOption.isDefined)
          ),
          div(cls := "swiss__main")(div(cls := "box"))
        )
      )
  }
}
