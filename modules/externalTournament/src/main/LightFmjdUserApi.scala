package lidraughts.externalTournament

import reactivemongo.bson._

import lidraughts.common.LightFmjdUser
import lidraughts.db.dsl._
import lidraughts.memo.Syncache

import scala.concurrent.duration._

final class LightFmjdUserApi(coll: Coll)(implicit system: akka.actor.ActorSystem) {

  import LightFmjdUserApi._

  def sync(id: FmjdPlayer.ID): Option[LightFmjdUser] = cache sync id
  def async(id: FmjdPlayer.ID): Fu[Option[LightFmjdUser]] = cache async id

  def asyncMany = cache.asyncMany _

  def invalidate = cache invalidate _

  def preloadOne = cache preloadOne _
  def preloadMany = cache preloadMany _
  def preloadSet = cache preloadSet _

  private val cacheName = "externalTournament.lightFmjd"

  private val cache = new Syncache[FmjdPlayer.ID, Option[LightFmjdUser]](
    name = cacheName,
    compute = id => coll.find($id(id), projection).uno[LightFmjdUser],
    default = _ => none[LightFmjdUser],
    strategy = Syncache.WaitAfterUptime(10 millis),
    expireAfter = Syncache.ExpireAfterAccess(15 minutes),
    logger = logger branch "LightFmjdUserApi"
  )

  def monitorCache = lidraughts.mon.syncache.chmSize(cacheName)(cache.chmSize)
}

private object LightFmjdUserApi {

  implicit val lightFmjdUserBSONReader = new BSONDocumentReader[LightFmjdUser] {

    def read(doc: BSONDocument) = {
      val firstName = doc.getAs[String]("firstName") err "FmjdPlayer firstName missing"
      val lastName = doc.getAs[String]("lastName") err "FmjdPlayer lastName missing"
      val title = doc.getAs[String]("title")
      LightFmjdUser(
        id = doc.getAs[FmjdPlayer.ID]("_id") err "FmjdPlayer id missing",
        name = FmjdPlayer.toDisplayName(firstName, lastName),
        title = if (title.isDefined) title else doc.getAs[String]("titleW")
      )
    }
  }

  val projection = $doc("firstName" -> true, "lastName" -> true, "title" -> true, "titleW" -> true)
}

