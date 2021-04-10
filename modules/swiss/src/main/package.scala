package lidraughts

import lidraughts.socket.WithSocket
import lidraughts.user.User

package object swiss extends PackageObject with WithSocket {

  private[swiss] type SocketMap = lidraughts.hub.TrouperMap[swiss.SwissSocket]

  private[swiss] val logger = lidraughts.log("swiss")

  private[swiss] type Ranking = Map[User.ID, Int]

  // FIDE TRF player IDs
  private[swiss] type PlayerIds = Map[User.ID, Int]
  private[swiss] type IdPlayers = Map[Int, User.ID]
}
