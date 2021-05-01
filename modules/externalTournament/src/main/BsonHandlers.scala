package lidraughts.externalTournament

import draughts.Clock.{ Config => ClockConfig }
import draughts.variant.Variant
import reactivemongo.bson._

import lidraughts.db.dsl._

private[externalTournament] object BsonHandlers {

  import ExternalTournament._
  import PlayerInfo.Bye

  implicit val ClockConfigBSONHandler = new BSONHandler[BSONDocument, ClockConfig] {
    def read(doc: BSONDocument) = ClockConfig(
      doc.getAs[Int]("limit").get,
      doc.getAs[Int]("increment").get
    )

    def write(config: ClockConfig) = BSONDocument(
      "limit" -> config.limitSeconds,
      "increment" -> config.incrementSeconds
    )
  }

  implicit val VariantBSONHandler = new BSONHandler[BSONInteger, Variant] {
    def read(b: BSONInteger): Variant = Variant.orDefault(b.value)
    def write(x: Variant) = BSONInteger(x.id)
  }

  implicit val UserDisplayBSONHandler = new BSONHandler[BSONString, UserDisplay] {
    def read(b: BSONString): UserDisplay = UserDisplay(b.value) err s"No such userDisplay: ${b.value}"
    def write(x: UserDisplay) = BSONString(x.key)
  }

  implicit val ChatVisibilityBSONHandler = new BSONHandler[BSONString, ChatVisibility] {
    def read(b: BSONString): ChatVisibility = ChatVisibility(b.value) err s"No such ChatVisibility: ${b.value}"
    def write(x: ChatVisibility) = BSONString(x.key)
  }

  implicit val PlayerStatusBSONHandler = new BSONHandler[BSONInteger, ExternalPlayer.Status] {
    def read(bsonInt: BSONInteger): ExternalPlayer.Status = ExternalPlayer.Status(bsonInt.value) err s"No such status: ${bsonInt.value}"
    def write(x: ExternalPlayer.Status) = BSONInteger(x.id)
  }

  implicit val ByeBSONHandler = Macros.handler[Bye]
  implicit val ExternalTournamentSettingsBSONHandler = Macros.handler[Settings]
  implicit val ExternalTournamentBSONHandler = Macros.handler[ExternalTournament]
  implicit val ExternalPlayerBSONHandler = Macros.handler[ExternalPlayer]
  implicit val FmjdPlayerBSONHandler = Macros.handler[FmjdPlayer]
  implicit val GameMetaBSONHandler = Macros.handler[GameMeta]
}
