package controllers

import play.api.libs.json._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.common.LightUser.lightUserWrites
import lidraughts.i18n.{ I18nKeys => trans, I18nLangPicker, enLang }

object Dasher extends LidraughtsController {

  private val translationsBase = List(
    trans.networkLagBetweenYouAndLidraughts,
    trans.timeToProcessAMoveOnLidraughtsServer,
    trans.sound,
    trans.background,
    trans.light,
    trans.dark,
    trans.transparent,
    trans.backgroundImageUrl,
    trans.boardTheme,
    trans.boardSize,
    trans.pieceSet,
    trans.preferences.zenMode
  )

  private val translationsAnon = List(
    trans.signIn,
    trans.signUp
  ) ::: translationsBase

  private val translationsAuth = List(
    trans.profile,
    trans.inbox,
    trans.preferences.preferences,
    trans.logOut
  ) ::: translationsBase

  private def translations(implicit ctx: Context) = lidraughts.i18n.JsDump.keysToObject(
    if (ctx.isAnon) translationsAnon else translationsAuth,
    ctx.lang
  ) ++ lidraughts.i18n.JsDump.keysToObject(
      // the language settings should never be in a totally foreign language
      List(trans.language),
      if (I18nLangPicker.allFromRequestHeaders(ctx.req).has(ctx.lang)) ctx.lang
      else I18nLangPicker.bestFromRequestHeaders(ctx.req) | enLang
    )

  def get = Open { implicit ctx =>
    negotiate(
      html = notFound,
      api = _ => ctx.me.??(Env.streamer.api.isStreamer) map { isStreamer =>
        Ok {
          Json.obj(
            "user" -> ctx.me.map(_.light),
            "lang" -> Json.obj(
              "current" -> ctx.lang.code,
              "accepted" -> I18nLangPicker.allFromRequestHeaders(ctx.req).map(_.code)
            ),
            "sound" -> Json.obj(
              "list" -> lidraughts.pref.SoundSet.list.map { set =>
                s"${set.key} ${set.name}"
              }
            ),
            "background" -> Json.obj(
              "current" -> ctx.currentBg,
              "image" -> ctx.pref.bgImgOrDefault
            ),
            "board" -> Json.obj(),
            "theme" -> Json.obj(
              "d2" -> Json.obj(
                "current" -> ctx.currentTheme.name,
                "list" -> lidraughts.pref.Theme.all.map(_.name)
              )
            ),
            "piece" -> Json.obj(
              "d2" -> Json.obj(
                "current" -> ctx.currentPieceSet.name,
                "list" -> lidraughts.pref.PieceSet.all.map(_.name)
              )
            ),
            "kid" -> ctx.me ?? (_.kid),
            "coach" -> isGranted(_.Coach),
            "streamer" -> isStreamer,
            "zen" -> ctx.pref.zen,
            "i18n" -> translations
          )
        }
      }
    )
  }
}
