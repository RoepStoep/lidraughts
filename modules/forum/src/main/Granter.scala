package lidraughts.forum

import lidraughts.security.{ Permission, Granter => Master }
import lidraughts.user.{ User, UserContext }

trait Granter {

  protected val TeamSlugPattern = """team-([\w-]++)""".r

  protected def userBelongsToTeam(teamId: String, userId: String): Fu[Boolean]
  protected def userOwnsTeam(teamId: String, userId: String): Fu[Boolean]

  def isGrantedRead(categSlug: String)(implicit ctx: UserContext): Boolean =
    categSlug != Categ.staffId || ctx.me.exists(Master(Permission.StaffForum))

  def isGrantedWrite(categSlug: String)(implicit ctx: UserContext): Fu[Boolean] =
    ctx.me.filter(isOldEnoughToForum) ?? { me =>
      if (Master(Permission.StaffForum)(me)) fuTrue
      else categSlug match {
        case Categ.staffId => fuFalse
        case TeamSlugPattern(teamId) => userBelongsToTeam(teamId, me.id)
        case _ => fuTrue
      }
    }

  private def isOldEnoughToForum(u: User) = {
    u.count.game > 0 && u.createdSinceDays(2)
  } || u.hasTitle || u.isVerified

  def isGrantedMod(categSlug: String)(implicit ctx: UserContext): Fu[Boolean] =
    categSlug match {
      case _ if (ctx.me ?? Master(Permission.ModerateForum)) => fuTrue
      case TeamSlugPattern(teamId) =>
        ctx.me ?? { me => userOwnsTeam(teamId, me.id) }
      case _ => fuFalse
    }
}
