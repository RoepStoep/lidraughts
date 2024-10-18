package lidraughts.practice

import scala.collection.breakOut
import draughts.variant.Variant
import lidraughts.practice.PracticeStructure.defaultLang
import lidraughts.study.{ Chapter, Study }

case class PracticeStructure(
    sections: List[PracticeSection],
    variant: Option[Variant]
) {

  def study(id: Study.Id): Option[PracticeStudy] =
    sections.flatMap(_ study id).headOption

  lazy val studiesByIds: Map[Study.Id, PracticeStudy] =
    sections.flatMap(_.studies).map { s =>
      s.id -> s
    }(breakOut)

  lazy val sectionsByStudyIds: Map[Study.Id, PracticeSection] =
    sections.flatMap { sec =>
      sec.studies.map { stu =>
        stu.id -> sec
      }
    }(breakOut)

  lazy val chapterIds: List[Chapter.Id] = sections.flatMap(_.studies).flatMap(_.chapterIds)

  lazy val nbChapters = chapterIds.size

  def findSection(id: Study.Id): Option[PracticeSection] = sectionsByStudyIds get id

  def hasStudy(id: Study.Id) = studiesByIds contains id

  def translatedSection(sectionId: String, lang: String): Option[PracticeSection] =
    sections.find(s => s.id == sectionId && s.lang == lang) match {
      case sec @ Some(_) => sec
      case _ => sections.find(s => s.id == sectionId && s.lang == defaultLang)
    }

  def translatedStudy(id: Study.Id, langOpt: Option[String]) = {
    val lang = langOpt | defaultLang
    findSection(id) flatMap { baseSection =>
      translatedSection(baseSection.id, lang) flatMap { transSection =>
        baseSection.studies zip transSection.studies flatMap {
          case (baseStudy, transStudy) if baseStudy.id == id || transStudy.id == id => transStudy.some
          case _ => none[PracticeStudy]
        } headOption
      }
    } match {
      case s @ Some(_) => s
      case _ => study(id)
    }
  }

  def withVariant(v: Variant) = PracticeStructure(
    sections = sections.filter(_.variant == v),
    variant = v.some
  )
}

case class PracticeSection(
    id: String,
    lang: String,
    variant: Variant,
    name: String,
    studies: List[PracticeStudy]
) {

  lazy val studiesByIds: Map[Study.Id, PracticeStudy] =
    studies.map { s =>
      s.id -> s
    }(breakOut)

  def study(id: Study.Id): Option[PracticeStudy] = studiesByIds get id

  def hasSlug(slug: String) = studies.exists(_.slug == slug)
}

case class PracticeStudy(
    id: Study.Id, // study ID
    name: String,
    desc: String,
    chapters: List[Chapter.IdName]
) {

  val slug = lidraughts.common.String slugify name

  def chapterIds = chapters.map(_.id)
}

object PracticeStructure {

  val defaultLang = lidraughts.i18n.defaultLang.code

  def isChapterNameCommented(name: Chapter.Name) = name.value.startsWith("//")

  private def isBetaSection(sec: PracticeConfigSection) = sec.id.toLowerCase.startsWith("beta-")

  def make(conf: PracticeConfig, chapters: Map[Study.Id, Vector[Chapter.IdName]], langOpt: Option[String], beta: Boolean = false) = {
    val sections = langOpt.fold(conf.sections)(_ => conf.sections.filter(_.lang.isEmpty))
    val lang = langOpt.filterNot(defaultLang ==)
    val sectionsBeta = if (beta) sections else sections.filterNot(isBetaSection)
    PracticeStructure(
      sections = sectionsBeta.map { defaultSec =>
        val sec = lang.flatMap(conf.translatedSection(defaultSec.id, _)) | defaultSec
        PracticeSection(
          id = sec.id,
          lang = sec.lang | defaultLang,
          variant = sec.getVariant,
          name = if (isBetaSection(sec)) s"${sec.name} [BETA]" else sec.name,
          studies = sec.studies.map { stu =>
            val id = Study.Id(stu.id)
            PracticeStudy(
              id = id,
              name = stu.name,
              desc = stu.desc,
              chapters = chapters.get(id).??(_.filterNot { c =>
                isChapterNameCommented(c.name)
              }.toList)
            )
          }
        )
      },
      variant = None
    )
  }

  def chaptersToLang(conf: PracticeConfig, chapters: Map[Study.Id, Vector[Chapter.IdName]], lang: String): Option[Map[Chapter.Id, Chapter.Id]] = {
    val chapterMap: Map[Chapter.Id, Chapter.Id] = conf.sections.filter(_.lang.isEmpty).flatMap { baseSection =>
      conf.translatedSection(baseSection.id, lang).fold(List.empty[(Chapter.Id, Chapter.Id)]) { transSection =>
        baseSection.studies zip transSection.studies flatMap {
          case (baseStudy, transStudy) =>
            ~chapters.get(Study.Id(baseStudy.id)) zip ~chapters.get(Study.Id(transStudy.id)) map {
              case (baseChapter, transChapter) => baseChapter.id -> transChapter.id
            }
        }
      }
    }(breakOut)
    if (chapterMap.isEmpty) none else chapterMap.some
  }

  def chaptersFromLangs(conf: PracticeConfig, chapters: Map[Study.Id, Vector[Chapter.IdName]]): Map[Chapter.Id, Chapter.Id] =
    conf.sections.filter(_.lang.nonEmpty).flatMap { transSection =>
      conf.sections.find(s => s.id == transSection.id && s.lang.isEmpty).fold(List.empty[(Chapter.Id, Chapter.Id)]) { baseSection =>
        baseSection.studies zip transSection.studies flatMap {
          case (baseStudy, transStudy) =>
            ~chapters.get(Study.Id(baseStudy.id)) zip ~chapters.get(Study.Id(transStudy.id)) map {
              case (baseChapter, transChapter) => transChapter.id -> baseChapter.id
            }
        }
      }
    }(breakOut)
}
