package lidraughts.swiss

import akka.actor.ActorSystem
import org.joda.time.DateTime
import ornicar.scalalib.Zero
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
import reactivemongo.bson._
import scala.concurrent.duration._

import actorApi._
import lidraughts.chat.{ Chat, ChatApi }
import lidraughts.common.{ Bus, GreatPlayer, LightUser }
import lidraughts.db.dsl._
import lidraughts.game.{ Game, Pov }
import lidraughts.hub.lightTeam.TeamId
import lidraughts.hub.{ Duct, DuctMap }
import lidraughts.round.actorApi.round.QuietFlag
import lidraughts.user.{ User, UserRepo }

final class SwissApi(
    swissColl: Coll,
    playerColl: Coll,
    pairingColl: Coll,
    cache: SwissCache,
    socketMap: SocketMap,
    director: SwissDirector,
    scoring: SwissScoring,
    rankingApi: SwissRankingApi,
    standingApi: SwissStandingApi,
    boardApi: SwissBoardApi,
    chatApi: ChatApi,
    lightUserApi: lidraughts.user.LightUserApi,
    proxyGames: List[Game.ID] => Fu[List[(Game.ID, Option[Game])]],
    bus: Bus
)(implicit system: ActorSystem) {

  private val sequencer =
    new lidraughts.hub.DuctSequencers(
      maxSize = 1024, // queue many game finished events
      expiration = 20 minutes,
      timeout = 10 seconds,
      name = "swiss.api"
    )

  import BsonHandlers._

  def byId(id: Swiss.Id) = swissColl.byId[Swiss](id.value)
  def notFinishedById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isNotFinished))
  def createdById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isCreated))
  def startedById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isStarted))

  def featurable: Fu[(List[Swiss], List[Swiss])] = cache.feature.get

  def create(data: SwissForm.SwissData, me: User, teamId: TeamId): Fu[Swiss] = {
    val swiss = Swiss(
      _id = Swiss.makeId,
      name = data.name | GreatPlayer.randomName,
      clock = data.clock,
      variant = data.realVariant,
      round = SwissRound.Number(0),
      nbPlayers = 0,
      nbOngoing = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      nextRoundAt = data.realStartsAt.some,
      startsAt = data.realStartsAt,
      finishedAt = none,
      winnerId = none,
      settings = Swiss.Settings(
        nbRounds = data.nbRounds,
        rated = data.rated | true,
        description = data.description,
        hasChat = data.hasChat | true,
        roundInterval = data.realRoundInterval
      )
    )
    swissColl.insert(addFeaturable(swiss)) >>-
      cache.featuredInTeam.invalidate(swiss.teamId) inject swiss
  }

  def update(swiss: Swiss, data: SwissForm.SwissData): Funit =
    Sequencing(swiss.id)(byId) { old =>
      swissColl
        .update(
          $id(old.id),
          old.copy(
            name = data.name | old.name,
            clock = data.clock,
            variant = data.realVariant,
            startsAt = data.startsAt.ifTrue(old.isCreated) | old.startsAt,
            nextRoundAt =
              if (old.isCreated) Some(data.startsAt | old.startsAt)
              else old.nextRoundAt,
            settings = old.settings.copy(
              nbRounds = data.nbRounds,
              rated = data.rated | old.settings.rated,
              description = data.description,
              hasChat = data.hasChat | old.settings.hasChat,
              roundInterval =
                if (data.roundInterval.isDefined) data.realRoundInterval
                else old.settings.roundInterval
            )
          ) |> { s =>
              if (s.isStarted && s.nbOngoing == 0 && (s.nextRoundAt.isEmpty || old.settings.manualRounds) && !s.settings.manualRounds)
                s.copy(nextRoundAt = DateTime.now.plusSeconds(s.settings.roundInterval.toSeconds.toInt).some)
              else if (s.isStarted && s.settings.manualRounds && !old.settings.manualRounds)
                s.copy(nextRoundAt = none)
              else s
            } |> addFeaturable
        )
        .void >>- socketReload(swiss.id)
    }

  def scheduleNextRound(swiss: Swiss, date: DateTime): Funit =
    Sequencing(swiss.id)(notFinishedById) { old =>
      old.settings.manualRounds ?? {
        if (old.isCreated)
          swissColl.updateField($id(old.id), "startsAt", date) >>
            swissColl.updateField($id(old.id), "nextRoundAt", date).void.void
        else if (old.isStarted && old.nbOngoing == 0)
          swissColl.updateField($id(old.id), "nextRoundAt", date).void >>- {
            val show = org.joda.time.format.DateTimeFormat.forStyle("MS") print date
            systemChat(swiss.id, s"Round ${swiss.round.value + 1} scheduled at $show UTC")
          }
        else funit
      } >>- socketReload(swiss.id)
    }

  def join(id: Swiss.Id, me: User, isInTeam: TeamId => Boolean): Fu[Boolean] =
    Sequencing(id)(notFinishedById) { swiss =>
      playerColl // try a rejoin first
        .updateField($id(SwissPlayer.makeId(swiss.id, me.id)), SwissPlayer.Fields.absent, false)
        .flatMap { rejoin =>
          fuccess(rejoin.n == 1) >>| { // if the match failed (not the update!), try a join
            (swiss.isEnterable && isInTeam(swiss.teamId)) ?? {
              playerColl.insert(SwissPlayer.make(swiss.id, me, swiss.perfLens)) zip
                swissColl.update($id(swiss.id), $inc("nbPlayers" -> 1)) inject true
            }
          }
        }
    } flatMap { res =>
      recomputeAndUpdateAll(id) inject res
    }

  def sortedGameIdsCursor(
    swissId: Swiss.Id,
    batchSize: Int = 0,
    readPreference: ReadPreference = ReadPreference.secondaryPreferred
  )(implicit cp: CursorProducer[Bdoc]) =
    SwissPairing.fields { f =>
      val query = pairingColl
        .find($doc(f.swissId -> swissId), $id(true))
        .sort($sort asc f.round)
      query.copy(options = query.options.batchSize(batchSize)).cursor[Bdoc](readPreference)
    }

  def featuredInTeam(teamId: TeamId): Fu[List[Swiss]] =
    cache.featuredInTeam.get(teamId) flatMap { ids =>
      swissColl.byOrderedIds[Swiss, Swiss.Id](ids)(_.id)
    }

  def visibleInTeam(teamId: TeamId, nb: Int): Fu[List[Swiss]] =
    swissColl.find($doc("teamId" -> teamId)).sort($sort desc "startsAt").list[Swiss](nb)

  def playerInfo(swiss: Swiss, userId: User.ID): Fu[Option[SwissPlayer.ViewExt]] =
    UserRepo named userId flatMap {
      _ ?? { user =>
        playerColl.byId[SwissPlayer](SwissPlayer.makeId(swiss.id, user.id).value) flatMap {
          _ ?? { player =>
            SwissPairing.fields { f =>
              pairingColl
                .find($doc(f.swissId -> swiss.id, f.players -> player.userId))
                .sort($sort asc f.round)
                .list[SwissPairing]()
            } flatMap {
              pairingViews(_, player)
            } flatMap { pairings =>
              SwissPlayer.fields { f =>
                playerColl.countSel($doc(f.swissId -> swiss.id, f.score $gt player.score)).dmap(1.+)
              } map { rank =>
                val pairingMap = pairings.view.map { p =>
                  p.pairing.round -> p
                }.toMap
                SwissPlayer
                  .ViewExt(
                    player,
                    rank,
                    user.light,
                    pairingMap,
                    SwissSheet.one(swiss, pairingMap.view.map { case (r, p) => (r, p.pairing) }.toMap, player)
                  )
                  .some
              }
            }
          }
        }
      }
    }

  def pairingViews(pairings: Seq[SwissPairing], player: SwissPlayer): Fu[Seq[SwissPairing.View]] =
    pairings.headOption ?? { first =>
      playerColl
        .find($inIds(pairings.map(_ opponentOf player.userId).map { SwissPlayer.makeId(first.swissId, _) }))
        .list[SwissPlayer]()
        .flatMap { opponents =>
          lightUserApi asyncMany opponents.map(_.userId) map { users =>
            opponents.zip(users) map {
              case (o, u) => SwissPlayer.WithUser(o, u | LightUser.fallback(o.userId))
            }
          } map { opponents =>
            pairings flatMap { pairing =>
              opponents.find(_.player.userId == pairing.opponentOf(player.userId)) map {
                SwissPairing.View(pairing, _)
              }
            }
          }
        }
    }

  def searchPlayers(id: Swiss.Id, term: String, nb: Int): Fu[List[User.ID]] =
    User.couldBeUsername(term) ?? SwissPlayer.fields { f =>
      playerColl.primitive[User.ID](
        selector = $doc(
          f.swissId -> id,
          f.userId $startsWith term.toLowerCase
        ),
        sort = $sort desc f.score,
        nb = nb,
        field = f.userId
      )
    }

  def pageOf(swiss: Swiss, userId: User.ID): Fu[Option[Int]] =
    rankingApi(swiss) map {
      _ get userId map { rank =>
        (rank - 1) / 10 + 1
      }
    }

  def gameView(pov: Pov): Fu[Option[GameView]] =
    (pov.game.swissId.map(Swiss.Id.apply) ?? byId) flatMap {
      _ ?? { swiss =>
        getGameRanks(swiss, pov.game) dmap {
          GameView(swiss, _).some
        }
      }
    }

  private def getGameRanks(swiss: Swiss, game: Game): Fu[Option[GameRanks]] =
    ~{
      game.whitePlayer.userId.ifTrue(swiss.isStarted) flatMap { whiteId =>
        game.blackPlayer.userId map { blackId =>
          rankingApi(swiss) map { ranking =>
            ranking.get(whiteId) |@| ranking.get(blackId) apply {
              case (whiteR, blackR) => GameRanks(whiteR, blackR)
            }
          }
        }
      }
    }

  private[swiss] def kickFromTeam(teamId: TeamId, userId: User.ID) =
    swissColl
      .primitive[Swiss.Id]($doc("teamId" -> teamId, "featurable" -> true), "_id")
      .flatMap { swissIds =>
        playerColl.distinct[Swiss.Id, Seq](
          "s",
          $inIds(swissIds.map { SwissPlayer.makeId(_, userId) }).some
        )
      }
      .flatMap { kickFromSwissIds(userId, _) }

  private[swiss] def kickLame(userId: User.ID) =
    bus
      .ask[List[TeamId]]('teamJoinedBy)(lidraughts.hub.actorApi.team.TeamIdsJoinedBy(userId, _))
      .flatMap { teamIds =>
        import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
        swissColl.aggregateList(
          Match($doc("teamId" $in teamIds, "featurable" -> true)),
          List(
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "as" -> "player",
                  "from" -> playerColl.name,
                  "let" -> $doc("s" -> "$_id"),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> $doc(
                          "$and" -> $arr(
                            $doc("$eq" -> $arr("$u", userId)),
                            $doc("$eq" -> $arr("$s", "$$s"))
                          )
                        )
                      )
                    )
                  )
                )
              )
            ),
            Match("player" $ne $arr()),
            Project($id(true))
          ),
          maxDocs = 100
        )
      }
      .map(_.flatMap(_.getAs[Swiss.Id]("_id")))
      .flatMap { kickFromSwissIds(userId, _) }

  private def kickFromSwissIds(userId: User.ID, swissIds: Seq[Swiss.Id]): Funit =
    swissIds.map { withdraw(_, userId) }.sequenceFu.void

  def withdraw(id: Swiss.Id, userId: User.ID): Funit =
    Sequencing(id)(notFinishedById) { swiss =>
      SwissPlayer.fields { f =>
        val selId = $id(SwissPlayer.makeId(swiss.id, userId))
        if (swiss.isStarted)
          playerColl.updateField(selId, f.absent, true)
        else
          playerColl.remove(selId) flatMap { res =>
            (res.n == 1) ?? swissColl.update($id(swiss.id), $inc("nbPlayers" -> -1)).void
          }
      }.void >>- recomputeAndUpdateAll(id)
    }

  private[swiss] def finishGame(game: Game): Funit =
    game.swissId.map(Swiss.Id) ?? { swissId =>
      Sequencing(swissId)(byId) { swiss =>
        if (!swiss.isStarted) {
          logger.info(s"Removing pairing ${game.id} finished after swiss ${swiss.id}")
          pairingColl.remove($id(game.id)) inject false
        } else
          pairingColl
            .updateField(
              $id(game.id),
              SwissPairing.Fields.status,
              pairingStatusHandler.writeTry(Right(game.winnerColor)).get
            )
            .flatMap { result =>
              if (result.nModified == 0) fuccess(false) // dedup
              else {
                if (swiss.nbOngoing > 0)
                  swissColl.update($id(swiss.id), $inc("nbOngoing" -> -1))
                else
                  fuccess {
                    logger.warn(s"swiss ${swiss.id} nbOngoing = ${swiss.nbOngoing}")
                  }
              } >>
                game.playerWhoDidNotMove.flatMap(_.userId).?? { absent =>
                  SwissPlayer.fields { f =>
                    playerColl
                      .updateField($doc(f.swissId -> swiss.id, f.userId -> absent), f.absent, true)
                      .void
                  }
                } >> {
                  (swiss.nbOngoing <= 1) ?? {
                    if (swiss.round.value == swiss.settings.nbRounds) doFinish(swiss)
                    else if (swiss.settings.manualRounds) fuccess {
                      systemChat(swiss.id, s"Round ${swiss.round.value + 1} needs to be scheduled.")
                    }
                    else
                      swissColl
                        .updateField(
                          $id(swiss.id),
                          "nextRoundAt",
                          swiss.settings.dailyInterval match {
                            case Some(days) => game.createdAt plusDays days
                            case None =>
                              DateTime.now.plusSeconds(swiss.settings.roundInterval.toSeconds.toInt)
                          }
                        )
                        .void >>-
                        systemChat(swiss.id, s"Round ${swiss.round.value + 1} will start soon.")
                  }
                } inject true
            }
      }.flatMap {
        case true => recomputeAndUpdateAll(swissId)
        case _ => funit
      }
    }

  private[swiss] def destroy(swiss: Swiss): Funit =
    swissColl.remove($id(swiss.id)) >>
      pairingColl.remove($doc(SwissPairing.Fields.swissId -> swiss.id)) >>
      playerColl.remove($doc(SwissPairing.Fields.swissId -> swiss.id)).void >>- {
        cache.featuredInTeam.invalidate(swiss.teamId)
        socketReload(swiss.id)
      }

  private[swiss] def finish(oldSwiss: Swiss): Funit =
    Sequencing(oldSwiss.id)(startedById) { swiss =>
      pairingColl.countSel($doc(SwissPairing.Fields.swissId -> swiss.id)) flatMap {
        case 0 => destroy(swiss)
        case _ => doFinish(swiss)
      }
    }
  private def doFinish(swiss: Swiss): Funit =
    SwissPlayer
      .fields { f =>
        playerColl.primitiveOne[User.ID]($doc(f.swissId -> swiss.id), $sort desc f.score, f.userId)
      }
      .flatMap { winnerUserId =>
        swissColl
          .update(
            $id(swiss.id),
            $unset("nextRoundAt", "lastRoundAt", "featurable") ++ $set(
              "settings.n" -> swiss.round,
              "finishedAt" -> DateTime.now,
              "winnerId" -> winnerUserId
            )
          )
          .void zip
          SwissPairing.fields { f =>
            pairingColl.remove($doc(f.swissId -> swiss.id, f.status -> true)) map { res =>
              if (res.n > 0) logger.warn(s"Swiss ${swiss.id} finished with ${res.n} ongoing pairings")
            }
          } void
      } >>- {
        systemChat(swiss.id, s"Tournament completed!")
        cache.featuredInTeam.invalidate(swiss.teamId)
        socketReload(swiss.id)
      }

  def kill(swiss: Swiss): Funit =
    if (swiss.isStarted)
      finish(swiss) >>- systemChat(swiss.id, s"Tournament cancelled by its creator.")
    else if (swiss.isCreated) destroy(swiss)
    else funit

  private def recomputeAndUpdateAll(id: Swiss.Id): Funit =
    scoring(id).flatMap {
      _ ?? { res =>
        rankingApi.update(res)
        standingApi.update(res) >>
          boardApi.update(res) >>-
          socketReload(id)
      }
    }

  private[swiss] def startPendingRounds: Funit =
    swissColl
      .find($doc("nextRoundAt" $lt DateTime.now), $id(true))
      .list[Bdoc](10)
      .map(_.flatMap(_.getAs[Swiss.Id]("_id")))
      .flatMap { ids =>
        lidraughts.common.Future.applySequentially(ids) { id =>
          Sequencing(id)(notFinishedById) { swiss =>
            if (swiss.round.value >= swiss.settings.nbRounds) doFinish(swiss)
            else if (swiss.nbPlayers >= 2)
              director.startRound(swiss).flatMap {
                _.fold {
                  systemChat(swiss.id, "All possible pairings were played.")
                  doFinish(swiss)
                } {
                  case s if s.nextRoundAt.isEmpty =>
                    systemChat(s.id, s"Round ${s.round.value} started.")
                    funit
                  case s =>
                    systemChat(s.id, s"Round ${s.round.value} failed.", true)
                    swissColl
                      .update($id(s.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(61)))
                      .void
                }
              }
            else {
              if (swiss.startsAt isBefore DateTime.now.minusMinutes(60)) destroy(swiss)
              else {
                systemChat(swiss.id, "Not enough players for first round; delaying start.", true)
                swissColl
                  .update($id(swiss.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(121)))
                  .void
              }
            }
          } >> recomputeAndUpdateAll(id)
        }
      }

  private[swiss] def checkOngoingGames: Funit =
    SwissPairing
      .fields { f =>
        import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
        pairingColl
          .aggregateList(
            Match($doc(f.status -> SwissPairing.ongoing)),
            List(
              GroupField(f.swissId)("ids" -> PushField(f.id))
            ),
            maxDocs = 100
          )
      }
      .map {
        _.flatMap { doc =>
          for {
            swissId <- doc.getAs[Swiss.Id]("_id")
            gameIds <- doc.getAs[List[Game.ID]]("ids")
          } yield swissId -> gameIds
        }
      }
      .flatMap {
        _.map {
          case (swissId, gameIds) =>
            Sequencing[List[Game]](swissId)(byId) { _ =>
              proxyGames(gameIds) map { pairs =>
                val games = pairs.collect { case (_, Some(g)) => g }
                val (finished, ongoing) = games.partition(_.finishedOrAborted)
                val flagged = ongoing.filter(_ outoftime true)
                val missingIds = pairs.collect { case (id, None) => id }
                lidraughts.mon.swiss.games("finished")(finished.size)
                lidraughts.mon.swiss.games("ongoing")(ongoing.size)
                lidraughts.mon.swiss.games("flagged")(flagged.size)
                lidraughts.mon.swiss.games("missing")(missingIds.size)
                if (flagged.nonEmpty)
                  bus.publish(lidraughts.hub.actorApi.map.TellMany(flagged.map(_.id), QuietFlag), 'roundMapTell)
                if (missingIds.nonEmpty)
                  pairingColl.remove($inIds(missingIds))
                finished
              }
            } flatMap {
              _.map(finishGame).sequenceFu.void
            }
        }.sequenceFu.void
      }

  private def systemChat(id: Swiss.Id, text: String, volatile: Boolean = false): Unit =
    chatApi.userChat.service(Chat.Id(id.value), text, volatile)

  private def Sequencing[A: Zero](
    id: Swiss.Id
  )(fetch: Swiss.Id => Fu[Option[Swiss]])(run: Swiss => Fu[A]): Fu[A] =
    sequencer(id.value) {
      fetch(id) flatMap {
        _ ?? run
      }
    }

  private def socketReload(swissId: Swiss.Id): Unit =
    socketMap.tell(swissId.value, Reload)
}
