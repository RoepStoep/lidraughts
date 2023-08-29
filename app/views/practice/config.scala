package views.html.practice

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object config {

  def apply(structure: lidraughts.practice.PracticeStructure, form: Form[_])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Practice structure",
      moreCss = cssTag("mod.misc")
    )(
        main(cls := "page-menu")(
          views.html.mod.menu("practice"),
          div(cls := "practice_config page-menu__content box box-pad")(
            h1("Practice config"),
            div(cls := "both")(
              postForm(action := routes.Practice.configSave)(
                textarea(cls := "practice_text", name := "text")(form("text").value),
                errMsg(form("text")),
                submitButton(cls := "button button-fat text", dataIcon := "E")(trans.save())
              ),
              div(cls := "preview")(
                ol(
                  structure.sections.map { section =>
                    val lang = (section.lang != lidraughts.practice.PracticeStructure.defaultLang) ?? { s" ${section.lang}" }
                    li(
                      h2(s"${section.name} (#${section.id}$lang)"),
                      ol(
                        section.studies.map { stud =>
                          li(
                            i(cls := s"practice icon ${stud.id}")(
                              h3(a(href := routes.Study.show(stud.id.value))(s"${stud.name} (#${stud.id})")),
                              em(stud.desc),
                              ol(
                                stud.chapters.map { cha =>
                                  li(a(href := routes.Study.chapter(stud.id.value, cha.id.value))(cha.name))
                                }
                              )
                            )
                          )
                        }
                      )
                    )
                  }
                )
              )
            )
          )
        )
      )
}
