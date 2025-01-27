package lidraughts.tournament

import akka.actor.{ ActorRef, ActorSelection, ActorSystem, Props }
import akka.pattern.{ ask, pipe }
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.Promise
import actorApi._
import lidraughts.common.paginator.Paginator
import lidraughts.common.{ Debouncer, LightUser, MaxPerPage, MaxPerSecond }
import lidraughts.game.{ Game, GameRepo, LightGame, LightPov, Pov }
import lidraughts.hub.actorApi.lobby.ReloadTournaments
import lidraughts.hub.actorApi.map.Tell
import lidraughts.hub.actorApi.timeline.{ Propagate, TourJoin }
import lidraughts.hub.lightTeam._
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.round.actorApi.round.{ AbortForce, GoBerserk }
import lidraughts.socket.actorApi.SendToFlag
import lidraughts.user.{ User, UserRepo }
import makeTimeout.short

final class TournamentApi(
    cached: Cached,
    apiJsonView: ApiJsonView,
    system: ActorSystem,
    sequencers: DuctMap[_],
    autoPairing: AutoPairing,
    clearJsonViewCache: Tournament.ID => Unit,
    clearWinnersCache: Tournament => Unit,
    clearTrophyCache: Tournament => Unit,
    renderer: ActorSelection,
    timeline: ActorSelection,
    socketMap: SocketMap,
    roundMap: lidraughts.hub.DuctMap[_],
    trophyApi: lidraughts.user.TrophyApi,
    verify: Condition.Verify,
    indexLeaderboard: Tournament => Funit,
    duelStore: DuelStore,
    pause: Pause,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    lightUserApi: lidraughts.user.LightUserApi,
    proxyGame: Game.ID => Fu[Option[Game]]
) {

  private val bus = system.lidraughtsBus

  def get(id: Tournament.ID) = TournamentRepo byId id

  def createTournament(
    setup: TournamentSetup,
    me: User,
    myTeams: List[LightTeam],
    getUserTeamIds: User => Fu[List[TeamId]],
    andJoin: Boolean = true
  ): Fu[Tournament] = {
    val tour = Tournament.make(
      by = Right(me),
      name = DataForm.canPickName(me) ?? setup.name,
      clock = setup.clockConfig,
      minutes = setup.minutes,
      waitMinutes = setup.waitMinutes | DataForm.waitMinuteDefault,
      startDate = setup.startDate,
      mode = setup.realMode,
      password = setup.password,
      system = System.Arena,
      variant = setup.realVariant,
      position = setup.startingPosition,
      openingTable = setup.openingTable,
      berserkable = setup.berserkable | true,
      streakable = setup.streakable | true,
      teamBattle = setup.teamBattleByTeam map TeamBattle.init,
      description = setup.description,
      hasChat = setup.hasChat | true
    ) |> { tour =>
        tour.perfType.fold(tour) { perfType =>
          tour.copy(conditions = setup.conditions.convert(perfType, myTeams.map(_.pair)(collection.breakOut)))
        }
      } |> { tour =>
        tour.copy(isWfd = !tour.isTeamBattle && tour.conditions.teamMember.exists { team =>
          myTeams.exists(t => t.isWfd && t.id == team.teamId)
        })
      }
    sillyNameCheck(tour, me)
    logger.info(s"Create $tour")
    TournamentRepo.insert(tour) >>- {
      andJoin ?? join(tour.id, me, tour.password, setup.teamBattleByTeam, getUserTeamIds, none)
    } inject tour
  }

  def update(old: Tournament, data: TournamentSetup, me: User, myTeams: List[LightTeam], api: Boolean): Fu[Tournament] = {
    import data._
    val isAdmin = lidraughts.security.Granter(_.ManageTournament)(me)
    val newPromoted =
      if (old.userTournament && isAdmin) ~promoted
      else old.isPromoted
    val tour = {
      if (!api) {
        // update all fields and use default values for missing fields (HTML form updates)
        old.copy(
          name = (DataForm.canPickName(me) ?? name) | old.name,
          clock = clockConfig,
          minutes = minutes,
          mode = realMode,
          password = password,
          variant = realVariant,
          startsAt = startDate | old.startsAt,
          position = startingPosition,
          openingTable = openingTable,
          noBerserk = !(~berserkable),
          noStreak = !(~streakable),
          description = description,
          hasChat = hasChat | true,
          isPromoted = newPromoted
        )
      } else {
        // update only fields that are specified (API updates)
        val newVariant = if (variant.isDefined) realVariant else old.variant
        old.copy(
          name = (DataForm.canPickName(me) ?? name) | old.name,
          clock = clockConfig,
          minutes = minutes,
          mode = if (rated.isDefined) realMode else old.mode,
          password = password.fold(old.password)(_.some.filter(_.nonEmpty)),
          variant = newVariant,
          startsAt = startDate | old.startsAt,
          position = if (newVariant != old.variant || positionKey(newVariant).isDefined) startingPositionFor(newVariant) else old.position,
          openingTable = if (newVariant != old.variant || positionKey(newVariant).isDefined) openingTableFor(newVariant) else old.openingTable,
          noBerserk = berserkable.fold(old.noBerserk)(!_),
          noStreak = streakable.fold(old.noStreak)(!_),
          description = description.fold(old.description)(_.some.filter(_.nonEmpty)),
          hasChat = hasChat | old.hasChat,
          isPromoted = if (promoted.isDefined && isAdmin) newPromoted else old.isPromoted
        )
      }
    } |> { tour =>
      tour.perfType.fold(tour) { perfType =>
        val teams: Map[String, String] = myTeams.map(_.pair)(collection.breakOut)
        if (!api) tour.copy(conditions = conditions.convert(perfType, teams))
        else tour.copy(conditions = conditions.convertApi(perfType, teams, old.conditions))
      }
    } |> { tour =>
      tour.copy(isWfd = !tour.isTeamBattle && tour.conditions.teamMember.exists { team =>
        myTeams.exists(t => t.isWfd && t.id == team.teamId)
      })
    }
    if (old.isWfd != tour.isWfd)
      cached.wfdCache.invalidate(tour.id)
    sillyNameCheck(tour, me)
    TournamentRepo update tour inject tour
  }

  private def sillyNameCheck(tour: Tournament, me: User): Unit =
    if (tour.name != me.titleUsername && lidraughts.common.LameName.anyNameButLidraughtsIsOk(tour.name)) {
      val msg = s"""@${me.username} created tournament "${tour.name} Arena" :kappa: https://lidraughts.org/tournament/${tour.id}"""
      logger warn msg
      bus.publish(lidraughts.hub.actorApi.slack.Warning(msg), 'slack)
    }

  private[tournament] def createFromPlan(plan: Schedule.Plan): Funit = {
    val minutes = Schedule durationFor plan.schedule
    val tournament = plan.build.foldRight(Tournament.schedule(plan.schedule, minutes)) { _(_) }
    logger.info(s"Create $tournament")
    TournamentRepo.insert(tournament).void
  }

  def teamBattleUpdate(
    tour: Tournament,
    data: TeamBattle.DataForm.Setup,
    filterExistingTeamIds: Set[TeamId] => Fu[Set[TeamId]]
  ): Funit =
    filterExistingTeamIds(data.potentialTeamIds) flatMap { teamIds =>
      PlayerRepo.bestTeamIdsByTour(tour.id, TeamBattle(teamIds, data.nbLeaders)) flatMap { rankedTeams =>
        val allTeamIds = teamIds ++ rankedTeams.foldLeft(Set.empty[TeamId]) {
          case (missing, team) if !teamIds.contains(team.teamId) => missing + team.teamId
          case (acc, _) => acc
        }
        TournamentRepo.setTeamBattle(tour.id, TeamBattle(allTeamIds, data.nbLeaders))
      }
    }

  def teamBattleTeamInfo(tour: Tournament, teamId: TeamId): Fu[Option[TeamBattle.TeamInfo]] =
    tour.teamBattle.exists(_ teams teamId) ?? cached.teamInfo.get(tour.id -> teamId)

  private[tournament] def makePairings(oldTour: Tournament, users: WaitingUsers, startAt: Long): Unit = {
    Sequencing(oldTour.id)(TournamentRepo.startedById) { tour =>
      cached ranking tour flatMap { ranking =>
        tour.system.pairingSystem.createPairings(tour, users, ranking).flatMap {
          case Nil => funit
          case pairings if nowMillis - startAt > 1200 =>
            pairingLogger.warn(s"Give up making https://lidraughts.org/tournament/${tour.id} ${pairings.size} pairings in ${nowMillis - startAt}ms")
            lidraughts.mon.tournament.pairing.giveup()
            funit
          case pairings => UserRepo.idsMap(pairings.flatMap(_.users)) flatMap { users =>
            pairings.map { pairing =>
              PairingRepo.insert(pairing) >>
                autoPairing(tour, pairing, users, ranking) addEffect { game =>
                  socketMap.tell(tour.id, StartGame(game))
                }
            }.sequenceFu >> featureOneOf(tour, pairings, ranking) >>- {
              lidraughts.mon.tournament.pairing.create(pairings.size)
            }
          }
        } >>- {
          val time = nowMillis - startAt
          lidraughts.mon.tournament.pairing.createTime(time.toInt)
          if (time > 100)
            pairingLogger.debug(s"Done making https://lidraughts.org/tournament/${tour.id} in ${time}ms")
        }
      }
    }
  }

  private def featureOneOf(tour: Tournament, pairings: Pairings, ranking: Ranking): Funit =
    tour.featuredId.ifTrue(pairings.nonEmpty) ?? PairingRepo.byId map2
      RankedPairing(ranking) map (_.flatten) flatMap { curOption =>
        pairings.flatMap(RankedPairing(ranking)).sortBy(_.bestRank).headOption ?? { bestCandidate =>
          def switch = TournamentRepo.setFeaturedGameId(tour.id, bestCandidate.pairing.gameId)
          curOption.filter(_.pairing.playing) match {
            case Some(current) if bestCandidate.bestRank < current.bestRank => switch
            case Some(_) => funit
            case _ => switch
          }
        }
      }

  def start(oldTour: Tournament): Unit =
    Sequencing(oldTour.id)(TournamentRepo.createdById) { tour =>
      TournamentRepo.setStatus(tour.id, Status.Started) >>-
        socketReload(tour.id) >>-
        publish()
    }

  def wipe(tour: Tournament): Funit =
    TournamentRepo.remove(tour).void >>
      PairingRepo.removeByTour(tour.id) >>
      PlayerRepo.removeByTour(tour.id) >>- publish() >>- socketReload(tour.id)

  def finish(oldTour: Tournament): Unit = {
    Sequencing(oldTour.id)(TournamentRepo.startedById) { tour =>
      PairingRepo count tour.id flatMap {
        case 0 => wipe(tour)
        case _ => for {
          _ <- TournamentRepo.setStatus(tour.id, Status.Finished)
          _ <- PlayerRepo unWithdraw tour.id
          _ <- PairingRepo removePlaying tour.id
          winner <- PlayerRepo winner tour.id
          _ <- winner.??(p => TournamentRepo.setWinnerId(tour.id, p.userId))
        } yield {
          clearJsonViewCache(tour.id)
          socketReload(tour.id)
          publish()
          PlayerRepo withPoints tour.id foreach {
            _ foreach { p => UserRepo.incToints(p.userId, p.score) }
          }
          awardTrophies(tour).logFailure(logger, _ => s"${tour.id} awardTrophies")
          indexLeaderboard(tour).logFailure(logger, _ => s"${tour.id} indexLeaderboard")
          clearWinnersCache(tour)
          clearTrophyCache(tour)
          duelStore.remove(tour)
        }
      }
    }
  }

  def kill(tour: Tournament): Unit = {
    if (tour.isStarted) finish(tour)
    else if (tour.isCreated) wipe(tour)
  }

  private def awardTrophies(tour: Tournament): Funit = {
    import lidraughts.user.TrophyKind._
    tour.schedule.??(_.freq == Schedule.Freq.Marathon) ?? {
      PlayerRepo.bestByTourWithRank(tour.id, 100).flatMap {
        _.map {
          case rp if rp.rank == 1 => trophyApi.award(rp.player.userId, marathonWinner)
          case rp if rp.rank <= 10 => trophyApi.award(rp.player.userId, marathonTopTen)
          case rp if rp.rank <= 50 => trophyApi.award(rp.player.userId, marathonTopFifty)
          case rp => trophyApi.award(rp.player.userId, marathonTopHundred)
        }.sequenceFu.void
      }
    }
  }

  def getVerdicts(tour: Tournament, me: Option[User], getUserTeamIds: User => Fu[List[TeamId]]): Fu[Condition.All.WithVerdicts] = me match {
    case None => fuccess(tour.conditions.accepted)
    case Some(user) => {
      tour.isStarted ?? PlayerRepo.exists(tour.id, user.id)
    } flatMap {
      case true => fuccess(tour.conditions.accepted)
      case _ => verify(tour.conditions, user, getUserTeamIds)
    }
  }

  def join(
    tourId: Tournament.ID,
    me: User,
    password: Option[String],
    withTeamId: Option[String],
    getUserTeamIds: User => Fu[List[TeamId]],
    promise: Option[Promise[Tournament.JoinResult]]
  ): Unit = Sequencing(tourId)(TournamentRepo.enterableById) { tour =>
    PlayerRepo.exists(tour.id, me.id) flatMap { playerExists =>
      import Tournament.JoinResult
      val fuResult: Fu[JoinResult] =
        if (!playerExists && tour.password.exists(p => !password.has(p))) fuccess(JoinResult.WrongPassword)
        else
          getVerdicts(tour, me.some, getUserTeamIds) flatMap { verdicts =>
            if (!verdicts.accepted) fuccess(JoinResult.Verdicts)
            else if (!pause.canJoin(me.id, tour)) fuccess(JoinResult.Paused)
            else if (~tour.spotlight.map(_.excludeUser(me)))
              fuccess(JoinResult.Excluded(tour.spotlight.flatMap(_.excludeReason)))
            else {
              def proceedWithTeam(team: Option[String]): Fu[JoinResult] =
                PlayerRepo.join(tour.id, me, tour.perfLens, team) >>
                  updateNbPlayers(tour.id) >>- {
                    withdrawOtherTournaments(tour.id, me.id)
                    socketReload(tour.id)
                    publish()
                  } inject JoinResult.Ok
              withTeamId match {
                case None if tour.isTeamBattle && playerExists => proceedWithTeam(none)
                case None if tour.isTeamBattle => fuccess(JoinResult.MissingTeam)
                case None => proceedWithTeam(none)
                case Some(team) => tour.teamBattle match {
                  case Some(battle) if battle.teams contains team =>
                    getUserTeamIds(me) flatMap { myTeams =>
                      if (myTeams has team) proceedWithTeam(team.some)
                      else fuccess(JoinResult.MissingTeam)
                    }
                  case _ => fuccess(JoinResult.Nope)
                }
              }
            }
          }
      fuResult map { result =>
        if (!result.ok) socketReload(tour.id)
        promise.foreach(_ success result)
      }
    }
  }

  def joinWithResult(
    tourId: Tournament.ID,
    me: User,
    password: Option[String],
    teamId: Option[String],
    getUserTeamIds: User => Fu[List[TeamId]]
  ): Fu[Tournament.JoinResult] = {
    val promise = Promise[Tournament.JoinResult]
    join(tourId, me, password, teamId, getUserTeamIds, promise.some)
    promise.future.withTimeoutDefault(5.seconds, Tournament.JoinResult.Nope)(system)
  }

  def pageOf(tour: Tournament, userId: User.ID): Fu[Option[Int]] =
    cached ranking tour map {
      _ get userId map { rank =>
        (Math.floor(rank / 10) + 1).toInt
      }
    }

  private def updateNbPlayers(tourId: Tournament.ID) =
    PlayerRepo count tourId flatMap { TournamentRepo.setNbPlayers(tourId, _) }

  private def withdrawOtherTournaments(tourId: Tournament.ID, userId: User.ID): Unit =
    TournamentRepo tourIdsToWithdrawWhenEntering tourId foreach {
      PlayerRepo.filterExists(_, userId) foreach {
        _ foreach {
          withdraw(_, userId, isPause = false, isStalling = false)
        }
      }
    }

  def selfPause(tourId: Tournament.ID, userId: User.ID): Unit =
    withdraw(tourId, userId, isPause = true, isStalling = false)

  private def stallPause(tourId: Tournament.ID, userId: User.ID): Unit =
    withdraw(tourId, userId, isPause = false, isStalling = true)

  private def withdraw(tourId: Tournament.ID, userId: User.ID, isPause: Boolean, isStalling: Boolean): Unit = {
    Sequencing(tourId)(TournamentRepo.enterableById) {
      case tour if tour.isCreated =>
        PlayerRepo.remove(tour.id, userId) >> updateNbPlayers(tour.id) >>- socketReload(tour.id) >>- publish()
      case tour if tour.isStarted => for {
        _ <- PlayerRepo.withdraw(tour.id, userId)
        pausable <- if (isPause) cached.ranking(tour).map { _ get userId exists (7>) } else fuccess(isStalling)
      } yield {
        if (pausable) pause.add(userId, tour)
        socketReload(tour.id)
        publish()
      }
      case _ => funit
    }
  }

  def withdrawAll(user: User): Unit =
    TournamentRepo.nonEmptyEnterableIds() foreach {
      PlayerRepo.filterExists(_, user.id) foreach {
        _ foreach {
          withdraw(_, user.id, isPause = false, isStalling = false)
        }
      }
    }

  def berserk(gameId: Game.ID, userId: User.ID): Unit =
    proxyGame(gameId) foreach {
      _.filter(_.berserkable) foreach { game =>
        game.tournamentId foreach { tourId =>
          Sequencing(tourId)(TournamentRepo.startedById) { tour =>
            PairingRepo.findPlaying(tour.id, userId) flatMap {
              case Some(pairing) if !pairing.berserkOf(userId) =>
                (pairing colorOf userId) ?? { color =>
                  roundMap.ask(gameId) { p: Promise[Boolean] => GoBerserk(color, p) } flatMap {
                    _ ?? PairingRepo.setBerserk(pairing, userId)
                  }
                }
              case _ => funit
            }
          }
        }
      }
    }

  def finishGame(game: Game): Unit =
    game.tournamentId foreach { tourId =>
      Sequencing(tourId)(TournamentRepo.startedById) { tour =>
        PairingRepo.finish(game) >>
          game.userIds.map(updatePlayer(tour, game.some)).sequenceFu.void >>- {
            duelStore.remove(game)
            socketReload(tour.id)
            updateTournamentStanding(tour)
            withdrawNonMover(game)
          }
      }
    }

  def sittingDetected(game: Game, player: User.ID): Unit =
    game.tournamentId foreach { tourId =>
      stallPause(tourId, player)
    }

  private def updatePlayer(
    tour: Tournament,
    finishing: Option[Game] // if set, update the player performance. Leave to none to just recompute the sheet.
  )(userId: User.ID): Funit =
    (tour.perfType.ifTrue(tour.mode.rated) ?? { UserRepo.perfOf(userId, _) }) flatMap { perf =>
      PlayerRepo.update(tour.id, userId) { player =>
        cached.sheet.update(tour, userId) map { sheet =>
          player.copy(
            score = sheet.total,
            fire = tour.streakable && sheet.onFire,
            ratingDiff = finishing.fold(player.ratingDiff)(player.ratingDiff + _.playerByUserId(userId).fold(0)(_.ratingDiff.getOrElse(0))),
            rating = perf.fold(player.rating)(_.intRating),
            provisional = perf.fold(player.provisional)(_.provisional),
            performance = {
              for {
                g <- finishing
                performance <- performanceOf(g, userId).map(_.toDouble)
                nbGames = sheet.scores.size
                if nbGames > 0
              } yield Math.round {
                player.performance * (nbGames - 1) / nbGames + performance / nbGames
              } toInt
            } | player.performance
          )
        }
      }
    }

  private def performanceOf(g: Game, userId: String): Option[Int] = for {
    opponent <- g.opponentByUserId(userId)
    opponentRating <- opponent.rating
    multiplier = g.winnerUserId.??(winner => if (winner == userId) 1 else -1)
  } yield opponentRating + 500 * multiplier

  private def withdrawNonMover(game: Game): Unit = for {
    tourId <- game.tournamentId
    if game.status == draughts.Status.NoStart
    player <- game.playerWhoDidNotMove
    userId <- player.userId
  } withdraw(tourId, userId, isPause = false, isStalling = false)

  def pausePlaybanned(userId: User.ID) =
    TournamentRepo.startedIds flatMap {
      PlayerRepo.filterExists(_, userId) flatMap {
        _.map { tourId =>
          PlayerRepo.withdraw(tourId, userId) >>- socketReload(tourId) >>- publish()
        }.sequenceFu.void
      }
    }

  private[tournament] def kickFromTeam(teamId: TeamId, userId: User.ID): Unit =
    TournamentRepo.nonEmptyEnterableIds(teamId.some) foreach {
      PlayerRepo.filterExists(_, userId) foreach {
        _ foreach { tourId =>
          Sequencing(tourId)(TournamentRepo.byId) { tour =>
            val fu =
              if (tour.isCreated) PlayerRepo.remove(tour.id, userId)
              else PlayerRepo.withdraw(tour.id, userId)
            fu >> updateNbPlayers(tourId) >>- socketReload(tourId)
          }
        }
      }
    }

  def ejectLame(userId: User.ID, playedIds: List[Tournament.ID]): Unit =
    TournamentRepo.nonEmptyEnterableIds() foreach {
      PlayerRepo.filterExists(_, userId) foreach { enteredIds =>
        (enteredIds ++ playedIds).foreach { ejectLame(_, userId) }
      }
    }

  def ejectLame(tourId: Tournament.ID, userId: User.ID): Unit =
    Sequencing(tourId)(TournamentRepo.byId) { tour =>
      PlayerRepo.remove(tour.id, userId) >> {
        if (tour.isStarted)
          PairingRepo.findPlaying(tour.id, userId).map {
            _ foreach { currentPairing =>
              roundMap.tell(currentPairing.gameId, AbortForce)
            }
          } >> PairingRepo.opponentsOf(tour.id, userId).flatMap { uids =>
            PairingRepo.removeByTourAndUserId(tour.id, userId) >>
              lidraughts.common.Future.applySequentially(uids.toList)(updatePlayer(tour, none))
          }
        else if (tour.isFinished && tour.winnerId.contains(userId))
          PlayerRepo winner tour.id flatMap {
            _ ?? { p =>
              TournamentRepo.setWinnerId(tour.id, p.userId)
            }
          }
        else funit
      } >>
        updateNbPlayers(tour.id) >>-
        socketReload(tour.id) >>- publish()
    }

  private val tournamentTopNb = 20
  private val tournamentTopCache = asyncCache.multi[Tournament.ID, TournamentTop](
    name = "tournament.top",
    id => PlayerRepo.bestByTour(id, tournamentTopNb) map TournamentTop.apply,
    expireAfter = _.ExpireAfterWrite(3 second)
  )

  def tournamentTop(tourId: Tournament.ID): Fu[TournamentTop] =
    tournamentTopCache get tourId

  object gameView {

    def player(pov: Pov): Fu[Option[GameView]] =
      (pov.game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, pov.game) zip getGameRanks(tour, pov.game) flatMap {
            case (teamVs, ranks) =>
              teamVs.fold(tournamentTop(tour.id) dmap some) { vs =>
                cached.teamInfo.get(tour.id -> vs.teams(pov.color)) map2 { info: TeamBattle.TeamInfo =>
                  TournamentTop(info.topPlayers take tournamentTopNb)
                }
              } dmap {
                GameView(tour, teamVs, ranks, _).some
              }
          }
        }
      }

    def watcher(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) zip getGameRanks(tour, game) dmap {
            case (teamVs, ranks) => GameView(tour, teamVs, ranks, none).some
          }
        }
      }

    def mobile(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getGameRanks(tour, game) dmap { ranks =>
            GameView(tour, none, ranks, none).some
          }
        }
      }

    def analysis(game: Game): Fu[Option[GameView]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) dmap { GameView(tour, _, none, none).some }
        }
      }

    def withTeamVs(game: Game): Fu[Option[TourAndTeamVs]] =
      (game.tournamentId ?? get) flatMap {
        _ ?? { tour =>
          getTeamVs(tour, game) dmap { TourAndTeamVs(tour, _).some }
        }
      }

    private def getGameRanks(tour: Tournament, game: Game): Fu[Option[GameRanks]] = ~{
      for {
        whiteId <- game.whitePlayer.userId
        blackId <- game.blackPlayer.userId
        if tour.isStarted // don't fetch ranks of finished tournaments
      } yield cached ranking tour map { ranking =>
        ranking.get(whiteId) |@| ranking.get(blackId) apply {
          case (whiteR, blackR) => GameRanks(whiteR + 1, blackR + 1)
        }
      }
    }

    private def getTeamVs(tour: Tournament, game: Game): Fu[Option[TeamBattle.TeamVs]] =
      (tour.isTeamBattle ?? PlayerRepo.teamVs(tour.id, game))
  }

  def fetchVisibleTournaments: Fu[VisibleTournaments] =
    TournamentRepo.publicCreatedSorted(6 * 60) zip
      TournamentRepo.publicStarted zip
      TournamentRepo.finishedNotable(30) map {
        case ((created, started), finished) =>
          VisibleTournaments(created, started, finished)
      }

  def playerInfo(tour: Tournament, userId: User.ID): Fu[Option[PlayerInfoExt]] =
    UserRepo named userId flatMap {
      _ ?? { user =>
        PlayerRepo.find(tour.id, user.id) flatMap {
          _ ?? { player =>
            playerPovs(tour, user.id, 50) map { povs =>
              PlayerInfoExt(user, player, povs).some
            }
          }
        }
      }
    }

  def allCurrentLeadersInStandard: Fu[Map[Tournament, TournamentTop]] =
    TournamentRepo.standardPublicStartedFromSecondary.flatMap { tours =>
      tours.map { tour =>
        tournamentTop(tour.id) map (tour -> _)
      }.sequenceFu.map(_.toMap)
    }

  def resultStream(tour: Tournament, perSecond: MaxPerSecond, nb: Int): Enumerator[Player.Result] = {
    import reactivemongo.play.iteratees.cursorProducer
    import play.api.libs.iteratee._
    var rank = 0
    PlayerRepo.cursor(
      tournamentId = tour.id,
      batchSize = perSecond.value
    ).bulkEnumerator(nb) &>
      lidraughts.common.Iteratee.delay(1 second)(system) &>
      Enumeratee.mapConcat(_.toSeq) &>
      Enumeratee.mapM { player =>
        lightUserApi.async(player.userId) map { lu =>
          rank = rank + 1
          Player.Result(player, lu | LightUser.fallback(player.userId), rank)
        }
      }
  }

  def byOwnerStream(owner: User, perSecond: MaxPerSecond, nb: Int): Enumerator[Tournament] = {
    import reactivemongo.play.iteratees.cursorProducer
    import play.api.libs.iteratee._
    TournamentRepo.cursor(owner, perSecond.value).bulkEnumerator(nb) &>
      lidraughts.common.Iteratee.delay(1 second)(system) &>
      Enumeratee.mapConcat(_.toSeq)
  }

  private def playerPovs(tour: Tournament, userId: User.ID, nb: Int): Fu[List[LightPov]] =
    PairingRepo.recentIdsByTourAndUserId(tour.id, userId, nb) flatMap
      GameRepo.light.gamesFromPrimary map {
        _ flatMap { LightPov.ofUserId(_, userId) }
      }

  def byOwnerPager(owner: User, page: Int): Fu[Paginator[Tournament]] = Paginator(
    adapter = TournamentRepo.byOwnerAdapter(owner.id),
    currentPage = page,
    maxPerPage = MaxPerPage(20)
  )

  private def Sequencing(tourId: Tournament.ID)(fetch: Tournament.ID => Fu[Option[Tournament]])(run: Tournament => Funit): Unit =
    doSequence(tourId) {
      fetch(tourId) flatMap {
        case Some(t) => run(t)
        case None => fufail(s"Can't run sequenced operation on missing tournament $tourId")
      }
    }

  private def doSequence(tourId: Tournament.ID)(fu: => Funit): Unit =
    sequencers.tell(tourId, Duct.extra.LazyFu(() => fu))

  private def socketReload(tourId: Tournament.ID): Unit = socketMap.tell(tourId, Reload)

  private object publish {
    private val debouncer = system.actorOf(Props(new Debouncer(15 seconds, {
      (_: Debouncer.Nothing) =>
        fetchVisibleTournaments flatMap apiJsonView.apply foreach { json =>
          bus.publish(
            SendToFlag("tournament", Json.obj("t" -> "reload", "d" -> json)),
            'sendToFlag
          )
        }
        TournamentRepo.promotable foreach { tours =>
          renderer ? TournamentTable(tours) map {
            case view: String => bus.publish(ReloadTournaments(view), 'lobbySocket)
          }
        }
    })))
    def apply(): Unit = { debouncer ! Debouncer.Nothing }
  }

  private object updateTournamentStanding {

    import lidraughts.hub.EarlyMultiThrottler
    import com.github.blemale.scaffeine.{ Cache, Scaffeine }

    // last published top hashCode
    private val lastPublished: Cache[Tournament.ID, Int] = Scaffeine()
      .expireAfterWrite(2 minute)
      .build[Tournament.ID, Int]

    private def publishNow(tourId: Tournament.ID) = tournamentTop(tourId) map { top =>
      val lastHash: Int = ~lastPublished.getIfPresent(tourId)
      if (lastHash != top.hashCode) bus.publish(
        lidraughts.hub.actorApi.round.TourStanding(JsonView.top(top, lightUserApi.sync)),
        Symbol(s"tour-standing-$tourId")
      )
      lastPublished.put(tourId, top.hashCode)
    }

    private val throttler = system.actorOf(Props(new EarlyMultiThrottler(logger = logger)))

    def apply(tour: Tournament): Unit =
      if (!tour.isTeamBattle)
        throttler ! EarlyMultiThrottler.work(
          id = tour.id,
          run = publishNow(tour.id),
          cooldown = 15.seconds
        )
  }
}
