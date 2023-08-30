package lidraughts.team

import lidraughts.user.LightUserApi

import play.api.libs.json._

final class JsonView(lightUserApi: LightUserApi) {

  implicit val teamWrites = OWrites[Team] { team =>
    Json
      .obj(
        "id" -> team.id,
        "name" -> team.name,
        "description" -> team.description,
        "open" -> team.open,
        "leader" -> lightUserApi.sync(team.createdBy),
        "nbMembers" -> team.nbMembers
      )
      .add("location" -> team.location)
  }
}
