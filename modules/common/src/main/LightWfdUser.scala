package lidraughts.common

import play.api.libs.json.{ Json, OWrites }

case class LightWfdUser(
    name: String,
    username: String,
    title: Option[String],
    isPatron: Boolean
) {

  def id = username.toLowerCase

  def shortTitle = title.map(t => if (t.endsWith("-64")) t.dropRight(3) else t)

  def titleName = shortTitle.fold(name)(_ + " " + name)
  def fullTitleName = title.fold(name)(_ + " " + name)
}

object LightWfdUser {

  implicit val lightWfdUserWrites = OWrites[LightWfdUser] { u =>
    Json.obj(
      "id" -> u.id,
      "name" -> u.name
    ).add("title" -> u.title)
      .add("patron" -> u.isPatron)
  }

  def fallback(userId: String) = LightWfdUser(
    name = userId,
    username = userId,
    title = None,
    isPatron = false
  )

  type Getter = String => Fu[Option[LightWfdUser]]
  type GetterSync = String => Option[LightWfdUser]
}