package lidraughts.externalTournament

import akka.actor.ActorSystem
import ornicar.scalalib.Zero
import scala.concurrent.duration._

import actorApi._
import draughts.format.{ Forsyth, FEN }
import draughts.variant.FromPosition
import ExternalPlayer.Status
import lidraughts.challenge.{ Challenge, ChallengeApi }
import lidraughts.challenge.Challenge.TimeControl
import lidraughts.common.Bus
import lidraughts.db.dsl._
import lidraughts.game.{ Game, GameRepo }
import lidraughts.round.actorApi.round.QuietFlag
import lidraughts.user.{ User, UserRepo }

final class ExternalTournamentApi(
    coll: Coll,
    socketMap: SocketMap,
    cached: Cached,
    challengeApi: ChallengeApi,
    gameMetaApi: GameMetaApi,
    updateIfPresent: Game => Fu[Game],
    bus: Bus
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
      validateUpdate(tourId) flatMap {
        case (hasGames, nbAccepted) =>
          if (hasGames && data.changedGameSettings(old))
            fufail("Cannot change game settings once games have been added")
          else if (hasGames && data.rounds.isDefined != old.hasRounds)
            fufail(s"Cannot ${if (old.hasRounds) "unset" else "set"} rounds once games have been added")
          else if (nbAccepted > 0 && data.autoStart != old.settings.autoStart)
            fufail("Cannot change autoStart once players have joined")
          else {
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
      }
    }

  private def validateUpdate(tourId: ExternalTournament.ID) =
    for {
      nbAccepted <- ExternalPlayerRepo.countAccepted(tourId)
      finished <- cached.getFinishedGames(tourId).dmap(_.games)
      ongoing <- finished.isEmpty ?? cached.getOngoingGames(tourId)
      upcoming <- ongoing.isEmpty ?? cached.getUpcomingGames(tourId)
    } yield (finished.nonEmpty || ongoing.nonEmpty || upcoming.nonEmpty, nbAccepted)

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

  def invalidateAll(tourId: ExternalTournament.ID) =
    Sequencing(tourId)(byId) { tour =>
      cached.upcomingGamesCache.invalidate(tour.id)
      cached.ongoingGameIdsCache.invalidate(tour.id)
      cached.finishedGamesCache.invalidate(tour.id)
      updateRanking(tour) >>
        cached.invalidateStandings(tour.id) >>-
        socketReload(tour.id)
    }

  def deletePlayer(
    tourId: ExternalTournament.ID,
    player: ExternalPlayer
  ): Fu[Boolean] =
    Sequencing(tourId)(byId) { tour =>
      val userHasGamesFu = for {
        finished <- cached.getFinishedGames(tour.id).map(_.games.filter(_.game.userIds.contains(player.userId)))
        ongoing <- finished.isEmpty ?? cached.getOngoingGames(tour.id).map(_.filter(_.game.userIds.contains(player.userId)))
        upcoming <- ongoing.isEmpty ?? cached.getUpcomingGames(tour.id).map(_.filter(_.userIds.contains(player.userId)))
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
        } yield PlayerInfo.make(tour, player, finished, ongoing).some
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
          cached.invalidateStandings(tourId)
          socketReload(tourId)
          funit
        } else gameMetaApi.withMeta(game).flatMap { gameMeta =>
          val updateFu: Fu[User.ID => Funit] = game.metadata.microMatchGameId match {
            case Some(id) if game.metadata.microMatchGameNr.contains(2) =>
              GameRepo.game(id).map { game2 =>
                updatePlayerMicroMatch(tour, List(game2, game.some).flatten)
              }
            case _ if game.metadata.microMatchGameNr.contains(1) =>
              fuccess(updatePlayer(tour, none)) // micromatch points are awarded after second game
            case _ => fuccess(updatePlayer(tour, game.some))
          }
          updateFu flatMap { update =>
            game.userIds.map(update).sequenceFu.void >>
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
    }

  def startGame(game: Game): Funit =
    game.externalTournamentId ?? { tourId =>
      Sequencing(tourId)(byId) { tour =>
        (tour.hasRounds && game.metadata.isMicroRematch) ?? {
          game.metadata.microMatchGameId.fold(fuccess(none[GameMeta]))(gameMetaApi.find) flatMap {
            case Some(previousGame) => gameMetaApi.insert(GameMeta(game.id, previousGame.round))
            case _ => funit
          }
        } >> {
          cached.upcomingGamesCache.invalidate(tour.id)
          cached.ongoingGameIdsCache.invalidate(tour.id)
          cached.invalidateStandings(tour.id)
        } >>- {
          socketReload(tour.id)
        }
      }
    }

  private def updatePlayer(
    tour: ExternalTournament,
    game: Option[Game] // update only ratings if none
  )(userId: User.ID): Funit =
    ExternalPlayerRepo.update(tour.id, userId) { player =>
      UserRepo.perfOf(userId, tour.perfType) map { perf =>
        def updatedPoints(g: Game) = player.points + g.winnerUserId.fold(1)(id => if (id == userId) 2 else 0)
        player.copy(
          rating = perf.fold(player.rating)(_.intRating),
          provisional = perf.fold(player.provisional)(_.provisional),
          points = game.fold(player.points)(updatedPoints)
        )
      }
    }

  private def updatePlayerMicroMatch(
    tour: ExternalTournament,
    games: List[Game]
  )(userId: User.ID): Funit =
    ExternalPlayerRepo.update(tour.id, userId) { player =>
      UserRepo.perfOf(userId, tour.perfType) map { perf =>
        val totalPoints = games.foldLeft(0) { (t, g) => t + g.winnerUserId.fold(1)(id => if (id == userId) 2 else 0) }
        player.copy(
          rating = perf.fold(player.rating)(_.intRating),
          provisional = perf.fold(player.provisional)(_.provisional),
          points = player.points + (if (totalPoints > 2) 2 else if (totalPoints == 2) 1 else 0)
        )
      }
    }

  def addChallenge(tourId: ExternalTournament.ID, data: DataForm.GameData): Fu[Option[Challenge]] =
    Sequencing(tourId)(byId) { tour =>
      if (tour.hasRounds && data.round.isEmpty) fufail("Round must be specified")
      else if (!tour.hasRounds && data.round.isDefined) fufail("No rounds configured for this tournament")
      else validateChallenge(tour, data) flatMap { challenge =>
        challengeApi.create(challenge) flatMap {
          case true =>
            gameMetaApi.insert(GameMeta(challenge.id, challenge.round)) >>- {
              logger.info(s"Challenge ${challenge.id} created in tournament $tourId")
              cached.upcomingGamesCache.invalidate(tourId)
              socketReload(tourId)
            } inject challenge.some
          case false =>
            fufail("Could not create game")
        }
      }
    }

  private def validateChallenge(tour: ExternalTournament, data: DataForm.GameData) =
    for {
      whiteUser <- UserRepo enabledByName data.whiteUserId flatten s"Invalid whiteUserId"
      blackUser <- UserRepo enabledByName data.blackUserId flatten s"Invalid blackUserId"
      _ <- ExternalPlayerRepo.findAccepted(tour.id, whiteUser.id) flatMap {
        case None => fufail(s"${data.whiteUserId} has not joined the tournament")
        case Some(p) if data.round.??(p.hasBye) => fufail(s"Round ${~data.round} bye already exists for ${whiteUser.id}")
        case _ => funit
      }
      _ <- ExternalPlayerRepo.findAccepted(tour.id, blackUser.id) flatMap {
        case None => fufail(s"${data.blackUserId} has not joined the tournament")
        case Some(p) if data.round.??(p.hasBye) => fufail(s"Round ${~data.round} bye already exists for ${blackUser.id}")
        case _ => funit
      }
      _ <- data.round ?? { round =>
        forbiddenRounds(tour, List(whiteUser.id, blackUser.id)) flatMap { forbidden =>
          forbidden.map(_.contains(round)) match {
            case List(true, _) => fufail(s"Round ${~data.round} game already exists for ${whiteUser.id}")
            case List(_, true) => fufail(s"Round ${~data.round} game already exists for ${blackUser.id}")
            case _ => funit
          }
        }
      }
      validFen = data.fen.isEmpty || data.fen.contains("random") || {
        data.fen ?? { f => ~Forsyth.<<<@(tour.variant, f).map(_.situation playable false) }
      }
      fenVariant <- validFen match {
        case false =>
          fufail("Invalid FEN")
        case true if data.fen.contains("random") && !(tour.variant.russian || tour.variant.brazilian) =>
          fufail("Random openings are only available for variants russian and brazilian")
        case _ => fuccess(data.fen.nonEmpty option tour.variant)
      }
      initialFen = data.fen match {
        case Some("random") => fenVariant.flatMap(_.openingTables.headOption).map(_.randomOpening._2.fen).map(FEN)
        case f @ _ => f.map(FEN)
      }
    } yield Challenge.make(
      variant = if (fenVariant.nonEmpty && tour.variant.standard) FromPosition else tour.variant,
      fenVariant = fenVariant,
      initialFen = initialFen,
      timeControl = tour.clock map { c =>
        TimeControl.Clock(c)
      } orElse tour.days.map {
        TimeControl.Correspondence.apply
      } getOrElse TimeControl.Unlimited,
      mode = draughts.Mode(tour.rated),
      color = draughts.White.name,
      challenger = Right(whiteUser),
      destUser = blackUser.some,
      rematchOf = none,
      external = true,
      startsAt = data.startsAt.some,
      autoStart = tour.settings.autoStart,
      microMatch = tour.settings.microMatches,
      externalTournamentId = tour.id.some,
      externalTournamentRound = data.round
    )

  def processBye(tourId: ExternalTournament.ID, data: DataForm.ByeData): Funit =
    Sequencing(tourId)(byId) { tour =>
      if (!tour.hasRounds) fufail("Cannot add byes in a tournament without rounds")
      else UserRepo enabledByName data.userId flatten s"Invalid userId" flatMap { user =>
        validateBye(tour, user, data) flatMap { bye =>
          ExternalPlayerRepo.update(tour.id, user.id) { player =>
            fuccess(player.copy(
              points = player.points + (if (bye.f) 2 else 1),
              byes = (bye :: ~player.byes).some
            ))
          } >>
            updateRanking(tour) >>
            cached.invalidateStandings(tourId) >>-
            socketReload(tourId)
        }
      }
    }

  private def validateBye(tour: ExternalTournament, user: User, data: DataForm.ByeData) =
    for {
      _ <- ExternalPlayerRepo.findAccepted(tour.id, user.id) flatMap {
        case None => fufail(s"${user.id} has not joined the tournament")
        case Some(p) if p.hasBye(data.round) => fufail(s"Round ${data.round} bye already exists for ${user.id}")
        case _ => fuTrue
      }
      _ <- forbiddenRounds(tour, List(user.id)).flatMap { forbidden =>
        if (forbidden.exists(_.contains(data.round))) fufail(s"Round ${data.round} game already exists for ${user.id}")
        else funit
      }
    } yield PlayerInfo.Bye(data.round, data.full)

  private def forbiddenRounds(tour: ExternalTournament, userIds: List[User.ID]) = {
    def challenge(c: Challenge) = c.round.isDefined && c.userIds.exists(userIds.contains)
    def game(g: GameWithMeta) = g.round.isDefined && g.game.userIds.exists(userIds.contains)
    for {
      upcoming <- cached.getUpcomingGames(tour.id).map(_.filter(challenge))
      ongoing <- cached.getOngoingGames(tour.id).map(_.filter(game))
      finished <- cached.getFinishedGames(tour.id).map(_.games.filter(game))
    } yield userIds.map { id =>
      val upcomingRounds = upcoming.foldLeft(Set.empty[Int]) { (set, c) => if (c.userIds.contains(id)) set + ~c.round else set }
      (ongoing ::: finished).foldLeft(upcomingRounds) { (set, g) => if (g.game.userIds.contains(id)) set + ~g.round else set }
    }
  }

  private[externalTournament] def checkOngoingGames: Funit =
    GameRepo.ongoingExternalTournament(100).flatMap { games =>
      games.groupBy(_.externalTournamentId).map {
        case (Some(tourId), games) =>
          Sequencing(tourId)(byId) { _ =>
            games.map(updateIfPresent).sequenceFu map { proxyGames =>
              val flagged = proxyGames.filter(_ outoftime true)
              if (flagged.nonEmpty)
                bus.publish(lidraughts.hub.actorApi.map.TellMany(flagged.map(_.id), QuietFlag), 'roundMapTell)
            }
          }
        case _ => funit
      }.sequenceFu.void
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
