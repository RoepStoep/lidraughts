package lidraughts.externalTournament

import reactivemongo.bson._

import lidraughts.db.dsl._

private[externalTournament] object BsonHandlers {

  implicit val PlayerStatusBSONHandler = new BSONHandler[BSONInteger, ExternalPlayer.Status] {
    def read(bsonInt: BSONInteger): ExternalPlayer.Status = ExternalPlayer.Status(bsonInt.value) err s"No such status: ${bsonInt.value}"
    def write(x: ExternalPlayer.Status) = BSONInteger(x.id)
  }

  implicit val ExternalTournamentBSONHandler = Macros.handler[ExternalTournament]
  implicit val ExternalPlayerBSONHandler = Macros.handler[ExternalPlayer]
}
