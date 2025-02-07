package lidraughts.evalCache

import play.api.libs.json._

import draughts.format.FEN
import lidraughts.common.IpAddress
import lidraughts.socket._
import lidraughts.user.User

final class EvalCacheSocketHandler(
    api: EvalCacheApi,
    anaCacheApi: lidraughts.anaCache.AnaCacheApi,
    truster: EvalCacheTruster,
    upgrade: EvalCacheUpgrade
) {

  import EvalCacheEntry._

  def apply(uid: Socket.Uid, member: SocketMember, user: Option[User], ip: IpAddress): Handler.Controller =
    makeController(uid, member, user map truster.makeTrusted, ip)

  private def makeController(
    uid: Socket.Uid,
    member: SocketMember,
    trustedUser: Option[TrustedUser],
    ip: IpAddress
  ): Handler.Controller = {

    case ("evalPut", o) => trustedUser foreach { tu =>
      JsonHandlers.readPut(tu, o) foreach { api.put(tu, _, uid) }
    }

    case ("anaPut", o) =>
      lidraughts.anaCache.JsonHandlers.readPut(o) foreach {
        anaCacheApi.put(_, trustedUser.map(_.user.id), ip)
      }

    case ("evalGet", o) => for {
      d <- o obj "d"
      variant = draughts.variant.Variant orDefault ~d.str("variant")
      fen <- d str "fen" map FEN.apply
      multiPv = (d int "mpv") | 1
      path <- d str "path"
    } {
      api.getEvalJson(variant, fen, multiPv) foreach {
        _ foreach { json =>
          member push Socket.makeMessage("evalHit", json + ("path" -> JsString(path)))
        }
      }
      if (d.value contains "up")
        upgrade.register(uid, member, variant, fen, multiPv, path)
    }
  }
}
