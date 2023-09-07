package controllers

import play.api.libs.json._
import scala.concurrent.duration._
import views._

import lidraughts.app._
import lidraughts.common.HTTPRequest
import lidraughts.timeline.Entry.entryWrites

object Timeline extends LidraughtsController {

  def home = Auth { implicit ctx => me =>
    lidraughts.mon.http.response.timeline.count()
    negotiate(
      html =
        if (HTTPRequest.isXhr(ctx.req)) for {
          entries <- Env.timeline.entryApi.userEntries(me.id)
            .logTimeIfGt(s"timeline site entries for ${me.id}", 10 seconds)
          _ <- Env.user.lightUserApi.preloadMany(entries.flatMap(_.userIds))
        } yield { html.timeline.entries(entries) }
        else for {
          entries <- Env.timeline.entryApi.moreUserEntries(me.id, 30)
            .logTimeIfGt(s"timeline site more entries (30) for ${me.id}", 10 seconds)
          _ <- Env.user.lightUserApi.preloadMany(entries.flatMap(_.userIds))
        } yield { html.timeline.more(entries) },
      _ => {
        // Must be empty if nb is not given, because old versions of the
        // mobile app that do not send nb are vulnerable to XSS in
        // timeline entries.
        val nb = (getInt("nb") | 0) atMost 20
        for {
          entries <- Env.timeline.entryApi.moreUserEntries(me.id, nb)
            .logTimeIfGt(s"timeline mobile $nb for ${me.id}", 10 seconds)
          users <- Env.user.lightUserApi.asyncManyFallback(entries.flatMap(_.userIds).distinct)
          userMap = users.view.map { u => u.id -> u }.toMap
        } yield Ok(Json.obj("entries" -> entries, "users" -> userMap))
      }
    ).mon(_.http.response.timeline.time)
  }

  def unsub(channel: String) = Auth { implicit ctx => me =>
    Env.timeline.unsubApi.set(channel, me.id, ~get("unsub") == "on")
  }
}
