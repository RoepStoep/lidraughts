package lidraughts.externalTournament

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

object DataForm {

  import lidraughts.common.Form.cleanNonEmptyText

  val form = Form(mapping(
    "name" -> cleanNonEmptyText(minLength = 3, maxLength = 40)
  )(Data.apply)(Data.unapply)) fill Data(
    name = ""
  )

  case class Data(
      name: String
  ) {

    def make(userId: String) = ExternalTournament(
      _id = ExternalTournament.makeId,
      name = name,
      createdBy = userId
    )
  }

  object Data {

    def make(tour: ExternalTournament) = Data(
      name = tour.name
    )
  }
}
