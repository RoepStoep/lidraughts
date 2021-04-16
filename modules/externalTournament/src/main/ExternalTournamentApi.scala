package lidraughts.externalTournament

import akka.actor.ActorSystem
import ornicar.scalalib.Zero
import scala.concurrent.duration._

import actorApi._
import ExternalPlayer.Status
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.{ User, UserRepo }

final class ExternalTournamentApi(
    coll: Coll,
    socketMap: SocketMap,
    cached: Cached
)(implicit system: ActorSystem) {

  private val sequencer =
    new lidraughts.hub.DuctSequencers(
      maxSize = 1024, // queue many game finished events
      expiration = 20 minutes,
      timeout = 10 seconds,
      name = "externalTournament.api"
    )

  import BsonHandlers._

  def tournamentForm = DataForm.tournament
  def playerForm = DataForm.player

  def byId(id: ExternalTournament.ID) = coll.byId[ExternalTournament](id)

  def create(
    data: DataForm.TournamentData,
    me: User
  ): Fu[ExternalTournament] = {
    val tour = data make me.id
    coll.insert(tour) inject tour
  }

  def addPlayer(
    tourId: ExternalTournament.ID,
    data: DataForm.PlayerData
  ): Fu[Option[ExternalPlayer]] =
    Sequencing(tourId)(byId) { tour =>
      UserRepo.named(data.userId) flatMap {
        _ ?? { user =>
          ExternalPlayerRepo.exists(tour.id, user.id) flatMap { exists =>
            if (exists) fuccess(none)
            else {
              val player = ExternalPlayer.make(tour, user)
              ExternalPlayerRepo.insert(player) >>- {
                socketReload(tour.id)
              } inject player.some
            }
          }
        }
      }
    }

  def isAutoStartAllowed(
    tourId: ExternalTournament.ID,
    userId1: User.ID,
    userId2: User.ID
  ): Fu[(Boolean, Boolean)] =
    ExternalPlayerRepo.filterJoined(tourId, List(userId1, userId2)) map { joinedUsers =>
      (joinedUsers.contains(userId1), joinedUsers.contains(userId2))
    }

  def answer(
    tourId: ExternalTournament.ID,
    me: User,
    accept: Boolean
  ): Fu[Boolean] =
    Sequencing(tourId)(byId) { tour =>
      ExternalPlayerRepo.find(tourId, me.id) flatMap {
        case Some(player) if !player.joined && accept =>
          ExternalPlayerRepo.setStatus(player.id, Status.Joined) >>
            updateRanking(tour) >>
            cached.invalidateStandings(tourId) >>- {
              socketReload(tourId)
            } inject true
        case Some(player) if player.invited && !accept =>
          ExternalPlayerRepo.setStatus(player.id, Status.Rejected) >>- {
            socketReload(tourId)
          } inject true
        case _ => fuFalse
      }
    }

  private def updateRanking(tour: ExternalTournament) =
    ExternalPlayerRepo.joinedByTour(tour.id) flatMap { currentPlayers =>
      val updatedPlayers = currentPlayers.sortWith {
        (p1, p2) =>
          if (p1.points == p2.points) p1.rating > p2.rating
          else p1.points > p2.points
      }.zipWithIndex.flatMap {
        case (p, r) => if (p.rank.contains(r + 1)) None else p.withRank(r + 1).some
      }
      lidraughts.common.Future.applySequentially(updatedPlayers)(updateRank).void
    }

  private def updateRank(p: ExternalPlayer) =
    p.rank.fold(funit)(ExternalPlayerRepo.setRank(p.id, _))

  def playerInfo(
    tour: ExternalTournament,
    userId: User.ID
  ): Fu[Option[PlayerInfo]] =
    ExternalPlayerRepo.find(tour.id, userId) flatMap {
      _ ?? { player =>
        cached.getFinishedGames(tour.id).map { games =>
          PlayerInfo.make(player, games).some
        }
      }
    }

  def pageOf(tour: ExternalTournament, userId: User.ID): Fu[Option[Int]] =
    ExternalPlayerRepo.find(tour.id, userId) map {
      _ ?? { p => p.page.some }
    }

  def finishGame(game: Game): Funit =
    game.externalTournamentId.fold(funit) { tourId =>
      Sequencing(tourId)(byId) { tour =>
        game.winnerUserId.fold(game.userIds.map(ExternalPlayerRepo.incPoints(tourId, _, 1)).sequenceFu.void) {
          ExternalPlayerRepo.incPoints(tourId, _, 2).void
        } >>
          updateRanking(tour) >>
          cached.invalidateStandings(tourId) >>- {
            cached.finishedGamesCache.invalidate(tourId)
            cached.ongoingGameIdsCache.invalidate(tourId)
            socketReload(tourId)
          }
      }
    } void

  def startGame(game: Game): Unit =
    game.externalTournamentId.foreach { tourId =>
      cached.ongoingGameIdsCache.invalidate(tourId)
      socketReload(tourId)
    }

  def addChallenge(c: lidraughts.challenge.Challenge): Unit =
    c.externalTournamentId.foreach { tourId =>
      socketReload(tourId)
    }

  private def Sequencing[A: Zero](
    id: ExternalTournament.ID
  )(fetch: ExternalTournament.ID => Fu[Option[ExternalTournament]])(run: ExternalTournament => Fu[A]): Fu[A] =
    sequencer(id) {
      fetch(id) flatMap {
        _ ?? run
      }
    }

  private def socketReload(tourId: ExternalTournament.ID): Unit =
    socketMap.tell(tourId, Reload)
}
