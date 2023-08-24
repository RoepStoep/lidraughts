package lidraughts.common

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

  type Getter = String => Fu[Option[LightWfdUser]]
  type GetterSync = String => Option[LightWfdUser]
}