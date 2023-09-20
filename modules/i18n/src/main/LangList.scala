package lidraughts.i18n

import lidraughts.common.Lang

object LangList {

  def name(lang: Lang): String = all.getOrElse(lang, lang.code)

  def nameByStr(str: String): String = I18nLangPicker.byStr(str).fold(str)(name)

  lazy val choices: List[(String, String)] = all.toList.map {
    case (l, name) => l.language -> name
  }.sortBy(_._1)

  private[i18n] val all = Map(
    Lang("en", "GB") -> "English",
    Lang("be", "BY") -> "Беларуская",
    Lang("cs", "CZ") -> "čeština",
    Lang("cy", "GB") -> "Cymraeg",
    Lang("de", "DE") -> "Deutsch",
    Lang("el", "GR") -> "Ελληνικά",
    Lang("en", "US") -> "English (US)",
    Lang("es", "ES") -> "español, castellano",
    Lang("fr", "FR") -> "français",
    Lang("fy", "NL") -> "Frysk",
    Lang("he", "IL") -> "עִבְרִית",
    Lang("it", "IT") -> "Italiano",
    Lang("ja", "JP") -> "日本語",
    Lang("lt", "LT") -> "lietuvių kalba",
    Lang("lv", "LV") -> "latviešu valoda",
    Lang("mn", "MN") -> "монгол",
    Lang("nl", "NL") -> "Nederlands",
    Lang("pl", "PL") -> "polski",
    Lang("pt", "PT") -> "Português",
    Lang("pt", "BR") -> "Português (BR)",
    Lang("ru", "RU") -> "русский язык",
    Lang("tr", "TR") -> "Türkçe",
    Lang("uk", "UA") -> "українська",
    Lang("vi", "VN") -> "Tiếng Việt",
    Lang("zh", "CN") -> "中文"
  )
}
