package views.html.base

import play.api.libs.json.Json

import lidraughts.api.{ Context, AnnounceStore }
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.common.{ Lang, ContentSecurityPolicy }
import lidraughts.pref.Pref

import controllers.routes

object layout {

  object bits {
    val doctype = raw("<!DOCTYPE html>")
    def htmlTag(implicit lang: Lang) = html(st.lang := lang.code)
    val topComment = raw("""<!-- Lidraughts is open source, a fork of Lichess! See https://github.com/roepstoep/lidraughts -->""")
    val charset = raw("""<meta charset="utf-8">""")
    val viewport = raw("""<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">""")
    def metaCsp(csp: ContentSecurityPolicy): Frag = raw {
      s"""<meta http-equiv="Content-Security-Policy" content="$csp">"""
    }
    def metaCsp(csp: Option[ContentSecurityPolicy])(implicit ctx: Context): Frag = metaCsp(csp getOrElse defaultCsp)
    def metaThemeColor(implicit ctx: Context): Frag = raw {
      s"""<meta name="theme-color" content="${ctx.pref.themeColor}">"""
    }
    def pieceSprite(implicit ctx: Context): Frag = pieceSprite(ctx.currentPieceSet)
    def pieceSprite(ps: lidraughts.pref.PieceSet): Frag =
      link(id := "piece-sprite", href := assetUrl(s"piece-css/$ps.css"), tpe := "text/css", rel := "stylesheet")
  }
  import bits._

  private val noTranslate = raw("""<meta name="google" content="notranslate">""")
  private def fontPreload = raw(s"""<link rel="preload" href="${assetUrl(s"font/lidraughts.woff2")}" as="font" type="font/woff2" crossorigin>""")
  private val manifests = raw(s"""<link rel="manifest" href="/manifest.json">""")

  private val favicons = raw {
    List(512, 256, 192, 128, 64) map { px =>
      s"""<link rel="icon" type="image/png" href="${staticUrl(s"favicon.$px.png")}" sizes="${px}x${px}">"""
    } mkString ("", "", s"""<link id="favicon" rel="icon" type="image/png" href="${staticUrl("images/favicon-32-white.png")}" sizes="32x32">""")
  }
  private def blindModeForm(implicit ctx: Context) = raw(s"""<form id="blind-mode" action="${routes.Main.toggleBlindMode}" method="POST"><input type="hidden" name="enable" value="${if (ctx.blind) 0 else 1}"><input type="hidden" name="redirect" value="${ctx.req.path}"><button type="submit">Accessibility: ${if (ctx.blind) "Disable" else "Enable"} blind mode</button></form>""")
  private val zenToggle = raw("""<a data-icon="E" id="zentog" class="text fbt active">ZEN MODE</a>""")
  private def dasher(me: lidraughts.user.User) = raw(s"""<div class="dasher"><a id="user_tag" class="toggle link">${me.username}</a><div id="dasher_app" class="dropdown"></div></div>""")

  private def allNotifications(implicit ctx: Context) = spaceless(s"""<div>
  <a id="challenge-toggle" class="toggle link">
    <span title="${trans.challenge.challenges().render}" class="data-count" data-count="${ctx.nbChallenges}" data-icon="U"></span>
  </a>
  <div id="challenge-app" class="dropdown"></div>
</div>
<div>
  <a id="notify-toggle" class="toggle link">
    <span title="${trans.notifications().render}" class="data-count" data-count="${ctx.nbNotifications}" data-icon=""></span>
  </a>
  <div id="notify-app" class="dropdown"></div>
</div>""")

  private def anonDasher(playing: Boolean)(implicit ctx: Context) = spaceless(s"""<div class="dasher">
  <a class="toggle link anon">
    <span title="${trans.preferences.preferences().render}" data-icon="%"></span>
  </a>
  <div id="dasher_app" class="dropdown" data-playing="$playing"></div>
</div>
<a href="${routes.Auth.login}?referrer=${ctx.req.path}" class="signin button button-empty">${trans.signIn().render}</a>""")

  private val clinputLink = a(cls := "link")(span(dataIcon := "y"))

