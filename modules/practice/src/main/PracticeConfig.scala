package lidraughts.practice

import draughts.variant.{ Standard, Variant }
import lidraughts.study.Study

case class PracticeConfig(
    sections: List[PracticeConfigSection]
) {

  def studyIds = sections.flatMap(_.studies.map(_.id)) map Study.Id.apply

  def translatedSection(id: String, lang: String) = sections.find(s => s.id == id && s.lang.contains(lang))
}

object PracticeConfig {

  val empty = PracticeConfig(Nil)

  def validate(config: PracticeConfig) = {
    val baseErrors = config.sections.flatMap { sec =>
      if (Variant(sec.id).isDefined)
        Some(s"Cannot use variant key ${sec.id} as section-id")
      else if (config.sections.exists(s => s.id == sec.id && s.variant != sec.variant))
        Some(s"Cannot use section-id ${sec.id} for different variants")
      else if (config.sections.count(s => s.id == sec.id && s.lang == sec.lang) > 1)
        Some(s"Duplicate section-id ${sec.id}")
      else None
    }
    val transErrors = config.sections.filter(_.lang.isDefined) flatMap { secTrans =>
      config.sections.find(s => s.id == secTrans.id && s.lang.isEmpty) match {
        case Some(secBase) =>
          if (config.sections.indexOf(secTrans) < config.sections.indexOf(secBase))
            Some(s"Expected untranslated section ${secBase.id} before translation ${secTrans.lang.get}")
          else if (!secBase.maybeVariant.exists(lidraughts.pref.Pref.practiceVariants.contains(_)))
            Some(s"Invalid variant in ${secBase.id}")
          else if (!secTrans.maybeVariant.exists(lidraughts.pref.Pref.practiceVariants.contains(_)))
            Some(s"Invalid variant in ${secTrans.id} ${secTrans.lang.get}")
          else if (secBase.variant != secTrans.variant)
            Some(s"Expected variant ${secBase.variant} in ${secTrans.id} ${secTrans.lang.get}")
          else if (secBase.studies.size != secTrans.studies.size)
            Some(s"Expected ${secBase.studies.size} studies in ${secTrans.id} ${secTrans.lang.get}")
          else None
        case _ => Some(s"No untranslated section for ${secTrans.id} ${secTrans.lang.get}")
      }
    }
    baseErrors ::: transErrors
  }
}

case class PracticeConfigSection(
    id: String,
    lang: Option[String],
    variant: Option[String],
    name: String,
    studies: List[PracticeConfigStudy]
) {

  def maybeVariant = variant match {
    case Some(v) => Variant(v)
    case _ => Standard.some
  }

  def getVariant = variant.flatMap(Variant.apply) | Standard
}

case class PracticeConfigStudy(
    id: String, // study ID
    name: String,
    desc: String
)
