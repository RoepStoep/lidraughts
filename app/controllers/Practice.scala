package controllers

import play.api.libs.json._
import play.api.mvc._

import draughts.variant.Variant
import lidraughts.api.Context
import lidraughts.app._
import lidraughts.practice.JsonView._
import lidraughts.practice.{ UserStudy, PracticeSection, PracticeStudy }
import lidraughts.pref.Pref.practiceVariants
import lidraughts.study.Study.WithChapter
import lidraughts.study.{ Chapter, Study => StudyModel }
import lidraughts.tree.Node.partitionTreeJsonWriter
import views._

object Practice extends LidraughtsController {

  private def env = Env.practice
  private def studyEnv = Env.study

  def index = Open { implicit ctx =>
    pageHit
    renderIndex(ctx.pref.practiceVariant, none)
  }

  def indexDfs = Open { implicit ctx =>
    lidraughts.mon.dfs.interland.pagePractice()
    Redirect(
      routes.Practice.showSectionOrVariant(draughts.variant.Frisian.key)
    ).withCookies(
        lidraughts.common.DfsInterlandCookie.cookie()
      ).fuccess
  }

  private def indexVariant(key: String) = Open { implicit ctx =>
    Variant(key) match {
      case Some(variant) if practiceVariants.contains(variant) =>
        if (ctx.pref.practiceVariant != variant)
          controllers.Pref.save("practiceVariant")(variant.key, ctx) flatMap {
            cookie => renderIndex(variant, cookie.some)
          }
        else renderIndex(variant, none)
      case _ => notFound
    }
  }

  def renderIndex(variant: Variant, cookie: Option[Cookie])(implicit ctx: Context) =
    env.api.get(ctx.me, variant.some, ctx.lang) map { html.practice.index(_) } map { h =>
      cookie.fold(Ok(h))(c => Ok(h).withCookies(c))
    } map NoCache

  def show(sectionId: String, studySlug: String, studyId: String) = Open { implicit ctx =>
    pageHit
    OptionFuResult(env.api.getStudyWithFirstOngoingChapter(ctx.me, ctx.lang, studyId))(showUserPractice)
  }

  def showChapter(sectionId: String, studySlug: String, studyId: String, chapterId: String) = Open { implicit ctx =>
    pageHit
    OptionFuResult(env.api.getStudyWithChapter(ctx.me, ctx.lang, studyId, chapterId))(showUserPractice)
  }

  def showSectionOrVariant(something: String) = Variant(something) match {
    case Some(variant) => indexVariant(variant.key)
    case _ => showSection(something)
  }

  def showSection(sectionId: String) =
    redirectTo(sectionId, none)(_.studies.headOption)

  def showStudySlug(sectionId: String, studySlug: String) =
    redirectTo(sectionId, studySlug.some)(_.studies.find(_.slug == studySlug))

  private def redirectTo(sectionId: String, withSlug: Option[String])(select: PracticeSection => Option[PracticeStudy]) = Open { implicit ctx =>
    env.api.structure.getAll.flatMap { struct =>
      struct.sections.find(sec => sec.id == sectionId && withSlug.fold(true)(sec.hasSlug)).fold(notFound) { section =>
        select(section) ?? { study =>
          Redirect(routes.Practice.show(section.id, study.slug, study.id.value)).fuccess
        }
      }
    }
  }

  private def showUserPractice(us: lidraughts.practice.UserStudy)(implicit ctx: Context) = analysisJson(us) map {
    case (analysisJson, studyJson) => NoCache(Ok(
      html.practice.show(us, lidraughts.practice.JsonView.JsData(
        study = studyJson,
        analysis = analysisJson,
        practice = lidraughts.practice.JsonView(us)
      ))
    ))
  }

  def chapter(studyId: String, chapterId: String) = Open { implicit ctx =>
    OptionFuResult(env.api.getStudyWithChapter(ctx.me, ctx.lang, studyId, chapterId)) { us =>
      analysisJson(us) map {
        case (analysisJson, studyJson) => Ok(Json.obj(
          "study" -> studyJson,
          "analysis" -> analysisJson
        )) as JSON
      }
    } map NoCache
  }

  private def analysisJson(us: UserStudy)(implicit ctx: Context): Fu[(JsObject, JsObject)] = us match {
    case UserStudy(_, _, chapters, WithChapter(study, chapter), _) =>
      studyEnv.jsonView(study, chapters, chapter, ctx.me) map { studyJson =>
        val initialFen = chapter.root.fen.some
        val pov = UserAnalysis.makePov(initialFen, chapter.setup.variant)
        val baseData = Env.round.jsonView.userAnalysisJson(pov, ctx.pref, initialFen, chapter.setup.orientation, owner = false, me = ctx.me)
        val analysis = baseData ++ Json.obj(
          "treeParts" -> partitionTreeJsonWriter.writes {
            lidraughts.study.TreeBuilder(chapter.root, chapter.setup.variant)
          },
          "practiceGoal" -> lidraughts.practice.PracticeGoal(chapter)
        )
        (analysis, studyJson)
      }
  }

  def complete(chapterId: String, nbMoves: Int) = Auth { implicit ctx => me =>
    env.api.progress.setNbMoves(me, chapterId, lidraughts.practice.PracticeProgress.NbMoves(nbMoves))
  }

  def reset = AuthBody { implicit ctx => me =>
    Variant(~get("v")) match {
      case Some(v) if practiceVariants.contains(v) =>
        env.api.progress.reset(me, v.some) inject Redirect(routes.Practice.index)
      case _ => notFound
    }
  }

  def config = Secure(_.PracticeConfig) { implicit ctx => me =>
    for {
      struct <- env.api.structure.getAll
      form <- env.api.config.form
    } yield Ok(html.practice.config(struct, form))
  }

  def configSave = SecureBody(_.PracticeConfig) { implicit ctx => me =>
    implicit val req = ctx.body
    env.api.config.form.flatMap { form =>
      FormFuResult(form) { err =>
        env.api.structure.getAll map { html.practice.config(_, err) }
      } { text =>
        ~env.api.config.set(text).right.toOption >>-
          env.api.structure.clear >>
          Env.mod.logApi.practiceConfig(me.id) inject Redirect(routes.Practice.config)
      }
    }
  }

  private implicit def makeStudyId(id: String): StudyModel.Id = StudyModel.Id(id)
  private implicit def makeChapterId(id: String): Chapter.Id = Chapter.Id(id)
}
