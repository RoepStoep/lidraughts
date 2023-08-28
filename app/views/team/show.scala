package views.html.team

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.mashup.TeamInfo
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator
import lidraughts.common.String.html.{ richText, safeJsonValue }
import lidraughts.team.Team

import controllers.routes

object show {

  import trans.team._

  def apply(
    t: Team,
    members: Paginator[Either[lidraughts.common.LightUser, lidraughts.common.LightWfdUser]],
    info: TeamInfo,
    chatOption: Option[lidraughts.chat.UserChat.Mine],
    socketVersion: Option[lidraughts.socket.Socket.SocketVersion],
    pimpChat: Option[String => Option[String]]
  )(implicit ctx: Context) =
    bits.layout(
      title = t.name,
      openGraph = lidraughts.app.ui.OpenGraph(
        title = s"${t.name} team",
        url = s"$netBaseUrl${routes.Team.show(t.id).url}",
        description = shorten(t.description, 152)
      ).some,
      moreJs =
        for {
          v <- socketVersion
          chat <- chatOption
        } yield frag(
          jsAt(s"compiled/lidraughts.chat${isProd ?? (".min")}.js"),
          embedJsUnsafe(s"""lidraughts.team=${
            safeJsonValue(
              Json.obj(
                "id" -> t.id,
                "socketVersion" -> v.value,
                "chat" -> views.html.chat.json(
                  chat.chat.pimp(pimpChat),
                  name = trans.chatRoom.txt(),
                  timeout = chat.timeout,
                  public = true,
                  resourceId = lidraughts.chat.Chat.ResourceId(s"team/${chat.chat.id}"),
                  localMod = ctx.userId has t.createdBy
                )
              )
            )
          }""")
        )
    )(
        main(cls := "team-show box", socketVersion.map { v =>
          data("socket-version") := v.value
        })(
          div(cls := "box__top")(
            h1(cls := "text", dataIcon := "f")(
              t.name,
              t.isWfd option a(cls := "official-wfd", title := "Official WFD team")("E")
            ),
            div(
              if (t.disabled) span(cls := "staff")("CLOSED")
              else nbMembers.plural(t.nbMembers, strong(t.nbMembers.localize))
            )
          ),
          (info.mine || t.enabled) option div(cls := "team-show__content")(
            div(cls := "team-show__content__col1")(
              st.section(cls := "team-show__meta")(
                p(teamLeader(), ": ", userIdLink(t.createdBy.some, isWfd = t.isWfd))
              ),
              chatOption.isDefined option frag(
                views.html.chat.frag,
                div(
                  cls := "chat__members",
                  aria.live := "off",
                  aria.relevant := "additions removals text"
                )(
                    span(cls := "number")(nbsp),
                    " ",
                    trans.spectators.txt().replace(":", ""),
                    " ",
                    span(cls := "list")
                  )
              ),
              div(cls := "team-show__actions")(
                (t.enabled && !info.mine) option frag(
                  if (info.requestedByMe) strong(beingReviewed())
                  else ctx.me.??(_.canTeam) option
                    postForm(cls := "inline", action := routes.Team.join(t.id))(
                      submitButton(cls := "button button-green")(joinTeam.txt())
                    )
                ),
                ctx.userId.ifTrue(t.enabled && info.mine) map { myId =>
                  postForm(
                    cls := "team-show__subscribe form3",
                    action := routes.Team.subscribe(t.id)
                  )(
                      div(
                        span(form3.cmnToggle("team-subscribe", "subscribe", checked = info.subscribed)),
                        label(`for` := "team-subscribe")(subscribeToTeamMessages())
                      )
                    )
                },
                (info.mine && !info.createdByMe) option
                  postForm(cls := "quit", action := routes.Team.quit(t.id))(
                    submitButton(cls := "button button-empty button-red confirm")(quitTeam.txt())
                  ),
                info.createdByMe option frag(
                  a(href := routes.Tournament.teamBattleForm(t.id), cls := "button button-empty text", dataIcon := "g")(
                    span(
                      strong(teamBattle()),
                      em(teamBattleOverview())
                    )
                  ),
                  a(href := s"${routes.Tournament.form()}?team=${t.id}", cls := "button button-empty text", dataIcon := "g")(
                    span(
                      strong(teamTournament()),
                      em(teamTournamentOverview())
                    )
                  ),
                  isGranted(_.Beta) option a(href := s"${routes.Swiss.form(t.id)}", cls := "button button-empty text", dataIcon := "g")(
                    span(
                      strong(swissTournament()),
                      em(swissTournamentOverview())
                    )
                  ),
                  a(href := routes.Team.pmAll(t.id), cls := "button button-empty text", dataIcon := "e")(
                    span(
                      strong(messageAllMembers()),
                      em(messageAllMembersOverview())
                    )
                  )
                ),
                (info.createdByMe || isGranted(_.Admin)) option
                  a(href := routes.Team.edit(t.id), cls := "button button-empty text", dataIcon := "%")(trans.settings()),
                (t.isWfd && (info.createdByMe || isGranted(_.ManageWfd))) option
                  a(href := routes.Team.wfd(t.id), cls := "button button-empty text", dataIcon := "%")(s"WFD profiles")
              ),
              div(cls := "team-show__members")(
                st.section(cls := "recent-members")(
                  h2(teamRecentMembers()),
                  div(cls := "userlist infinitescroll")(
                    pagerNext(members, np => routes.Team.show(t.id, np).url),
                    members.currentPageResults.map {
                      case Left(member) => div(cls := "paginated")(lightUserLink(member))
                      case Right(wfdMember) => div(cls := "paginated")(lightWfdUserLink(wfdMember))
                    }
                  )
                )
              )
            ),
            div(cls := "team-show__content__col2")(
              st.section(cls := "team-show__desc")(
                richText(t.description),
                t.location.map { loc =>
                  frag(br, trans.location(), ": ", richText(loc))
                }
              ),
              info.hasRequests option div(cls := "team-show__requests")(
                h2(xJoinRequests(info.requests.size)),
                views.html.team.request.list(info.requests, t.some)
              ),
              div(cls := "team-show__tour-forum")(
                info.featuredTours.nonEmpty option frag(
                  st.section(cls := "team-show__tour team-tournaments")(
                    h2(a(href := routes.Team.tournaments(t.id))(trans.tournaments())),
                    tournaments.widget(info.featuredTours)
                  )
                ),
                NotForKids {
                  st.section(cls := "team-show__forum")(
                    h2(a(href := teamForumUrl(t.id))(trans.forum())),
                    info.forumPosts.take(10).map { post =>
                      a(cls := "team-show__forum__post", href := routes.ForumPost.redirect(post.postId))(
                        div(cls := "meta")(
                          strong(post.topicName),
                          em(
                            post.userId map { id => if (t.isWfd) wfdUsernameOrId(id) else usernameOrId(id) },
                            " • ",
                            momentFromNow(post.createdAt)
                          )
                        ),
                        p(shorten(post.text, 200))
                      )
                    },
                    a(cls := "more", href := teamForumUrl(t.id))(t.name, " ", trans.forum(), " »")
                  )
                }
              )
            )
          )
        )
      )
}
