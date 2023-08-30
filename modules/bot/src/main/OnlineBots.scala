package lidraughts.bot

import scala.concurrent.duration._

import lidraughts.common.{ Every, AtMost }
import lidraughts.memo.{ ExpireSetMemo, PeriodicRefreshCache }
import lidraughts.user.{ User, UserRepo }

final class OnlineBots(
    onlineUserIdMemo: ExpireSetMemo
)(implicit system: akka.actor.ActorSystem) {

  private val onlineBotsCache = new PeriodicRefreshCache[Set[User.ID]](
    every = Every(30 seconds),
    atMost = AtMost(30 seconds),
    f = () => UserRepo.filterBot(onlineUserIdMemo.keys),
    default = Set.empty,
    logger = logger branch "top50online",
    initialDelay = 15 seconds
  )

  def get: Set[User.ID] = onlineBotsCache.get
}
