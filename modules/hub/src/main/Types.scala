package lidraughts.hub

package object lightTeam {
  type TeamId = String
  type TeamName = String
  case class LightTeam(_id: TeamId, name: TeamName, wfd: Option[Boolean]) {
    def id = _id
    def pair = id -> name
    def isWfd = ~wfd
  }
}
