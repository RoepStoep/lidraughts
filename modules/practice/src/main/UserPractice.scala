package lidraughts.practice

import draughts.variant.Variant
import lidraughts.study.{ Study, Chapter }

case class UserPractice(
    structure: PracticeStructure,
    progress: PracticeProgress
) {

  def progressOn(studyId: Study.Id) = {
    val chapterIds = structure.study(studyId).??(_.chapterIds)
    Completion(
      done = progress countDone chapterIds,
      total = chapterIds.size
    )
  }

  lazy val nbDoneChapters = structure.chapterIds count progress.chapters.contains

  lazy val progressPercent = if (structure.nbChapters != 0) nbDoneChapters * 100 / structure.nbChapters else 0
}

case class UserStudy(
    practice: UserPractice,
    practiceStudy: PracticeStudy,
    chapters: List[Chapter.Metadata],
    study: Study.WithChapter,
    section: PracticeSection
) {

  def url = s"/practice/${section.id}/${practiceStudy.slug}/${study.study.id}"
}

case class Completion(done: Int, total: Int) {

  def percent = if (total == 0) 0 else done * 100 / total

  def complete = done >= total
}
