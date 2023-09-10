package lidraughts.security

import play.api.mvc.RequestHeader

import lidraughts.common.HTTPRequest._

/* CSRF protection by using the HTTP origin header.
 * This applies to all incoming HTTP requests, and therefore, all forms of the site.
 * The origin header is set by the browser, and cannot be forged in cross-site requests.
 * Read along the code comments for details.
 */
final class CSRFRequestHandler(domain: String, enabled: Boolean) {

  private def logger = lidraughts.log("csrf")

  /* Returns true if the request can be accepted
   * Returns false to reject the request with 403 Forbidden
   */
  def check(req: RequestHeader): Boolean = {
    /* Cross origin XHR is not allowed by browsers,
     * therefore all XHR requests can be accepted
     */
    if (isXhr(req)) true
    /* GET, HEAD and OPTIONS never modify the server data,
     * so we accept them
     */
    else if (isSafe(req) && !isSocket(req)) true
    /* The origin header is set to a known value used by the mobile app,
     * so we accept it */
    else if (appOrigin(req).isDefined) true
    else origin(req) match {
      case None =>
        /* The origin header is not set.
           * This can only happen with very old browsers,
           * which support was dropped a long time ago, and that are full of other vulnerabilities.
           * These old browsers cannot load Lichess because Lichess only support modern TLS.
           * All the browsers that can run Lichess nowadays set the origin header properly.
           * The absence of the origin header usually indicates a programmatic call (API or scrapping),
           * so we let these requests through.
           */
        lidraughts.mon.http.csrf.missingOrigin()
        logger.debug(print(req))
        true
      case Some(o) if isSubdomain(o) =>
        /* The origin header is set to the lichess domain, or a subdomain of it.
           * Since the request comes from Lichess, we accept it.
           */
        true
      case Some(_) =>
        /* The origin header is set to another value, like a domain or "null".
           * We reject the request.
           * Note that in the case of an HTTP 302 redirect,
           * or when privacy requires it, then the origin header IS SET, and contains "null",
           * causing the unsafe request to be rejected.
           */
        if (isSocket(req)) {
          lidraughts.mon.http.csrf.websocket()
          logger.info(s"WS ${origin(req)}: ${print(req)}")
        } else {
          lidraughts.mon.http.csrf.forbidden()
          logger.info(s"${origin(req)}: ${print(req)}")
        }
        !enabled
    }
  }

  private val topDomain = s"://$domain"
  private val subDomain = s".$domain"

  // origin = "https://lidraughts.org"
  // domain = "lidraughts.org"
  private def isSubdomain(origin: String) =
    origin.endsWith(subDomain) || origin.endsWith(topDomain)
}
