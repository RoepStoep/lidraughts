package lidraughts.common

import play.api.mvc.RequestHeader

object DfsInterlandCookie {

  private val cookieName = "interland"
  private val cookieSet = "1"

  def hasCookie(implicit req: RequestHeader) =
    req.cookies.get(cookieName).exists(_.value == cookieSet)

  def cookie(implicit req: RequestHeader) =
    LidraughtsCookie.cookie(
      cookieName, cookieSet,
      maxAge = 2592000.some, // 30 days
      httpOnly = true.some
    )
}
