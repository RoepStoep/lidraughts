package lidraughts.chat

import lidraughts.common.LightUser
import lidraughts.common.PimpedJson._
import play.api.libs.json._

object JsonView {

  def apply(chat: AnyChat): JsValue = chat match {
    case c: MixedChat => mixedChatWriter writes c
    case c: UserChat => userChatWriter writes c
    case c: PimpedUserChat => pimpedUserChatWriter writes c
  }

  def apply(line: Line): JsValue = lineWriter writes line

  def userModInfo(u: UserModInfo)(implicit lightUser: LightUser.GetterSync) =
    lidraughts.user.JsonView.modWrites.writes(u.user) ++ Json.obj(
      "history" -> u.history
    )

  def mobile(chat: AnyChat, writeable: Boolean = true) = Json.obj(
    "lines" -> apply(chat),
    "writeable" -> writeable
  )

  implicit val chatIdWrites: Writes[Chat.Id] = stringIsoWriter(Chat.chatIdIso)

  lazy val timeoutReasons = Json toJson ChatTimeout.Reason.all

  implicit val timeoutReasonWriter: Writes[ChatTimeout.Reason] = OWrites[ChatTimeout.Reason] { r =>
    Json.obj("key" -> r.key, "name" -> r.name)
  }

  implicit def timeoutEntryWriter(implicit lightUser: LightUser.GetterSync) = OWrites[ChatTimeout.UserEntry] { e =>
    Json.obj(
      "reason" -> e.reason.key,
      "mod" -> lightUser(e.mod).fold("?")(_.name),
      "date" -> e.createdAt
    )
  }

  implicit val mixedChatWriter: Writes[MixedChat] = Writes[MixedChat] { c =>
    JsArray(c.lines map lineWriter.writes)
  }

  implicit val userChatWriter: Writes[UserChat] = Writes[UserChat] { c =>
    JsArray(c.lines map userLineWriter.writes)
  }

  implicit val pimpedUserChatWriter: Writes[PimpedUserChat] = Writes[PimpedUserChat] { c =>
    JsArray(c.lines map pimpedUserLineWriter.writes)
  }

  private[chat] implicit val lineWriter: Writes[Line] = Writes[Line] {
    case l: UserLine => userLineWriter writes l
    case l: PimpedUserLine => pimpedUserLineWriter writes l
    case l: PlayerLine => playerLineWriter writes l
  }

  private implicit val userLineWriter = Writes[UserLine] { l =>
    Json.obj(
      "u" -> l.username,
      "t" -> l.text
    ).add("r" -> l.troll).add("d" -> l.deleted).add("title" -> l.title)
  }

  private implicit val pimpedUserLineWriter = Writes[PimpedUserLine] { l =>
    Json.obj(
      "u" -> l.username,
      "n" -> l.displayName,
      "t" -> l.text
    ).add("r" -> l.troll).add("d" -> l.deleted).add("title" -> l.title)
  }

  private implicit val playerLineWriter = Writes[PlayerLine] { l =>
    Json.obj(
      "c" -> l.color.name,
      "t" -> l.text
    )
  }
}
