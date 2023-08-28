package views.html
package forum

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.paginator.Paginator

import controllers.routes

object categ {

  def index(categs: List[lidraughts.forum.CategView])(implicit ctx: Context) = views.html.base.layout(
    title = trans.forum.txt(),
    moreCss = cssTag("forum"),
    openGraph = lidraughts.app.ui.OpenGraph(
      title = "Lidraughts community forum",
      url = s"$netBaseUrl${routes.ForumCateg.index.url}",
      description = "Draughts discussions and feedback about lidraughts development"
    ).some
  ) {
      main(cls := "forum index box")(
        div(cls := "box__top")(
          h1(dataIcon := "d", cls := "text")("Lidraughts Forum"),
          bits.searchForm()
        ),
        showCategs(categs.filterNot(_.categ.isTeam)),
        categs.exists(_.categ.isTeam) option frag(
          h1("Your teams boards"),
          showCategs(categs.filter(_.categ.isTeam))
        )
      )
    }

  def show(
    categ: lidraughts.forum.Categ,
    topics: Paginator[lidraughts.forum.TopicView],
    canWrite: Boolean,
    stickyPosts: List[lidraughts.forum.TopicView],
    isWfd: Boolean
  )(implicit ctx: Context) = {

    val newTopicButton = canWrite option
      a(href := routes.ForumTopic.form(categ.slug), cls := "button button-empty button-green text", dataIcon := "m")(
        trans.createANewTopic()
      )
    def showTopic(sticky: Boolean)(topic: lidraughts.forum.TopicView) = tr(cls := List("sticky" -> sticky))(
      td(cls := "subject")(
        a(href := routes.ForumTopic.show(categ.slug, topic.slug))(topic.name)
      ),
      td(cls := "right")(topic.views.localize),
      td(cls := "right")(topic.nbReplies.localize),
      td(
        topic.lastPost.map { post =>
          frag(
            a(href := s"${routes.ForumTopic.show(categ.slug, topic.slug, topic.lastPage)}#${post.number}")(
              momentFromNow(post.createdAt)
            ),
            br,
            authorLink(post, isWfd = isWfd)
          )
        }
      )
    )
    val bar = div(cls := "bar")(
      bits.pagination(routes.ForumCateg.show(categ.slug, 1), topics, showPost = false),
      newTopicButton
    )

    views.html.base.layout(
      title = categ.name,
      moreCss = cssTag("forum"),
      openGraph = lidraughts.app.ui.OpenGraph(
        title = s"Forum: ${categ.name}",
        url = s"$netBaseUrl${routes.ForumCateg.show(categ.slug).url}",
        description = categ.desc
      ).some
    ) {
        val content = frag(
          h1(
            a(
              href := categ.team.fold(routes.ForumCateg.index)(routes.Team.show(_)),
              dataIcon := "I",
              cls := "text"
            ),
            categ.team.fold(frag(categ.name))(teamIdToName)
          ),
          bar,
          table(cls := "topics slist slist-pad")(
            thead(
              tr(
                th,
                th(cls := "right")(trans.views()),
                th(cls := "right")(trans.replies()),
                th(trans.lastPost())
              )
            ),
            tbody(
              stickyPosts map showTopic(true),
              topics.currentPageResults map showTopic(false)
            )
          ),
          bar
        )
        if (categ.isStaff)
          main(cls := "page-menu")(
            views.html.mod.menu("forum"),
            div(cls := "forum forum-categ box page-menu__content")(content)
          )
        else main(cls := "forum forum-categ box")(content)
      }
  }

  private def showCategs(categs: List[lidraughts.forum.CategView])(implicit ctx: Context) =
    table(cls := "categs slist slist-pad")(
      thead(
        tr(
          th,
          th(cls := "right")(trans.topics()),
          th(cls := "right")(trans.posts()),
          th(trans.lastPost())
        )
      ),
      tbody(
        categs.map { categ =>
          (!categ.categ.isStaff || isGranted(_.StaffForum)) option tr(
            td(cls := "subject")(
              h2(a(href := routes.ForumCateg.show(categ.slug))(categ.name)),
              p(categ.desc)
            ),
            td(cls := "right")(categ.nbTopics.localize),
            td(cls := "right")(categ.nbPosts.localize),
            td(
              categ.lastPost.map {
                case (topic, post, page) => frag(
                  a(href := s"${routes.ForumTopic.show(categ.slug, topic.slug, page)}#${post.number}")(
                    momentFromNow(post.createdAt)
                  ),
                  br,
                  trans.by(authorName(post))
                )
              }
            )
          )
        }
      )
    )
}
