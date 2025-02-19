package lidraughts.relay

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import lidraughts.common.Form.{ cleanNonEmptyText, cleanText }
import lidraughts.user.User
import lidraughts.security.Granter

object RelayForm {

  import lidraughts.common.Form.UTCDate._

  val maxHomepageHours = 72

  val form = Form(mapping(
    "name" -> cleanText(minLength = 3, maxLength = 80),
    "description" -> cleanText(minLength = 3, maxLength = 400),
    "markup" -> optional(cleanText(maxLength = 20000)),
    "official" -> optional(boolean),
    "homepageHours" -> optional(number(min = 0, max = maxHomepageHours)),
    "syncUrl" -> nonEmptyText.verifying("Lidraughts tournaments can't be used as broadcast source", u => !isTournamentApi(u)),
    "gameIndices" -> optional(nonEmptyText.verifying("Invalid game indices", u => isCommaSeparatedNumbers(u))),
    "gameIds" -> optional(nonEmptyText.verifying("Invalid game IDs", u => isCommaSeparatedGameIds(u))),
    "simulId" -> optional(nonEmptyText),
    "withProfileName" -> optional(boolean),
    "credit" -> optional(cleanNonEmptyText),
    "startsAt" -> optional(utcDate),
    "throttle" -> optional(number(min = 2, max = 60))
  )(Data.apply)(Data.unapply))

  private def isTournamentApi(url: String) =
    """/api/tournament/\w{8}/games""".r.find(url)

  private def isCommaSeparatedNumbers(indices: String) =
    indices.split(',').forall(_ forall Character.isDigit)

  private def isCommaSeparatedGameIds(indices: String) =
    indices.split(',').forall(lidraughts.game.Game.validId)

  def create = form

  def edit(r: Relay) = form fill Data.make(r)

  case class Data(
      name: String,
      description: String,
      markup: Option[String],
      official: Option[Boolean],
      homepageHours: Option[Int],
      syncUrl: String,
      gameIndices: Option[String],
      gameIds: Option[String],
      simulId: Option[String],
      withProfileName: Option[Boolean],
      credit: Option[String],
      startsAt: Option[DateTime],
      throttle: Option[Int]
  ) {

    def cleanUrl = {
      val trimmed = syncUrl.trim
      if (trimmed endsWith "/") trimmed.take(trimmed.size - 1)
      else trimmed
    }

    def update(relay: Relay, user: User) = {
      val isOfficial = ~official && Granter(_.Admin)(user)
      relay.copy(
        name = name,
        description = description,
        markup = markup,
        official = isOfficial,
        homepageHours = isOfficial ?? homepageHours,
        sync = makeSync,
        credit = credit,
        startsAt = startsAt,
        finished = relay.finished && startsAt.fold(true)(_.isBefore(DateTime.now))
      )
    }

    def makeSync = Relay.Sync(
      upstream = Relay.Sync.Upstream(cleanUrl),
      indices = gameIndices.map(_.split(',').flatMap(parseIntOption).toList),
      gameIds = gameIds.map(_.split(',').toList),
      simulId = simulId.filter(_.nonEmpty),
      withProfileName = withProfileName,
      until = none,
      nextAt = none,
      delay = throttle,
      log = SyncLog.empty
    )

    def make(user: User) = {
      val isOfficial = ~official && Granter(_.Admin)(user)
      Relay(
        _id = Relay.makeId,
        name = name,
        description = description,
        markup = markup,
        ownerId = user.id,
        sync = makeSync,
        credit = credit,
        likes = lidraughts.study.Study.Likes(1),
        createdAt = DateTime.now,
        finished = false,
        official = isOfficial,
        homepageHours = isOfficial ?? homepageHours,
        startsAt = startsAt,
        startedAt = none
      )
    }
  }

  object Data {

    def make(relay: Relay) = Data(
      name = relay.name,
      description = relay.description,
      markup = relay.markup,
      official = relay.official option true,
      homepageHours = relay.official ?? relay.homepageHours,
      syncUrl = relay.sync.upstream.url,
      gameIndices = relay.sync.indices.map(_.mkString(",")),
      gameIds = relay.sync.gameIds.map(_.mkString(",")),
      simulId = relay.sync.simulId,
      withProfileName = relay.sync.withProfileName,
      credit = relay.credit,
      startsAt = relay.startsAt,
      throttle = relay.sync.delay
    )
  }
}
