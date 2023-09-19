package views
package html.tournament

import controllers.routes

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.common.String.html.markdownLinksOrRichText
import lidraughts.tournament.{ Tournament, TournamentShield, TeamBattle }

object side {

  private val separator = " • "

  def apply(
    tour: Tournament,
    verdicts: lidraughts.tournament.Condition.All.WithVerdicts,
    streamers: List[lidraughts.user.User.ID],
    shieldOwner: Option[TournamentShield.OwnerId],
    chat: Boolean
  )(implicit ctx: Context) = frag(
    div(cls := "tour__meta")(
      st.section(dataIcon := tour.perfType.map(_.iconChar.toString))(
        div(
          p(
            tour.clock.show,
            separator,
            if (tour.variant.exotic) {
              views.html.game.bits.variantLink(
                tour.variant,
                tour.variant.name
              )
            } else tour.perfType.map(_.name),
            tour.isThematic ?? s"$separator ${trans.thematic.txt()}",
            separator,
            tour.durationString
          ),
          tour.mode.fold(trans.casualTournament, trans.ratedTournament)(),
          separator,
          systemName(tour.system).capitalize,
          (isGranted(_.ManageTournament) || (ctx.userId.has(tour.createdBy) && tour.isCreated)) option frag(
            " ",
            a(href := routes.Tournament.edit(tour.id), title := trans.editTournament.txt())(iconTag("%"))
          )
        )
      ),
      tour.teamBattle map teamBattle(tour),
      tour.spotlight map { s =>
        st.section(
          markdownLinksOrRichText(s.description),
          shieldOwner map { owner =>
            p(cls := "defender", dataIcon := "5")(
              "Defender:",
              userIdLink(owner.value.some)
            )
          }
        )
      },
      tour.description map { d =>
        st.section(cls := "description")(markdownLinksOrRichText(d))
      },
      verdicts.relevant option st.section(dataIcon := "7", cls := List(
        "conditions" -> true,
        "accepted" -> (ctx.isAuth && verdicts.accepted),
        "refused" -> (ctx.isAuth && !verdicts.accepted)
      ))(div(
        (verdicts.list.size < 2) option p(trans.conditionOfEntry()),
        verdicts.list map { v =>
          p(cls := List(
            "condition text" -> true,
            "accepted" -> v.verdict.accepted,
            "refused" -> !v.verdict.accepted
          ))(v.condition match {
            case lidraughts.tournament.Condition.TeamMember(teamId, teamName) =>
              trans.mustBeInTeam(teamLinkWithName(teamId, lidraughts.common.String.html.escapeHtml(teamName), withIcon = false))
            case c => c.name(ctx.lang)
          })
        }
      )),
      tour.spotlight.flatMap(_.drawLimit).map { lim =>
        div(cls := "text", dataIcon := "2")(
          if (lim > 0) trans.drawOffersAfterX(lim)
          else trans.drawOffersNotAllowed()
        )
      },
      tour.noBerserk option div(cls := "text", dataIcon := "`")(trans.arena.noBerserkAllowed()),
      tour.noStreak option div(cls := "text", dataIcon := "Q")(trans.arena.noArenaStreaks()),
      !tour.isScheduled option frag(trans.by(userIdLink(tour.createdBy.some, isWfd = tour.isWfd)), br),
      (!tour.isStarted || (tour.isScheduled && tour.isThematic)) option absClientDateTime(tour.startsAt),
      tour.isThematic option p(cls := "opening")(
        tour.openingTable.fold(openingLink(tour)) { table =>
          if (!tour.isThematicRandom) frag(
            a(target := "_blank", href := table.url)(table.name),
            br,
            openingLink(tour)
          )
          else trans.randomOpeningFromX(
            a(target := "_blank", href := table.url)(table.name)
          )
        }
      )
    ),
    streamers map views.html.streamer.bits.contextual,
    chat option views.html.chat.frag
  )

  private def openingPosition(position: draughts.StartingPosition) = frag(
    strong(position.code), " ", position.name
  )

  private def openingLink(tour: Tournament)(implicit ctx: Context) = frag(
    tour.position.url.fold(openingPosition(tour.position)) { url =>
      a(target := "_blank", href := url)(
        openingPosition(tour.position)
      )
    },
    separator,
    a(href := routes.UserAnalysis.parse(tour.variant.key + "/" + tour.position.fen.replace(" ", "_")))(trans.analysis())
  )

  private def teamBattle(tour: Tournament)(battle: TeamBattle)(implicit ctx: Context) =
    st.section(cls := "team-battle")(
      p(cls := "team-battle__title text", dataIcon := "f")(
        s"Battle of ${battle.teams.size} teams and ${battle.nbLeaders} leaders",
        (ctx.userId.has(tour.createdBy) || isGranted(_.ManageTournament)) option
          a(href := routes.Tournament.teamBattleEdit(tour.id), title := "Edit team battle")(iconTag("%"))
      )
    )
}
