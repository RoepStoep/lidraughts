package lidraughts.app
package templating

import controllers.routes
import mashup._

import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.{ LightUser, LightWfdUser, Lang }
import lidraughts.i18n.{ I18nKeys => trans }
import lidraughts.rating.{ PerfType, Perf }
import lidraughts.user.{ ProfileWfd, User, Title }

trait UserHelper { self: I18nHelper with StringHelper with NumberHelper =>

  def ratingProgress(progress: Int) =
    if (progress > 0) goodTag(cls := "rp")(progress)
    else if (progress < 0) badTag(cls := "rp")(math.abs(progress))
    else emptyFrag

  def showPerfRating(rating: Int, name: String, nb: Int, provisional: Boolean, icon: Char)(implicit lang: Lang): Frag =
    span(
      title := s"$name rating over ${nb.localize} games",
      dataIcon := icon,
      cls := "text"
    )(
        if (nb > 0) frag(rating, provisional option "?")
        else frag(nbsp, nbsp, nbsp, "-")
      )

  def showPerfRating(perfType: PerfType, perf: Perf)(implicit lang: Lang): Frag =
    showPerfRating(perf.intRating, perfType.name, perf.nb, perf.provisional, perfType.iconChar)

  def showPerfRating(u: User, perfType: PerfType)(implicit lang: Lang): Frag =
    showPerfRating(perfType, u perfs perfType)

  def showPerfRating(u: User, perfKey: String)(implicit lang: Lang): Option[Frag] =
    PerfType(perfKey) map { showPerfRating(u, _) }

  def showBestPerf(u: User)(implicit lang: Lang): Option[Frag] = u.perfs.bestPerf map {
    case (pt, perf) => showPerfRating(pt, perf)
  }
  def showBestPerfs(u: User, nb: Int)(implicit lang: Lang): List[Frag] =
    u.perfs.bestPerfs(nb) map {
      case (pt, perf) => showPerfRating(pt, perf)
    }

  def showRatingDiff(diff: Int): Frag = diff match {
    case 0 => span("±0")
    case d if d > 0 => goodTag(s"+$d")
    case d => badTag(s"−${-d}")
  }

  def lightUser(userId: String): Option[LightUser] = Env.user lightUserSync userId
  def lightUser(userId: Option[String]): Option[LightUser] = userId flatMap lightUser

  def lightWfdUser(userId: String): Option[LightWfdUser] = Env.user lightWfdUserSync userId
  def lightWfdUser(userId: Option[String]): Option[LightWfdUser] = userId flatMap lightWfdUser

  // def lightUserSync: LightUser.SyncGetter(userId: String): Option[LightUser] = Env.user lightUserSync userId

  def usernameOrId(userId: String) = lightUser(userId).fold(userId)(_.titleName)
  def usernameOrAnon(userId: Option[String]) = lightUser(userId).fold(User.anonymous)(_.titleName)

  def wfdUsernameOrId(userId: String) = lightWfdUser(userId).fold(userId)(_.titleName)

  def isOnline(userId: String) = Env.user isOnline userId

  def isStreaming(userId: String) = Env.streamer.liveStreamApi isStreaming userId

  def userIdLink(
    userIdOption: Option[String],
    cssClass: Option[String] = None,
    withOnline: Boolean = true,
    withTitle: Boolean = true,
    truncate: Option[Int] = None,
    params: String = "",
    modIcon: Boolean = false,
    isWfd: Boolean = false
  )(implicit lang: Lang): Frag =
    if (isWfd) userIdOption.flatMap(lightWfdUser).fold[Frag](User.anonymous) { user =>
      userIdNameLink(
        userId = user.id,
        username = user.username,
        isPatron = user.isPatron,
        title = withTitle ?? user.title map Title.apply,
        cssClass = cssClass,
        withOnline = withOnline,
        truncate = truncate,
        params = params,
        modIcon = modIcon,
        displayName = user.name.some
      )
    }
    else userIdOption.flatMap(lightUser).fold[Frag](User.anonymous) { user =>
      userIdNameLink(
        userId = user.id,
        username = user.name,
        isPatron = user.isPatron,
        title = withTitle ?? user.title map Title.apply,
        cssClass = cssClass,
        withOnline = withOnline,
        truncate = truncate,
        params = params,
        modIcon = modIcon
      )
    }

