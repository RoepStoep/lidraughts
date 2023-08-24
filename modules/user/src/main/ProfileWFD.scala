package lidraughts.user

case class ProfileWfd(
    firstName: Option[String] = None,
    lastName: Option[String] = None
) {

  def nonEmptyRealName = List(ne(firstName), ne(lastName)).flatten match {
    case Nil => none
    case names => (names mkString " ").some
  }

  private def ne(str: Option[String]) = str.filter(_.nonEmpty)
}

object ProfileWfd {

  val default = ProfileWfd()

  def fromProfile(profile: Option[Profile]): Option[ProfileWfd] =
    profile.map { p =>
      ProfileWfd(p.firstName, p.lastName)
    }

  import reactivemongo.bson.Macros
  private[user] val profileWfdBSONHandler = Macros.handler[ProfileWfd]
}
