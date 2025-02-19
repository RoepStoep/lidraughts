package lidraughts.oauth

sealed abstract class OAuthScope(val key: String, val name: String) {
  override def toString = s"Scope($key)"
}

object OAuthScope {

  object Preference {
    case object Read extends OAuthScope("preference:read", "Read preferences")
    case object Write extends OAuthScope("preference:write", "Write preferences")
  }

  object Email {
    case object Read extends OAuthScope("email:read", "Read email address")
  }

  object Challenge {
    case object Read extends OAuthScope("challenge:read", "Read incoming challenges")
    case object Write extends OAuthScope("challenge:write", "Create, accept, decline challenges")
  }

  object Tournament {
    case object Write extends OAuthScope("tournament:write", "Create and update tournaments")
  }

  object Puzzle {
    case object Read extends OAuthScope("puzzle:read", "Read puzzle activity")
  }

  object Team {
    case object Write extends OAuthScope("team:write", "Join, leave, and manage teams")
  }

  object Bot {
    case object Play extends OAuthScope("bot:play", "Play games with the bot API")
  }

  case class Scoped(user: lidraughts.user.User, scopes: List[OAuthScope])

  type Selector = OAuthScope.type => OAuthScope

  val all = List(
    Preference.Read, Preference.Write,
    Email.Read,
    Challenge.Read, Challenge.Write,
    Tournament.Write,
    Puzzle.Read,
    Team.Write,
    Bot.Play
  )

  val byKey: Map[String, OAuthScope] = all.map { s => s.key -> s } toMap

  def keyList(scopes: Iterable[OAuthScope]) = scopes.map(_.key) mkString ", "

  def select(selectors: Iterable[OAuthScope.type => OAuthScope]) = selectors.map(_(OAuthScope)).toList

  import reactivemongo.bson._
  import lidraughts.db.dsl._
  private[oauth] implicit val scopeHandler = new BSONHandler[BSONString, OAuthScope] {
    def read(b: BSONString): OAuthScope = OAuthScope.byKey.get(b.value) err s"No such scope: ${b.value}"
    def write(s: OAuthScope) = BSONString(s.key)
  }
}
