package lidraughts

import lidraughts.common.{ LightUser, LightWfdUser }
import lidraughts.socket.WithSocket
import lidraughts.user.User

package object swiss extends PackageObject with WithSocket {

  private[swiss] type SocketMap = lidraughts.hub.TrouperMap[swiss.SwissSocket]

  private[swiss] val logger = lidraughts.log("swiss")

  private[swiss] type Ranking = Map[lidraughts.user.User.ID, Int]

  private[swiss] type LightUsersGetter = (List[String], Boolean) => Fu[List[Either[Option[LightUser], Option[LightWfdUser]]]]

  // FIDE TRF player IDs
  private[swiss] type PlayerIds = Map[User.ID, Int]
  private[swiss] type IdPlayers = Map[Int, User.ID]
}