  def lightUserLink(
    user: LightUser,
    cssClass: Option[String] = None,
    withOnline: Boolean = true,
    withTitle: Boolean = true,
    truncate: Option[Int] = None,
    params: String = ""
  )(implicit lang: Lang): Tag = userIdNameLink(
    userId = user.id,
    username = user.name,
    isPatron = user.isPatron,
    title = withTitle ?? user.title map Title.apply,
    cssClass = cssClass,
    withOnline = withOnline,
    truncate = truncate,
    params = params,
    modIcon = false
  )

  def lightWfdUserLink(
    user: LightWfdUser,
    cssClass: Option[String] = None,
    withOnline: Boolean = true,
    withTitle: Boolean = true,
    truncate: Option[Int] = None,
    params: String = ""
  )(implicit lang: Lang): Tag = userIdNameLink(
    userId = user.id,
    username = user.username,
    isPatron = user.isPatron,
    title = withTitle ?? user.title map Title.apply,
    cssClass = cssClass,
    withOnline = withOnline,
    truncate = truncate,
    params = params,
    modIcon = false,
    displayName = user.name.some
  )

  def userIdLink(
    userId: String,
    cssClass: Option[String]
  )(implicit lang: Lang): Frag = userIdLink(userId.some, cssClass)

  def titleTag(title: Option[Title]): Option[Frag] = title map { t =>
    val title64 = t.is64
    frag(
      span(
        cls := "title",
        dataBot(t),
        title64 option dataTitle64,
        st.title := Title.titleName(t)
      )(if (title64) t.value.dropRight(3) else t.value),
      nbsp
    )
  }
  def titleTag(lu: LightUser): Frag = titleTag(lu.title map Title.apply)

  private def userIdNameLink(
    userId: String,
    username: String,
    isPatron: Boolean,
    cssClass: Option[String],
    withOnline: Boolean,
    truncate: Option[Int],
    title: Option[Title],
    params: String,
    modIcon: Boolean,
    displayName: Option[String] = None
  )(implicit lang: Lang): Tag = a(
    cls := userClass(userId, cssClass, withOnline),
    href := userUrl(username, params = params)
  )(
      withOnline ?? (if (modIcon) moderatorIcon else lineIcon(isPatron)),
      titleTag(title),
      truncate.fold(displayName.getOrElse(username))(displayName.getOrElse(username).take)
    )

  def userLink(
    user: User,
    cssClass: Option[String] = None,
    withOnline: Boolean = true,
    withPowerTip: Boolean = true,
    withTitle: Boolean = true,
    withBestRating: Boolean = false,
    withPerfRating: Option[PerfType] = None,
    params: String = "",
    withProfileName: Boolean = false
  )(implicit lang: Lang): Tag = a(
    cls := userClass(user.id, cssClass, withOnline, withPowerTip),
    href := userUrl(user.username, params)
  )(
      withOnline ?? lineIcon(user),
      withTitle option titleTag(user.title),
      if (withProfileName && !withPowerTip) span(user.username, title := user.profileOrDefault.nonEmptyRealName) else user.username,
      userRating(user, withPerfRating, withBestRating)
    )

  def userSpan(
    user: User,
    cssClass: Option[String] = None,
    withOnline: Boolean = true,
    withPowerTip: Boolean = true,
    withTitle: Boolean = true,
    withBestRating: Boolean = false,
    withPerfRating: Option[PerfType] = None,
    text: Option[String] = None
  )(implicit lang: Lang): Frag = span(
    cls := userClass(user.id, cssClass, withOnline, withPowerTip),
    dataHref := userUrl(user.username)
  )(
      withOnline ?? lineIcon(user),
      withTitle option titleTag(user.title),
      text | user.username,
      userRating(user, withPerfRating, withBestRating)
    )

