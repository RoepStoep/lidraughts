package lidraughts

import lidraughts.socket.WithSocket

package object externalTournament extends PackageObject with WithSocket {

  private[externalTournament] type SocketMap = lidraughts.hub.TrouperMap[externalTournament.ExternalTournamentSocket]

  private[externalTournament] val logger = lidraughts.log("externalTournament")
}
