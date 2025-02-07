package lidraughts.anaCache

import com.typesafe.config.Config

final class Env(
    config: Config,
    settingStore: lidraughts.memo.SettingStore.Builder,
    db: lidraughts.db.Env,
    system: akka.actor.ActorSystem,
    asyncCache: lidraughts.memo.AsyncCache.Builder
) {

  private val CollectionAnaCache = config getString "collection.ana_cache"

  lazy val api = new AnaCacheApi(
    coll = db(CollectionAnaCache),
    asyncCache = asyncCache
  )
}

object Env {

  lazy val current: Env = "anaCache" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "anaCache",
    settingStore = lidraughts.memo.Env.current.settingStore,
    db = lidraughts.db.Env.current,
    system = lidraughts.common.PlayApp.system,
    asyncCache = lidraughts.memo.Env.current.asyncCache
  )
}
