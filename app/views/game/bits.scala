package views.html.game

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.{ Game, Pov, Player }
import lidraughts.user.Title

import controllers.routes

object bits {

  def gameIcon(game: Game): Char = game.perfType match {
    case _ if game.fromPosition => '*'
    case _ if game.imported => '/'
    case Some(p) if game.variant.exotic => p.iconChar
    case _ if game.hasAi => 'n'
    case Some(p) => p.iconChar
    case _ => '8'
  }

  def sides(
    pov: Pov,
    initialFen: Option[draughts.format.FEN],
    tour: Option[lidraughts.tournament.TourAndTeamVs],
    cross: Option[lidraughts.game.Crosstable.WithMatchup],
    simul: Option[lidraughts.simul.Simul],
    userTv: Option[lidraughts.user.User] = None,
    bookmarked: Boolean
  )(implicit ctx: Context) = div(
    side.meta(pov, initialFen, tour, simul, userTv, bookmarked = bookmarked),
    cross.map { c =>
      div(cls := "crosstable")(crosstable(ctx.userId.fold(c)(c.fromPov), pov.gameId.some))
    }
  )

  def variantLink(
    variant: draughts.variant.Variant,
    name: String,
    initialFen: Option[draughts.format.FEN] = None
  )(implicit ctx: Context) = a(
    cls := "variant-link",
    href := (variant match {
      case draughts.variant.FromPosition => s"""${routes.Editor.index}?fen=${initialFen.??(_.value.replace(' ', '_'))}"""
      case v => routes.Page.variant(v.key).url
    }),
    rel := "nofollow",
    target := "_blank",
    title := variantTitle(variant)
  )(name)

  private def playerTitle(player: Player) =
    lightUser(player.userId).flatMap(_.title) map Title.apply map { t =>
      val title64 = t.is64
      span(
        cls := "title",
        dataBot(t),
        title64 option dataTitle64,
        title := Title titleName t
      )(if (title64) t.value.dropRight(3) else t.value)
    }

  def vstext(pov: Pov)(ctxOption: Option[Context]): Frag =
    span(cls := "vstext")(
      span(cls := "vstext__pl user-link")(
        playerUsername(pov.player, withRating = false, withTitle = false),
        br,
        playerTitle(pov.player) map { t => frag(t, " ") },
        pov.player.rating,
        pov.player.provisional option "?"
      ),
      pov.game.clock map { c =>
        span(cls := "vstext__clock")(shortClockName(c.config))
      } orElse {
        ctxOption flatMap { implicit ctx =>
          pov.game.daysPerTurn map { days =>
            span(cls := "vstext__clock")(
              if (days == 1) trans.oneDay() else trans.nbDays.pluralSame(days)
            )
          }
        }
      },
      span(cls := "vstext__op user-link")(
        playerUsername(pov.opponent, withRating = false, withTitle = false),
        br,
        pov.opponent.rating,
        pov.opponent.provisional option "?",
        playerTitle(pov.opponent) map { t => frag(" ", t) }
      )
    )
}
