package lidraughts.externalTournament

import akka.actor.ActorSystem
import ornicar.scalalib.Zero
import scala.concurrent.duration._

import actorApi._
import ExternalPlayer.Status
import lidraughts.challenge.Challenge
import lidraughts.db.dsl._
import lidraughts.game.Game
import lidraughts.user.{ User, UserRepo }

final class ExternalTournamentApi(
    coll: Coll,
    socketMap: SocketMap,
    cached: Cached,
    challengeApi: lidraughts.challenge.ChallengeApi,
    gameMetaApi: GameMetaApi
)(implicit system: ActorSystem) {

  private val sequencer =
    new lidraughts.hub.DuctSequencers(
      maxSize = 1024, // queue many game finished events
      expiration = 20 minutes,
      timeout = 10 seconds,
      name = "externalTournament.api"
    )

  import BsonHandlers._

  def byId(id: ExternalTournament.ID) = coll.byId[ExternalTournament](id)

  def create(
    data: DataForm.TournamentData,
    me: User
  ): Fu[ExternalTournament] = {
    val tour = data make me.id
    coll.insert(tour) inject tour
  }

  def update(
    tourId: ExternalTournament.ID,
    data: DataForm.TournamentData
  ): Fu[Option[ExternalTournament]] =
    Sequencing(tourId)(byId) { old =>
      val newTour = data make old.createdBy
      val updated = old.copy(
        name = newTour.name,
        variant = newTour.variant,
        clock = newTour.clock,
        days = newTour.days,
        rated = newTour.rated,
        settings = newTour.settings
      )
      coll.update($id(tourId), updated) >>- {
        socketReload(tourId)
      } inject updated.some
    }

  def addPlayer(
    tourId: ExternalTournament.ID,
    data: DataForm.PlayerData,
    user: User
  ): Fu[Option[ExternalPlayer]] =
    Sequencing(tourId)(byId) { tour =>
      ExternalPlayerRepo.exists(tour.id, user.id) flatMap { exists =>
        if (exists) fuccess(none)
        else {
          val player = ExternalPlayer.make(tour, user, data.fmjdId)
          ExternalPlayerRepo.insert(player) >>- {
            socketReload(tour.id)
          } inject player.some
        }
      }
    }

  def deletePlayer(
    tourId: ExternalTournament.ID,
    player: ExternalPlayer
  ): Fu[Boolean] =
    Sequencing(tourId)(byId) { tour =>
      val userHasGamesFu = for {
        finished <- cached.getFinishedGames(tour.id).map(_.games.filter(_.game.userIds.contains(player.userId)))
        ongoing <- finished.isEmpty ?? cached.getOngoingGames(tour.id).map(_.filter(_.game.userIds.contains(player.userId)))
        upcoming <- ongoing.isEmpty ?? challengeApi.allForExternalTournament(tour.id).map(_.filter(_.userIds.contains(player.userId)))
      } yield finished.nonEmpty || ongoing.nonEmpty || upcoming.nonEmpty
      userHasGamesFu flatMap { hasGames =>
        if (hasGames) fuFalse
        else ExternalPlayerRepo.remove(player) >>
          updateRanking(tour) >>
          cached.invalidateStandings(tourId) >>- {
            socketReload(tourId)
          } inject true
      }
    }

  def answer(
    tourId: ExternalTournament.ID,
    me: User,
    accept: Boolean
  ): Fu[Boolean] =
    Sequencing(tourId)(byId) { tour =>
      ExternalPlayerRepo.find(tourId, me.id) flatMap {
        case Some(player) if !player.accepted && accept =>
          ExternalPlayerRepo.setStatus(player.id, Status.Accepted) >>
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
    ExternalPlayerRepo.acceptedByTour(tour.id) flatMap { currentPlayers =>
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
        for {
          finished <- cached.getFinishedGames(tour.id)
          ongoing <- cached.getOngoingGames(tour.id)
        } yield PlayerInfo.make(player, finished.games, ongoing).some
      }
    }

  def pageOf(tour: ExternalTournament, userId: User.ID): Fu[Option[Int]] =
    ExternalPlayerRepo.find(tour.id, userId) map {
      _ ?? { p => p.page.some }
    }

  def finishGame(game: Game): Funit =
    game.externalTournamentId.?? { tourId =>
      Sequencing(tourId)(byId) { tour =>
        if (game.aborted) {
          cached.ongoingGameIdsCache.invalidate(tourId)
          socketReload(tourId)
          funit
        } else gameMetaApi.withMeta(game).flatMap { gameMeta =>
          game.userIds.map(updatePlayer(tour, game)).sequenceFu.void >>
            updateRanking(tour) >> {
              cached.ongoingGameIdsCache.invalidate(tourId)
              cached.addFinishedGame(tourId, gameMeta)
              cached.invalidateStandings(tourId)
            } >>- {
              socketReload(tourId)
            }
        }
      }
    }

  private def updatePlayer(
    tour: ExternalTournament,
    game: Game
  )(userId: User.ID): Funit =
    ExternalPlayerRepo.update(tour.id, userId) { player =>
      UserRepo.perfOf(userId, tour.perfType) map { perf =>
        player.copy(
          rating = perf.fold(player.rating)(_.intRating),
          provisional = perf.fold(player.provisional)(_.provisional),
          points = player.points + game.winnerUserId.fold(1)(id => if (id == userId) 2 else 0)
        )
      }
    }

  def startGame(game: Game): Funit =
    game.externalTournamentId ?? { tourId =>
      Sequencing(tourId)(byId) { tour =>
        cached.ongoingGameIdsCache.invalidate(tour.id)
        cached.invalidateStandings(tour.id) >>-
          socketReload(tour.id)
      }
    }

  def addChallenge(c: Challenge): Funit =
    c.externalTournamentId ?? { tourId =>
      gameMetaApi.insert(GameMeta(c.id, c.round)) >>-
        socketReload(tourId)
    }

  def forbiddenRounds(tour: ExternalTournament, userIds: (User.ID, User.ID)) = {
    def challenge(c: Challenge) = c.round.isDefined && c.userIds.exists(id => userIds._1 == id || userIds._2 == id)
    def game(g: GameWithMeta) = g.round.isDefined && g.game.userIds.exists(id => userIds._1 == id || userIds._2 == id)
    for {
      upcoming <- challengeApi.allForExternalTournament(tour.id).map(_.filter(challenge))
      ongoing <- cached.getOngoingGames(tour.id).map(_.filter(game))
      finished <- cached.getFinishedGames(tour.id).map(_.games.filter(game))
    } yield {
      def toSet(id: User.ID) = {
        val upcomingRounds = upcoming.foldLeft(Set.empty[Int]) { (set, c) => if (c.userIds.contains(id)) set + ~c.round else set }
        (ongoing ::: finished).foldLeft(upcomingRounds) { (set, g) => if (g.game.userIds.contains(id)) set + ~g.round else set }
      }
      toSet(userIds._1) -> toSet(userIds._2)
    }
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
