package lidraughts.externalTournament

import reactivemongo.bson._

import lidraughts.db.dsl._

private[externalTournament] object BsonHandlers {

  implicit val ExternalTournamentBsonHandler = Macros.handler[ExternalTournament]
  implicit val ExternalPlayerBsonHandler = Macros.handler[ExternalPlayer]
}
