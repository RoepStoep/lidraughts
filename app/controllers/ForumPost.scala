package controllers

import lidraughts.app._
import lidraughts.common.{ HTTPRequest, IpAddress }
import scala.concurrent.duration._
import views._

object ForumPost extends LidraughtsController with ForumController {

  private val CreateRateLimit = new lidraughts.memo.RateLimit[IpAddress](4, 5 minutes,
    name = "forum create post",
    key = "forum.post")

  def search(text: String, page: Int) = OpenBody { implicit ctx =>
    NotForKids {
      if (text.trim.isEmpty) Redirect(routes.ForumCateg.index).fuccess
      else Env.forumSearch(text, page, isGranted(_.StaffForum), ctx.troll) map { paginator =>
        html.forum.search(text, paginator)
      }
    }
  }

  def recent = Open { implicit ctx =>
    NotForKids {
      Env.forum.recent(ctx.me, teamCache.teamIdsList) map { posts =>
        Ok(html.forum.post.recent(posts))
      }
    }
  }

  def create(categSlug: String, slug: String, page: Int) = OpenBody { implicit ctx =>
    CategGrantWrite(categSlug) {
      isWfdForum(categSlug) flatMap { isWfd =>
        implicit val req = ctx.body
        OptionFuResult(topicApi.show(categSlug, slug, page, ctx.troll)) {
          case (categ, topic, posts) =>
            if (topic.closed) fuccess(BadRequest("This topic is closed"))
            else if (topic.isOld) fuccess(BadRequest("This topic is archived"))
            else forms.post.bindFromRequest.fold(
              err => for {
                captcha <- forms.anyCaptcha
                unsub <- ctx.userId ?? Env.timeline.status(s"forum:${topic.id}")
                canModCateg <- isGrantedMod(categ.slug)
              } yield BadRequest(html.forum.topic.show(categ, topic, posts, Some(err -> captcha), unsub, canModCateg = canModCateg, isWfd = isWfd)),
              data => CreateRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
                postApi.makePost(categ, topic, data) map { post =>
                  Redirect(routes.ForumPost.redirect(post.id))
                }
              }
            )
        }
      }
    }
  }

  def edit(postId: String) = AuthBody { implicit ctx => me =>
    implicit val req = ctx.body
    forms.postEdit.bindFromRequest.fold(
      err => Redirect(routes.ForumPost.redirect(postId)).fuccess,
      data => CreateRateLimit(HTTPRequest lastRemoteAddress ctx.req) {
        postApi.editPost(postId, data.changes, me).map { post =>
          Redirect(routes.ForumPost.redirect(post.id))
        }
      }
    )
  }

  def delete(categSlug: String, id: String) = Auth { implicit ctx => me =>
    CategGrantMod(categSlug) {
      postApi.delete(categSlug, id, me) map { Ok(_) }
    }
  }

  def react(id: String, reaction: String, v: Boolean) = Auth { implicit ctx => me =>
    postApi.react(id, me, reaction, v) map {
      _ ?? { post =>
        Ok(views.html.forum.post.reactions(post, true))
      }
    }
  }

  def redirect(id: String) = Open { implicit ctx =>
    OptionResult(postApi.urlData(id, ctx.troll)) {
      case lidraughts.forum.PostUrlData(categ, topic, page, number) =>
        Redirect(routes.ForumTopic.show(categ, topic, page).url + "#" + number)
    }
  }
}
