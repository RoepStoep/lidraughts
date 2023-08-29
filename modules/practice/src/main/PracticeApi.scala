package lidraughts.practice

import scala.concurrent.duration._

import draughts.variant.Variant
import lidraughts.db.dsl._
import lidraughts.study.{ Chapter, Study }
import lidraughts.user.User

final class PracticeApi(
    coll: Coll,
    configStore: lidraughts.memo.ConfigStore[PracticeConfig],
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    studyApi: lidraughts.study.StudyApi,
    bus: lidraughts.common.Bus
) {

  import BSONHandlers._

  def get(user: Option[User], variant: Option[Variant]): Fu[UserPractice] = for {
    struct <- structure.getLang(user.flatMap(_.lang)).map(s => variant.fold(s)(s.withVariant))
    prog <- user.fold(fuccess(PracticeProgress.anon))(progress.getTranslated)
  } yield UserPractice(struct, prog)

  private def getAll(user: Option[User]): Fu[UserPractice] = for {
    struct <- structure.getAll
    prog <- user.fold(fuccess(PracticeProgress.anon))(progress.getTranslated)
  } yield UserPractice(struct, prog)

  def getStudyWithFirstOngoingChapter(user: Option[User], tryStudyId: Study.Id): Fu[Option[UserStudy]] = for {
    up <- getAll(user)
    studyId = up.structure.translatedStudy(tryStudyId, user.flatMap(_.lang)).fold(tryStudyId)(_.id)
    chapters <- studyApi.chapterMetadatas(studyId)
    chapter = up.progress firstOngoingIn chapters
    studyOption <- chapter.fold(studyApi byIdWithFirstChapter studyId) { chapter =>
      studyApi.byIdWithChapter(studyId, chapter.id)
    }
  } yield makeUserStudy(studyOption, up, chapters)

  def getStudyWithChapter(user: Option[User], tryStudyId: Study.Id, tryChapterId: Chapter.Id): Fu[Option[UserStudy]] = for {
    up <- getAll(user)
    lang = user.flatMap(_.lang)
    studyId = up.structure.translatedStudy(tryStudyId, lang).fold(tryStudyId)(_.id)
    chapters <- studyApi.chapterMetadatas(studyId)
    baseChapterId <- structure.getChaptersFromLangs.map(_.getOrElse(tryChapterId, tryChapterId))
    chapterId <- structure.getChaptersToLang(lang).map(_.fold(baseChapterId)(_.getOrElse(baseChapterId, baseChapterId)))
    studyOption <- studyApi.byIdWithChapter(studyId, chapterId)
  } yield makeUserStudy(studyOption, up, chapters)

  private def makeUserStudy(studyOption: Option[Study.WithChapter], up: UserPractice, chapters: List[Chapter.Metadata]) = for {
    rawSc <- studyOption
    sc = rawSc.copy(
      study = rawSc.study.rewindTo(rawSc.chapter).withoutMembers,
      chapter = rawSc.chapter.withoutChildrenIfPractice
    )
    practiceStudy <- up.structure study sc.study.id
    section <- up.structure findSection sc.study.id
    publishedChapters = chapters.filterNot { c =>
      PracticeStructure isChapterNameCommented c.name
    }
    if publishedChapters.exists(_.id == sc.chapter.id)
  } yield UserStudy(up, practiceStudy, publishedChapters, sc, section)

  object config {
    def get = configStore.get map (_ | PracticeConfig.empty)
    def set = configStore.set _
    def form = configStore.makeForm(Some(PracticeConfig.validate))
  }

  object structure {

    private val cacheAll = asyncCache.single[PracticeStructure](
      "practice.structure.all",
      f = for {
        conf <- config.get
        chapters <- studyApi.chapterIdNames(conf.studyIds)
      } yield PracticeStructure.make(conf, chapters, none),
      expireAfter = _.ExpireAfterAccess(3.hours)
    )

    private val cacheLang = asyncCache.clearable[String, PracticeStructure](
      "practice.structure.lang",
      f = lang => for {
        conf <- config.get
        chapters <- studyApi.chapterIdNames(conf.studyIds)
      } yield PracticeStructure.make(conf, chapters, lang.some),
      expireAfter = _.ExpireAfterAccess(3.hours)
    )

    def getAll = cacheAll.get
    def getLang(lang: Option[String]) = cacheLang.get(lang.getOrElse(PracticeStructure.defaultLang))

    private val chaptersFromLangs = asyncCache.single[Map[Chapter.Id, Chapter.Id]](
      "practice.structure.chapters.fromLangs",
      f = for {
        conf <- config.get
        chapters <- studyApi.chapterIdNames(conf.studyIds)
      } yield PracticeStructure.chaptersFromLangs(conf, chapters),
      expireAfter = _.ExpireAfterAccess(3.hours)
    )

    private val chaptersToLang = asyncCache.clearable[String, Option[Map[Chapter.Id, Chapter.Id]]](
      "practice.structure.chapters.toLang",
      f = lang => for {
        conf <- config.get
        chapters <- studyApi.chapterIdNames(conf.studyIds)
      } yield PracticeStructure.chaptersToLang(conf, chapters, lang),
      expireAfter = _.ExpireAfterAccess(3.hours)
    )

    def getChaptersFromLangs = chaptersFromLangs.get
    def getChaptersToLang(lang: Option[String]) = lang ?? chaptersToLang.get

    def clear = {
      cacheAll.refresh
      cacheLang.invalidateAll
      chaptersFromLangs.refresh
      chaptersToLang.invalidateAll
    }

    def onSave(study: Study) = getAll foreach { structure =>
      if (structure.hasStudy(study.id)) clear
    }
  }

  object progress {

    import PracticeProgress.NbMoves

    def getTranslated(user: User): Fu[PracticeProgress] =
      getRaw(user) flatMap { prog =>
        structure.getChaptersToLang(user.lang).map {
          _.fold(prog) { chaptersToLang =>
            prog.mapChapters(chaptersToLang.get)
          }
        }
      }

    private def getRaw(user: User): Fu[PracticeProgress] =
      coll.uno[PracticeProgress]($id(user.id)) map {
        _ | PracticeProgress.empty(PracticeProgress.Id(user.id))
      }

    private def save(p: PracticeProgress): Funit =
      coll.update($id(p.id), p, upsert = true).void

    def setNbMoves(user: User, fromChapterId: Chapter.Id, score: NbMoves) =
      structure.getChaptersFromLangs.map { _.getOrElse(fromChapterId, fromChapterId) } flatMap { chapterId =>
        {
          getRaw(user) flatMap { prog =>
            save(prog.withNbMoves(chapterId, score))
          }
        } >>- studyApi.studyIdOf(chapterId).foreach {
          _ ?? { studyId =>
            bus.publish(PracticeProgress.OnComplete(user.id, studyId, chapterId), 'finishPractice)
          }
        }
      }

    def reset(user: User, variant: Option[Variant]) =
      variant match {
        case Some(v) => for {
          prog <- getRaw(user)
          struct <- structure.getAll
          studies = struct.sections.filter(s => s.variant == v && s.lang == PracticeStructure.defaultLang).flatMap(_.studies)
        } yield save(prog.clearChapters(studies.flatMap(_.chapterIds)))
        case _ => coll.remove($id(user.id)).void
      }
  }
}
