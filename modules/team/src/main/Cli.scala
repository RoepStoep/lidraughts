package lidraughts.team

import lidraughts.db.dsl._
import lidraughts.user.UserRepo

private[team] final class Cli(api: TeamApi, coll: Colls) extends lidraughts.common.Cli {

  import BSONHandlers._

  def process = {

    case "team" :: "enable" :: team :: Nil => perform(team)(api.enable)

    case "team" :: "disable" :: team :: Nil => perform(team)(api.disable)

    case "team" :: "wfd" :: team :: Nil => perform(team)(api.setWfd)

    case "team" :: "recompute" :: "nbMembers" :: "all" :: Nil =>
      api.recomputeNbMembers
      fuccess("In progress... it will take a while")

    case "team" :: "recompute" :: "nbMembers" :: teamId :: Nil =>
      api.recomputeNbMembers(teamId) inject "done"
  }

  private def perform(teamId: String)(op: Team => Funit): Fu[String] =
    coll.team.byId[Team](teamId) flatMap {
      _.fold(fufail[String]("Team not found")) { u => op(u) inject "Success" }
    }
}
