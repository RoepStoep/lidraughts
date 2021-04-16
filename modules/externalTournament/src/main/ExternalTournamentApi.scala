package lidraughts.externalTournament

import actorApi._
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.{ User, UserRepo }
import ExternalPlayer.Status

final class ExternalTournamentApi(
    coll: Coll,
    socketMap: SocketMap,
    cached: Cached
) {

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
    tour: ExternalTournament,
    data: DataForm.PlayerData
  ): Fu[Option[ExternalPlayer]] =
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

  def isAutoStartAllowed(
    tourId: ExternalTournament.ID,
    userId1: User.ID,
    userId2: User.ID
  ): Fu[(Boolean, Boolean)] =
    ExternalPlayerRepo.filterJoined(tourId, List(userId1, userId2)) map { joinedUsers =>
      (joinedUsers.contains(userId1), joinedUsers.contains(userId2))
    }

  def answer(
    tour: ExternalTournament,
    me: User,
    accept: Boolean
  ): Fu[Boolean] =
    ExternalPlayerRepo.find(tour.id, me.id) flatMap {
      case Some(player) if !player.joined && accept =>
        ExternalPlayerRepo.setStatus(player.id, Status.Joined) >>
          updateRanking(tour) >>
          cached.invalidateStandings(tour.id) >>- {
            socketReload(tour.id)
          } inject true
      case Some(player) if player.invited && !accept =>
        ExternalPlayerRepo.setStatus(player.id, Status.Rejected) >>- {
          socketReload(tour.id)
        } inject true
      case _ => fuFalse
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
    game.externalTournamentId.map(byId).fold(funit) {
      _ flatMap {
        _ ?? { tour =>
          game.winnerUserId.fold(game.userIds.map(ExternalPlayerRepo.incPoints(tour.id, _, 1)).sequenceFu.void) {
            ExternalPlayerRepo.incPoints(tour.id, _, 2).void
          } >>
            updateRanking(tour) >>
            cached.invalidateStandings(tour.id) >>- {
              cached.finishedGamesCache.invalidate(tour.id)
              cached.ongoingGameIdsCache.invalidate(tour.id)
              socketReload(tour.id)
            }
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

  def socketReload(tourId: ExternalTournament.ID): Unit = socketMap.tell(tourId, Reload)
}