  def userIdSpanMini(userId: String, withOnline: Boolean = false)(implicit lang: Lang): Frag = {
    val user = lightUser(userId)
    val name = user.fold(userId)(_.name)
    span(
      cls := userClass(userId, none, withOnline),
      dataHref := userUrl(name)
    )(
        withOnline ?? lineIcon(user),
        user.??(u => titleTag(u.title map Title.apply)),
        name
      )
  }

  private def renderRating(perf: Perf): Frag = frag(
    " (",
    perf.intRating,
    perf.provisional option "?",
    ")"
  )

  private def userRating(user: User, withPerfRating: Option[PerfType], withBestRating: Boolean): Frag =
    withPerfRating match {
      case Some(perfType) => renderRating(user.perfs(perfType))
      case _ if withBestRating => user.perfs.bestPerf ?? {
        case (_, perf) => renderRating(perf)
      }
      case _ => ""
    }

  private def userUrl(username: String, params: String = "") =
    s"""${routes.User.show(username)}$params"""

  protected def userClass(
    userId: String,
    cssClass: Option[String],
    withOnline: Boolean,
    withPowerTip: Boolean = true
  ): List[(String, Boolean)] =
    (withOnline ?? List((if (isOnline(userId)) "online" else "offline") -> true)) ::: List(
      "user-link" -> true,
      ~cssClass -> cssClass.isDefined,
      "ulpt" -> withPowerTip
    )

  def userGameFilterTitle(u: User, nbs: UserInfo.NbGames, filter: GameFilter)(implicit lang: Lang): Frag =
    if (filter == GameFilter.Search) frag(br, trans.search.advancedSearch())
    else splitNumber(userGameFilterTitleNoTag(u, nbs, filter))

  def userGameFilterTitleNoTag(u: User, nbs: UserInfo.NbGames, filter: GameFilter)(implicit lang: Lang): String = (filter match {
    case GameFilter.All => trans.nbGames.pluralSameTxt(u.count.game)
    case GameFilter.Me => nbs.withMe ?? trans.nbGamesWithYou.pluralSameTxt
    case GameFilter.Rated => trans.nbRated.pluralSameTxt(u.count.rated)
    case GameFilter.Win => trans.nbWins.pluralSameTxt(u.count.win)
    case GameFilter.Loss => trans.nbLosses.pluralSameTxt(u.count.loss)
    case GameFilter.Draw => trans.nbDraws.pluralSameTxt(u.count.draw)
    case GameFilter.Playing => trans.nbPlaying.pluralSameTxt(nbs.playing)
    case GameFilter.Bookmark => trans.nbBookmarks.pluralSameTxt(nbs.bookmark)
    case GameFilter.Imported => trans.nbImportedGames.pluralSameTxt(nbs.imported)
    case GameFilter.Search => trans.search.advancedSearch.txt()
  })

  def describeUser(user: User) = {
    val name = user.titleUsername
    val nbGames = user.count.game
    val createdAt = org.joda.time.format.DateTimeFormat forStyle "M-" print user.createdAt
    val currentRating = user.perfs.bestPerf ?? {
      case (pt, perf) => s" Current ${pt.name} rating: ${perf.intRating}."
    }
    s"$name played $nbGames games since $createdAt.$currentRating"
  }

  val patronIconChar = ""
  val lineIconChar = ""

  val lineIcon: Frag = i(cls := "line")
  def patronIcon(implicit lang: Lang): Frag = i(cls := "line patron", title := trans.patron.lidraughtsPatron.txt())
  val moderatorIcon: Frag = i(cls := "line moderator", title := "Lidraughts Mod")
  private def lineIcon(patron: Boolean)(implicit lang: Lang): Frag = if (patron) patronIcon else lineIcon
  private def lineIcon(user: Option[LightUser])(implicit lang: Lang): Frag = lineIcon(user.??(_.isPatron))
  def lineIcon(user: LightUser)(implicit lang: Lang): Frag = lineIcon(user.isPatron)
  def lineIcon(user: User)(implicit lang: Lang): Frag = lineIcon(user.isPatron)
  def lineIconChar(user: User): Frag = if (user.isPatron) patronIconChar else lineIconChar
}
