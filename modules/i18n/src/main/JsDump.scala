package lidraughts.i18n

import java.io._
import scala.concurrent.Future
import scala.collection.JavaConversions._
import play.api.libs.json.{ JsString, JsObject }

import lidraughts.common.Lang

private[i18n] final class JsDump(path: String) {

  def apply: Funit = Future {
    pathFile.mkdir
    writeRefs
    writeFullJson
  } void

  private val pathFile = new File(path)

  private def dumpFromKey(keys: Set[String], dedupKeys: Set[String], lang: Lang, ref: I18nDb.Ref): Set[String] =
    keys.filterNot(dedupKeys.apply).map { key =>
      """"%s":"%s"""".format(key, escape(Translator.txt.literal(key, ref, Nil, lang)))
    }

  private def writeRefs = writeFile(
    new File("%s/refs.json".format(pathFile.getCanonicalPath)),
    LangList.all.toList.sortBy(_._1.code).map {
      case (lang, name) => s"""["${lang.code}","$name"]"""
    }.mkString("[", ",", "]")
  )

  private def writeFullJson = I18nDb.langs foreach { lang =>
    val dbs = List(I18nDb.Site, I18nDb.Arena, I18nDb.Study)
    val (keyValues, _) = dbs.foldLeft(Set.empty[String] -> Set.empty[String]) { (sets, ref) =>
      val newKeys = asScalaSet(I18nDb(ref)(defaultLang.value).keySet).toSet
      (sets._1 ++ dumpFromKey(newKeys, sets._2, lang, ref), sets._2 ++ newKeys)
    }
    val code = keyValues.mkString("{", ",", "}")
    val file = new File("%s/%s.all.json".format(pathFile.getCanonicalPath, lang.code))
    writeFile(file, code)
  }

  private def writeFile(file: File, content: String) = {
    val out = new PrintWriter(file)
    try { out.print(content) }
    finally { out.close }
  }

  private def escape(text: String) = text.replaceIf('"', "\\\"").replaceIf("\r\n", "\\n").replaceIf('\n', "\\n")
}

object JsDump {

  private def quantitySuffix(q: I18nQuantity): String = q match {
    case I18nQuantity.Zero => ":zero"
    case I18nQuantity.One => ":one"
    case I18nQuantity.Two => ":two"
    case I18nQuantity.Few => ":few"
    case I18nQuantity.Many => ":many"
    case I18nQuantity.Other => ""
  }

  private type JsTrans = Iterable[(String, JsString)]

  private def translatedJs(k: String, t: Translation, lang: Lang): JsTrans = t match {
    case literal: Simple => List(k -> JsString(literal.message))
    case literal: Escaped => List(k -> JsString(literal.message))
    case plurals: Plurals => plurals.messages.map {
      case (quantity, msg) => s"$k${quantitySuffix(quantity)}" -> JsString(msg)
    }
  }

  def keysToObject(keys: Seq[Translated], lang: Lang): JsObject = JsObject {
    keys.flatMap { k =>
      Translator.findTranslation(k.key, k.db, lang).fold[JsTrans](Nil) { translatedJs(k.key, _, lang) }
    }
  }

  val emptyMessages: MessageMap = new java.util.HashMap()

  def dbToObject(ref: I18nDb.Ref, lang: Lang): JsObject =
    I18nDb(ref).get(defaultLang.value) ?? { defaultMsgs =>
      JsObject {
        val msgs = I18nDb(ref).get(lang.value) | emptyMessages
        defaultMsgs.flatMap {
          case (k, v) => translatedJs(k, msgs.getOrDefault(k, v), lang)
        }
      }
    }
}
