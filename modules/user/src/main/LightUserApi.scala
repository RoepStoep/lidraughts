package lidraughts.user

import reactivemongo.bson._
import scala.concurrent.duration._

import lidraughts.common.LightUser
import lidraughts.db.dsl._
import lidraughts.memo.Syncache
import User.{ BSONFields => F }

final class LightUserApi(coll: Coll)(implicit system: akka.actor.ActorSystem) {

  import LightUserApi._

  def sync(id: User.ID): Option[LightUser] = cache sync id
  def async(id: User.ID): Fu[Option[LightUser]] = cache async id

  def syncFallback(id: User.ID) = sync(id) | LightUser.fallback(id)
  def asyncFallback(id: User.ID) = async(id) dmap (_ | LightUser.fallback(id))

  def asyncMany = cache.asyncMany _

  def asyncManyFallback(ids: Seq[User.ID]): Fu[Seq[LightUser]] =
    ids.map(asyncFallback).sequenceFu

  def invalidate = cache invalidate _

  def preloadOne = cache preloadOne _
  def preloadMany = cache preloadMany _
  def preloadUser(user: User) = cache.setOneIfAbsent(user.id, user.light.some)

  def isBotSync(id: User.ID) = sync(id).exists(_.isBot)

  private val cacheName = "user.light"

  private val cache = new Syncache[User.ID, Option[LightUser]](
    name = cacheName,
    compute = id => coll.find($id(id), projection).uno[LightUser],
    default = id => LightUser(id, id, None, false).some,
    strategy = Syncache.WaitAfterUptime(10 millis),
    expireAfter = Syncache.ExpireAfterAccess(15 minutes),
    logger = logger branch "LightUserApi"
  )

  def monitorCache = lidraughts.mon.syncache.chmSize(cacheName)(cache.chmSize)
}

private object LightUserApi {

  implicit val lightUserBSONReader = new BSONDocumentReader[LightUser] {

    def read(doc: BSONDocument) = LightUser(
      id = doc.getAs[User.ID](F.id) err "LightUser id missing",
      name = doc.getAs[String](F.username) err "LightUser username missing",
      title = doc.getAs[String](F.title),
      isPatron = ~doc.getAs[Bdoc](F.plan).flatMap(_.getAs[Boolean]("active"))
    )
  }

  val projection = $doc(F.username -> true, F.title -> true, s"${F.plan}.active" -> true)
}
