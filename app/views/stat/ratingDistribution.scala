package views.html
package stat

import play.api.libs.json.Json

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.safeJsonValue
import lidraughts.rating.PerfType

import controllers.routes

object ratingDistribution {

  def apply(perfType: PerfType, data: List[Int])(implicit ctx: Context) = views.html.base.layout(
    title = trans.monthlyPerfTypeRatingDistribution.txt(perfType.trans),
    moreCss = cssTag("user.rating.stats"),
    wrapClass = "full-screen-force",
    moreJs = frag(
      jsTag("chart/ratingDistribution.js"),
      embedJsUnsafe(s"""lidraughts.ratingDistributionChart(${
        safeJsonValue(Json.obj(
          "freq" -> data,
          "myRating" -> ctx.me.map { me => me.perfs(perfType).intRating },
          "i18n" -> i18nJsObject(List(
            trans.players,
            trans.yourRating,
            trans.cumulative,
            trans.glicko2Rating
          ))
        ))
      })""")
    )
  ) {
      main(cls := "page-menu")(
        user.bits.communityMenu("ratings"),
        div(cls := "rating-stats page-menu__content box box-pad")(
          h1(trans.monthlyPerfTypeRatingDistribution(views.html.base.bits.mselect(
            "variant-stats",
            span(perfType.trans),
            PerfType.leaderboardable map { pt =>
              a(
                dataIcon := pt.iconChar,
                cls := (perfType == pt).option("current"),
                href := routes.Stat.ratingDistribution(pt.key)
              )(pt.trans)
            }
          ))),
          div(cls := "desc", dataIcon := perfType.iconChar)(
            ctx.me.flatMap(_.perfs(perfType).glicko.establishedIntRating).map { rating =>
              lidraughts.user.Stat.percentile(data, rating) match {
                case (under, sum) => div(
                  trans.nbPerfTypePlayersThisMonth(raw(s"""<strong>${sum.localize}</strong>"""), perfType.trans), br,
                  trans.yourPerfTypeRatingIsRating(perfType.trans, raw(s"""<strong>$rating</strong>""")), br,
                  trans.youAreBetterThanPercentOfPerfTypePlayers(raw(s"""<strong>${"%.1f" format under * 100.0 / sum}%</strong>"""), perfType.trans)
                )
              }
            } getOrElse div(
              trans.nbPerfTypePlayersThisMonth.plural(data.sum, raw(s"""<strong>${data.sum.localize}</strong>"""), perfType.trans), br,
              trans.youDoNotHaveAnEstablishedPerfTypeRating(perfType.trans)
            )
          ),
          div(id := "rating_distribution_chart")(spinner)
        )
      )
    }

}
