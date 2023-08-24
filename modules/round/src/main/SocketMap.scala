package lidraughts.round

import scala.concurrent.duration._

import lidraughts.game.Game
import lidraughts.hub.actorApi.Deploy
import lidraughts.hub.actorApi.map.{ Tell, TellIfExists, Exists }
import lidraughts.user.User

private object SocketMap {

  def make(
    makeHistory: Game.ID => History,
    dependencies: RoundSocket.Dependencies,
    socketTimeout: FiniteDuration,
    playban: lidraughts.playban.PlaybanApi,
    toWfdName: String => Option[String]
  ): SocketMap = {

    import dependencies._

    val defaultGoneWeight = fuccess(1f)
    def goneWeight(userId: User.ID): Fu[Float] = playban.getRageSit(userId).dmap(_.goneWeight)
    def goneWeights(game: Game): Fu[(Float, Float)] =
      game.whitePlayer.userId.fold(defaultGoneWeight)(goneWeight) zip
        game.blackPlayer.userId.fold(defaultGoneWeight)(goneWeight)

    lazy val socketMap: SocketMap = lidraughts.socket.SocketMap[RoundSocket](
      system = system,
      mkTrouper = (id: Game.ID) => new RoundSocket(
        dependencies = dependencies,
        gameId = id,
        history = makeHistory(id),
        keepMeAlive = () => socketMap touch id,
        getGoneWeights = goneWeights,
        toWfdName = toWfdName
      ),
      accessTimeout = socketTimeout,
      monitoringName = "round.socketMap",
      broomFrequency = 4001 millis
    )
    system.lidraughtsBus.subscribeFuns(
      'startGame -> {
        case msg: lidraughts.game.actorApi.StartGame => socketMap.tellIfPresent(msg.game.id, msg)
      },
      'roundSocket -> {
        case TellIfExists(gameId, msg) => socketMap.tellIfPresent(gameId, msg)
        case Tell(gameId, msg) => socketMap.tell(gameId, msg)
        case Exists(gameId, promise) => promise success socketMap.exists(gameId)
      },
      'deploy -> {
        case m: Deploy => socketMap tellAll m
      }
    )
    socketMap
  }
}
