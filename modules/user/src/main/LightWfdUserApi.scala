package lidraughts.user

import reactivemongo.bson._
import scala.concurrent.duration._

import lidraughts.common.LightWfdUser
import lidraughts.db.dsl._
import lidraughts.memo.Syncache
import User.{ BSONFields => F }

final class LightWfdUserApi(coll: Coll)(implicit system: akka.actor.ActorSystem) {

  import LightWfdUserApi._

  def sync(id: User.ID): Option[LightWfdUser] = cache sync id
  def async(id: User.ID): Fu[Option[LightWfdUser]] = cache async id

  def asyncMany = cache.asyncMany _

  def invalidate = cache invalidate _

  def preloadOne = cache preloadOne _
  def preloadMany = cache preloadMany _

  private val cacheName = "user.wfd"

  private val cache = new Syncache[User.ID, Option[LightWfdUser]](
    name = cacheName,
    compute = id => coll.find($id(id), projection).uno[LightWfdUser],
    default = id => LightWfdUser(id, id, None, false).some,
    strategy = Syncache.WaitAfterUptime(20 millis),
    expireAfter = Syncache.ExpireAfterAccess(15 minutes),
    logger = logger branch "LightWfdUserApi"
  )

}

private object LightWfdUserApi {

  implicit val lightUserBSONReader = new BSONDocumentReader[LightWfdUser] {

    private implicit def profileWfdHandler = ProfileWfd.profileWfdBSONHandler

    def read(doc: BSONDocument) = {
      val username = doc.getAs[String](F.username) err "LightUser username missing"
      LightWfdUser(
        name = doc.getAs[ProfileWfd](F.profileWfd).flatMap(_.nonEmptyRealName) getOrElse username,
        username = username,
        title = doc.getAs[String](F.title),
        isPatron = ~doc.getAs[Bdoc](F.plan).flatMap(_.getAs[Boolean]("active"))
      )
    }
  }

  val projection = $doc(F.username -> true, F.title -> true, s"${F.plan}.active" -> true, F.profileWfd -> true)
}