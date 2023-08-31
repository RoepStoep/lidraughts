package lidraughts.common

import play.api.mvc.RequestHeader

object DfsInterlandCookie {

  private val cookieName = "interland"
  private val cookieLanding = "1"
  private val cookieRegistered = "2"

  def hasLandingCookie(implicit req: RequestHeader) =
    req.cookies.get(cookieName).exists(_.value == cookieLanding)

  def hasRegisteredCookie(implicit req: RequestHeader) =
    req.cookies.get(cookieName).exists(_.value == cookieRegistered)

  def cookie(registered: Boolean = false)(implicit req: RequestHeader) =
    LidraughtsCookie.cookie(
      cookieName,
      if (registered) cookieRegistered else cookieLanding,
      maxAge = 2592000.some, // 30 days
      httpOnly = true.some
    )
}