  private def clinput(implicit ctx: Context) =
    div(id := "clinput")(
      clinputLink,
      input(
        spellcheck := "false",
        autocomplete := ctx.blind.toString,
        aria.label := trans.search.search.txt(),
        placeholder := trans.search.search.txt()
      )
    )

  private lazy val botImage = img(src := staticUrl("images/icons/bot.png"), title := "Robot draughts", style :=
    "display:inline;width:34px;height:34px;vertical-align:top;margin-right:5px;vertical-align:text-top")

  private val spaceRegex = """\s{2,}+""".r
  private def spaceless(html: String) = raw(spaceRegex.replaceAllIn(html.replace("\\n", ""), ""))

  private val dataVapid = attr("data-vapid")
  private val dataUser = attr("data-user")
  private val dataSoundSet = attr("data-sound-set")
  private val dataSocketDomain = attr("data-socket-domain")
  private val dataZoom = attr("data-zoom")
  private val dataPreload = attr("data-preload")
  private val dataNonce = attr("data-nonce")
  private val dataAnnounce = attr("data-announce")

  def apply(
    title: String,
    fullTitle: Option[String] = None,
    robots: Boolean = isGloballyCrawlable,
    moreCss: Frag = emptyFrag,
    moreJs: Frag = emptyFrag,
    playing: Boolean = false,
    openGraph: Option[lidraughts.app.ui.OpenGraph] = None,
    draughtsground: Boolean = true,
    zoomable: Boolean = false,
    deferJs: Boolean = false,
    csp: Option[ContentSecurityPolicy] = None,
    wrapClass: String = ""
  )(body: Frag)(implicit ctx: Context) = frag(
    doctype,
    htmlTag(ctx.lang)(
      topComment,
      head(
        charset,
        viewport,
        metaCsp(csp),
        metaThemeColor,
        if (isProd && !isStage) frag(
          st.headTitle(fullTitle | s"$title • lidraughts.org")
        )
        else st.headTitle(s"[dev] ${fullTitle | s"$title • lidraughts.org"}"),
        cssTag("site"),
        ctx.pageData.inquiry.isDefined option cssTagNoTheme("mod.inquiry"),
        ctx.userContext.impersonatedBy.isDefined option cssTagNoTheme("mod.impersonate"),
        ctx.blind option cssTagNoTheme("blind"),
        moreCss,
        pieceSprite,
        meta(content := openGraph.fold(trans.siteDescription.txt())(o => o.description), name := "description"),
        favicons,
        !robots option raw("""<meta content="noindex, nofollow" name="robots">"""),
        noTranslate,
        openGraph.map(_.frags),
        link(href := routes.Blog.atom, `type` := "application/atom+xml", rel := "alternate", st.title := trans.blog.txt()),
        ctx.transpBgImg map { img =>
          raw(s"""<style type="text/css" id="bg-data">body.transp::before{background-image:url('$img');}</style>""")
        },
        fontPreload,
        manifests
      ),
      st.body(
        cls := List(
          s"${ctx.currentBg} ${ctx.currentTheme.cssClass} coords-${ctx.pref.coordsClass}" -> true,
          "zen" -> ctx.pref.isZen,
          "blind-mode" -> ctx.blind,
          "kid" -> ctx.kid,
          "mobile" -> ctx.isMobileBrowser,
          "playing fixed-scroll" -> playing,
          "coords-out" -> (ctx.pref.coords == Pref.Coords.OUTSIDE),
          "standard-result" -> !ctx.pref.draughtsResult
        ),
        dataDev := (!isProd).option("true"),
        dataVapid := (ctx.userId.isDefined && isGranted(_.Beta)) option vapidPublicKey,
        dataUser := ctx.userId,
        dataSoundSet := ctx.currentSoundSet.toString,
        dataSocketDomain := socketDomain,
        dataAssetUrl := assetBaseUrl,
        dataAssetVersion := assetVersion.value,
        dataNonce := ctx.nonce.ifTrue(sameAssetDomain).map(_.value),
        dataTheme := ctx.currentBg,
        dataAnnounce := AnnounceStore.get.map(a => safeJsonValue(a.json)),
        style := zoomable option s"--zoom:${ctx.zoom}"
      )(
          blindModeForm,
          ctx.pageData.inquiry map { views.html.mod.inquiry(_) },
          ctx.me ifTrue ctx.userContext.impersonatedBy.isDefined map { views.html.mod.impersonate(_) },
          isStage option views.html.base.bits.stage,
          lidraughts.security.EmailConfirm.cookie
            .get(ctx.req)
            .ifTrue(ctx.isAnon)
            .map(views.html.auth.bits.checkYourEmailBanner(_)),
          playing option zenToggle,
          siteHeader(playing),
          div(id := "main-wrap", cls := List(
            wrapClass -> wrapClass.nonEmpty,
            "is2d" -> true
          ))(body),
          ctx.me.map { me =>
            div(
              id := "friend_box",
              dataPreload := safeJsonValue(Json.obj(
                "users" -> ctx.onlineFriends.users.map(_.fullTitleName),
                "playing" -> ctx.onlineFriends.playing,
                "patrons" -> ctx.onlineFriends.patrons,
                "studying" -> ctx.onlineFriends.studying,
                "i18n" -> i18nJsObject(List(
                  trans.nbFriendsOnline
                ))
              ))
            )(
                div(cls := "friend_box_title") {
                  val count = ctx.onlineFriends.users.size
                  trans.nbFriendsOnline.plural(count, strong(count))
                },
                div(cls := "content_wrap")(
                  div(cls := "content list"),
                  div(cls := List(
                    "nobody" -> true,
                    "none" -> ctx.onlineFriends.users.nonEmpty
                  ))(
                    span(trans.noFriendsOnline()),
                    a(cls := "find button", href := routes.User.opponents)(
                      span(cls := "is3 text", dataIcon := "h")(trans.findFriends())
                    )
                  )
                )
              )
          },
          a(id := "reconnecting", cls := "link text", dataIcon := "B")(trans.reconnecting()),
          draughtsground option jsTag("vendor/draughtsground.min.js"),
          ctx.requiresFingerprint option fingerprintTag,
          if (isProd)
            jsAt(s"compiled/lidraughts.site.min.js", defer = deferJs)
          else frag(
            jsAt(s"compiled/lidraughts.deps.js", defer = deferJs),
            jsAt(s"compiled/lidraughts.site.js", defer = deferJs)
          ),
          moreJs,
          embedJsUnsafe(s"""lidraughts.quantity=${lidraughts.i18n.JsQuantity(ctx.lang)};$timeagoLocaleScript"""),
          ctx.pageData.inquiry.isDefined option jsTag("inquiry.js", defer = deferJs)
        )
    )
  )

