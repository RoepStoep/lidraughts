package views.html
package user

import controllers.routes

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.richText
import lidraughts.user.User

object bots {

  def apply(users: List[User])(implicit ctx: Context) = {

    val title = s"${users.size} Online bots"

    val sorted = users.sortBy { -_.playTime.??(_.total) }

    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("slist"), cssTag("bot.list")),
      wrapClass = "full-screen-force"
    )(
        main(cls := "page-menu bots")(
          user.bits.communityMenu("bots"),
          sorted.partition(_.isVerified) match {
            case (featured, all) =>
              div(cls := "bots page-menu__content")(
                featured.nonEmpty option div(cls := "box bots__featured")(
                  div(cls := "box__top")(h1("Featured bots")),
                  botTable(featured)
                ),
                all.nonEmpty option div(cls := "box")(
                  div(cls := "box__top")(h1("Community bots")),
                  botTable(all)
                ),
                users.isEmpty option div(cls := "box")(
                  div(cls := "box__top")(h1(title))
                )
              )
          }
        )
      )
  }

  private def botTable(users: List[User])(implicit ctx: Context) = div(cls := "bots__list")(
    users map { u =>
      div(cls := "bots__list__entry")(
        div(cls := "bots__list__entry__desc")(
          div(cls := "bots__list__entry__head")(
            userLink(u),
            div(cls := "bots__list__entry__rating")(
              u.bestAny3Perfs.map { showPerfRating(u, _) }
            )
          ),
          u.profile
            .ifTrue(ctx.noKid)
            .ifTrue(!u.troll || ctx.is(u))
            .flatMap(_.nonEmptyBio)
            .map { bio => td(shorten(bio, 400)) }
        ),
        a(
          dataIcon := "U",
          cls := List("bots__list__entry__play button button-empty text" -> true),
          st.title := trans.challenge.challengeToPlay.txt(),
          href := s"${routes.Lobby.home}?user=${u.username}#friend"
        )(trans.play())
      )
    }
  )
}
