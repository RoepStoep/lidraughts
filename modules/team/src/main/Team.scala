package lidraughts.team

import org.joda.time.DateTime
import ornicar.scalalib.Random

import lidraughts.user.User

case class Team(
    _id: Team.ID, // also the url slug
    name: String,
    location: Option[String],
    description: String,
    nbMembers: Int,
    enabled: Boolean,
    open: Boolean,
    createdAt: DateTime,
    createdBy: User.ID,
    chat: Boolean,
    wfd: Option[Boolean] = None
) {

  def id = _id

  def slug = id

  def disabled = !enabled

  def isCreator(user: String) = user == createdBy

  def light = lidraughts.hub.lightTeam.LightTeam(_id, name, wfd)

  def isWfd = ~wfd
}

object Team {

  case class Mini(id: Team.ID, name: String)

  type ID = String

  case class IdsStr(value: String) extends AnyVal {

    import IdsStr.separator

    def contains(teamId: ID) =
      value == teamId ||
        value.startsWith(s"$teamId$separator") ||
        value.endsWith(s"$separator$teamId") ||
        value.contains(s"$separator$teamId$separator")

    def toArray: Array[String] = value split IdsStr.separator
    def toList = value.nonEmpty ?? toArray.toList
  }

  object IdsStr {

    private val separator = ' '

    val empty = IdsStr("")

    def apply(ids: Iterable[ID]): IdsStr = IdsStr(ids mkString separator.toString)
  }

  def make(
    name: String,
    location: Option[String],
    description: String,
    open: Boolean,
    createdBy: User
  ): Team = new Team(
    _id = nameToId(name),
    name = name,
    location = location,
    description = description,
    nbMembers = 1,
    enabled = true,
    open = open,
    createdAt = DateTime.now,
    createdBy = createdBy.id,
    chat = true
  )

  def nameToId(name: String) = (lidraughts.common.String slugify name) |> { slug =>
    // if most chars are not latin, go for random slug
    if (slug.size > (name.size / 2)) slug else Random nextString 8
  }
}