  object siteHeader {

    private val topnavToggle = spaceless("""
<input type="checkbox" id="tn-tg" class="topnav-toggle fullscreen-toggle" autocomplete="off" aria-label="Navigation">
<label for="tn-tg" class="fullscreen-mask"></label>
<label for="tn-tg" class="hbg"><span class="hbg__in"></span></label>""")

    private def reports(implicit ctx: Context) = isGranted(_.SeeReport) option
      a(cls := "link data-count link-center", title := "Moderation", href := routes.Report.list, dataCount := reportNbOpen, dataIcon := "")

    private def teamRequests(implicit ctx: Context) = ctx.teamNbRequests > 0 option
      a(cls := "link data-count link-center", href := routes.Team.requests, dataCount := ctx.teamNbRequests, dataIcon := "f", title := trans.team.teams.txt())

    def apply(playing: Boolean)(implicit ctx: Context) =
      header(id := "top")(
        div(cls := "site-title-nav")(
          topnavToggle,
          h1(cls := "site-title")(
            if (ctx.kid) span(title := trans.kidMode.txt(), cls := "kiddo")(":)")
            else ctx.isBot option botImage,
            a(href := "/")(
              "lidraughts",
              span(if (isProd && !isStage) ".org" else ".dev")
            )
          ),
          ctx.blind option h2("Navigation"),
          topnav()
        ),
        div(cls := "site-buttons")(
          clinput,
          reports,
          teamRequests,
          ctx.me map { me =>
            frag(allNotifications, dasher(me))
          } getOrElse { !ctx.pageData.error option anonDasher(playing) }
        )
      )
  }
}
