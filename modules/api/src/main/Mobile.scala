package lidraughts.api

import org.joda.time.DateTime
import play.api.http.HeaderNames
import play.api.mvc.RequestHeader

import lidraughts.common.ApiVersion

object Mobile {

  object AppVersion {

    def mustUpgrade(v: String) = mustUpgradeFromVersions(v)

    // only call if a more recent version is available in both stores!
    private val mustUpgradeFromVersions = Set(
      "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4", /* no support for 64-variants */
      "1.1.0", "1.1.1" /* no support for brazilian draughts */
    // "1.2.0", "1.3.0" /* cordova versions */
    )

  }

  object Api {

    case class Old(
        version: ApiVersion,
        // date when a newer version was released
        deprecatedAt: DateTime,
        // date when the server stops accepting requests
        unsupportedAt: DateTime
    )

    val currentVersion = ApiVersion(3)

    val acceptedVersions: Set[ApiVersion] = Set(1, 2, 3) map ApiVersion.apply

    val oldVersions: List[Old] = List( // Old( // old ping API
    //   version = ApiVersion(2),
    //   deprecatedAt = new DateTime("2019-11-25"),
    //   unsupportedAt = new DateTime("2023-11-12")
    // )
    )

    private val PathPattern = """/socket/v(\d++)$""".r.unanchored
    private val HeaderPattern = """application/vnd\.lidraughts\.v(\d++)\+json""".r

    def requestVersion(req: RequestHeader): Option[ApiVersion] = {
      (req.headers.get(HeaderNames.ACCEPT), req.path) match {
        case (Some(HeaderPattern(v)), _) => parseIntOption(v) map ApiVersion.apply
        case (_, PathPattern(v)) => parseIntOption(v) map ApiVersion.apply
        case _ => none
      }
    } filter acceptedVersions.contains

    def requested(req: RequestHeader) = requestVersion(req).isDefined
  }
}
