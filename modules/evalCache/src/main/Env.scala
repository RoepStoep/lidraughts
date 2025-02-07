package lidraughts.evalCache

import com.typesafe.config.Config

final class Env(
    config: Config,
    settingStore: lidraughts.memo.SettingStore.Builder,
    db: lidraughts.db.Env,
    system: akka.actor.ActorSystem,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    anaCacheApi: lidraughts.anaCache.AnaCacheApi
) {

  private val CollectionEvalCache = config getString "collection.eval_cache"

  private lazy val truster = new EvalCacheTruster

  private lazy val upgrade = new EvalCacheUpgrade(asyncCache)

  lazy val api = new EvalCacheApi(
    coll = db(CollectionEvalCache),
    truster = truster,
    upgrade = upgrade,
    asyncCache = asyncCache
  )

  lazy val socketHandler = new EvalCacheSocketHandler(
    api = api,
    anaCacheApi = anaCacheApi,
    truster = truster,
    upgrade = upgrade
  )

  system.lidraughtsBus.subscribeFun('socketLeave) {
    case lidraughts.socket.actorApi.SocketLeave(uid, _) => upgrade unregister uid
  }

  def cli = new lidraughts.common.Cli {
    def process = {
      case "eval-cache" :: "drop" :: variant :: fenParts =>
        draughts.variant.Variant.byKey.get(variant) ?? { v =>
          api.drop(v, draughts.format.FEN(fenParts mkString " ")) inject "done!"
        }
    }
  }
}

object Env {

  lazy val current: Env = "evalCache" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "evalCache",
    settingStore = lidraughts.memo.Env.current.settingStore,
    db = lidraughts.db.Env.current,
    system = lidraughts.common.PlayApp.system,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    anaCacheApi = lidraughts.anaCache.Env.current.api
  )
}
