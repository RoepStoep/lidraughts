package lidraughts.push

import akka.actor._
import collection.JavaConverters._
import com.google.auth.oauth2.{ GoogleCredentials, ServiceAccountCredentials }
import com.typesafe.config.Config
import play.api.Play
import Play.current

import lidraughts.game.{ Game, Pov }
import lidraughts.user.User

final class Env(
    config: Config,
    db: lidraughts.db.Env,
    getLightUser: lidraughts.common.LightUser.GetterSync,
    gameProxy: Game.ID => Fu[Option[Game]],
    urgentGames: User.ID => Fu[List[Pov]],
    scheduler: lidraughts.common.Scheduler,
    system: ActorSystem
) {

  private val CollectionDevice = config getString "collection.device"
  private val CollectionSubscription = config getString "collection.subscription"

  private val OneSignalUrl = config getString "onesignal.url"
  private val OneSignalAppId = config getString "onesignal.app_id"
  private val OneSignalKey = config getString "onesignal.key"

  private val FirebaseUrl = config getString "firebase.url"

  private val WebUrl = config getString "web.url"
  val WebVapidPublicKey = config getString "web.vapid_public_key"

  private lazy val deviceApi = new DeviceApi(db(CollectionDevice))
  lazy val webSubscriptionApi = new WebSubscriptionApi(db(CollectionSubscription))

  def registerDevice = deviceApi.register _
  def unregisterDevices = deviceApi.unregister _

  private lazy val oneSignalPush = new OneSignalPush(
    deviceApi.findLastManyByUserId("onesignal", 3) _,
    url = OneSignalUrl,
    appId = OneSignalAppId,
    key = OneSignalKey
  )

  val googleCredentials: Option[GoogleCredentials] = try {
    config.getString("firebase.json").some.filter(_.nonEmpty).map { json =>
      ServiceAccountCredentials
        .fromStream(new java.io.ByteArrayInputStream(json.getBytes()))
        .createScoped(Set("https://www.googleapis.com/auth/firebase.messaging").asJava)
    }
  } catch {
    case e: Exception =>
      logger.warn("Failed to create google credentials", e)
      none
  }
  if (googleCredentials.isDefined) logger.info("Firebase push notifications are enabled.")

  private lazy val firebasePush = new FirebasePush(
    googleCredentials,
    deviceApi,
    url = FirebaseUrl
  )(system)

  private lazy val webPush = new WebPush(
    webSubscriptionApi.getSubscriptions(5) _,
    url = WebUrl,
    vapidPublicKey = WebVapidPublicKey
  )

  private lazy val pushApi = new PushApi(
    firebasePush,
    oneSignalPush,
    webPush,
    getLightUser,
    gameProxy,
    urgentGames,
    bus = system.lidraughtsBus,
    scheduler = scheduler
  )

  system.lidraughtsBus.subscribeFun('finishGame, 'moveEventCorres, 'newMessage, 'challenge, 'corresAlarm, 'offerEventCorres) {
    case lidraughts.game.actorApi.FinishGame(game, _, _) => pushApi finish game logFailure logger
    case lidraughts.hub.actorApi.round.CorresMoveEvent(move, _, pushable, _, _) if pushable => pushApi move move logFailure logger
    case lidraughts.hub.actorApi.round.CorresTakebackOfferEvent(gameId) => pushApi takebackOffer gameId logFailure logger
    case lidraughts.hub.actorApi.round.CorresDrawOfferEvent(gameId) => pushApi drawOffer gameId logFailure logger
    case lidraughts.message.Event.NewMessage(t, p) => pushApi newMessage (t, p) logFailure logger
    case lidraughts.challenge.Event.Create(c) => pushApi challengeCreate c logFailure logger
    case lidraughts.challenge.Event.Accept(c, joinerId) => pushApi.challengeAccept(c, joinerId) logFailure logger
    case lidraughts.game.actorApi.CorresAlarmEvent(pov) => pushApi corresAlarm pov logFailure logger
  }
}

object Env {

  lazy val current: Env = "push" boot new Env(
    db = lidraughts.db.Env.current,
    system = lidraughts.common.PlayApp.system,
    getLightUser = lidraughts.user.Env.current.lightUserSync,
    gameProxy = lidraughts.round.Env.current.proxy.game _,
    urgentGames = lidraughts.round.Env.current.proxy.urgentGames _,
    scheduler = lidraughts.common.PlayApp.scheduler,
    config = lidraughts.common.PlayApp loadConfig "push"
  )
}
