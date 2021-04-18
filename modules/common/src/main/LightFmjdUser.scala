package lidraughts.common

import play.api.libs.json.{ Json, OWrites }

case class LightFmjdUser(
    id: String,
    name: String,
    title: Option[String]
) {

  def shortTitle = title.map(t => if (t.endsWith("-64")) t.dropRight(3) else t)

  def titleName = shortTitle.fold(name)(_ + " " + name)
  def fullTitleName = title.fold(name)(_ + " " + name)
}

object LightFmjdUser {

  implicit val lightFmjdUserWrites = OWrites[LightFmjdUser] { u =>
    Json.obj(
      "id" -> u.id,
      "name" -> u.name
    ).add("title" -> u.title)
  }

  type Getter = String => Fu[Option[LightFmjdUser]]
  type GetterSync = String => Option[LightFmjdUser]
  type IsBotSync = String => Boolean
}
