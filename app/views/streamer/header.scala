package views.html.streamer

import controllers.routes
import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

object header {

  import trans.streamer._

  def apply(s: lidraughts.streamer.Streamer.WithUserAndStream, following: Option[Boolean])(implicit ctx: Context) =
    div(cls := "streamer-header")(
      bits.pic(s.streamer, s.user),
      div(cls := "overview")(
        h1(dataIcon := "")(
          titleTag(s.user.title),
          s.streamer.name
        ),
        s.streamer.headline.map(_.value).map { d =>
          p(cls := s"headline ${if (d.size < 60) "small" else if (d.size < 120) "medium" else "large"}")(d)
        },
        div(cls := "services")(
          s.streamer.twitch.map { twitch =>
            a(
              cls := List(
                "service twitch" -> true,
                "live" -> s.stream.exists(_.twitch)
              ),
              href := twitch.fullUrl
            )(bits.svg.twitch, " ", twitch.minUrl)
          },
          s.streamer.youTube.map { youTube =>
            a(
              cls := List(
                "service youTube" -> true,
                "live" -> s.stream.exists(_.twitch)
              ),
              href := youTube.fullUrl
            )(bits.svg.youTube, " ", youTube.minUrl)
          },
          a(cls := "service lidraughts", href := routes.User.show(s.user.username))(
            bits.svg.lidraughts,
            " ",
            netBaseUrl,
            routes.User.show(s.user.username).url
          )
        ),
        div(cls := "ats")(
          s.stream.map { s =>
            p(cls := "at")(currentlyStreaming(strong(s.status)))
          } getOrElse frag(
            p(cls := "at")(trans.lastSeenActive(momentFromNow(s.streamer.seenAt))),
            s.streamer.liveAt.map { liveAt =>
              p(cls := "at")(lastStream(momentFromNow(liveAt)))
            }
          )
        ),
        following.map { f =>
          (ctx.isAuth && !ctx.is(s.user)) option
            submitButton(attr("data-user") := s.user.id, dataIcon := "h", cls := List(
              "follow button text" -> true,
              "active" -> f
            ))(
              span(cls := "active-no")(trans.follow()),
              span(cls := "active-yes")(trans.following())
            )
        }
      )
    )
}
