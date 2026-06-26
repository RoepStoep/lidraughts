package controllers

import scala.concurrent.duration._

import io.prismic.Fragment.DocumentLink
import io.prismic.{ Api => PrismicApi, _ }
import draughts.variant.Variant
import lidraughts.app._
import lidraughts.common.Lang

object Prismic {

  private type DocWithResolver = (Document, AnyRef with DocumentLinkResolver)

  private val logger = lidraughts.log("prismic")

  val prismicLogger = (level: Symbol, message: String) => level match {
    case 'DEBUG => logger debug message
    case 'ERROR => logger error message
    case _ => logger info message
  }

  private val prismicApiCache = Env.memo.asyncCache.single[PrismicApi](
    name = "prismic.fetchPrismicApi",
    f = PrismicApi.get(Env.api.PrismicApiUrl, logger = prismicLogger),
    expireAfter = _.ExpireAfterWrite(1 minute)
  )

  private val variantLanguageCache = Env.memo.asyncCache.clearable[Variant, Option[List[DocWithResolver]]](
    name = "prismic.variantLanguageCache",
    f = fetchVariantLanguages,
    expireAfter = _.ExpireAfterWrite(10 minutes)
  )

  private def fetchVariantLanguages(variant: Variant) = prismicApi flatMap { api =>
    api.forms("everything")
      .query(s"""[[:d = at(document.type, "variant")][:d = at(my.variant.key, "${variant.key}")]]""")
      .set("lang", "*")
      .ref(api.master.ref)
      .submit() map {
        _.results.map(_ -> makeLinkResolver(api)).some
      }
  } recover {
    case e: Exception =>
      logger.error(s"variant:${variant.key}", e)
      lidraughts.mon.http.prismic.timeout()
      none
  }

  private def invalidateVariantLanguages(variant: Variant) = {
    variantLanguageCache.invalidate(variant)
    none[DocWithResolver]
  }

  def prismicApi = prismicApiCache.get

  implicit def makeLinkResolver(prismicApi: PrismicApi, ref: Option[String] = None) =
    DocumentLinkResolver(prismicApi) {
      case (link, _) => routes.Blog.show(link.id, link.slug, ref).url
      case _ => routes.Lobby.home.url
    }

  def getDocument(id: String): Fu[Option[Document]] = prismicApi flatMap { api =>
    api.forms("everything")
      .query(s"""[[:d = at(document.id, "$id")]]""")
      .ref(api.master.ref)
      .submit() map {
        _.results.headOption
      }
  }

  // Bookmarks were removed from the Prismic API; UIDs on the "doc" custom type replace them.
  // https://community.prismic.io/t/faq-deprecation-of-bookmarks-and-collections-in-prismic-api/18154
  def getBookmark(uid: String) = prismicApi flatMap { api =>
    api.forms("everything")
      .query(s"""[[:d = at(document.type, "doc")][:d = at(my.doc.uid, "$uid")]]""")
      .ref(api.master.ref)
      .submit() map {
        _.results.headOption map (_ -> makeLinkResolver(api))
      }
  } recover {
    case e: Exception =>
      logger.error(s"doc:$uid", e)
      lidraughts.mon.http.prismic.timeout()
      none
  }

  def getVariant(variant: Variant, lang: Lang) = variantLanguageCache get variant map {
    _.fold(invalidateVariantLanguages(variant)) { docs =>
      def findLang(l: String) = docs.find(doc => ~doc._1.getText("variant.lang").map(_.startsWith(l)))
      findLang(lang.language) orElse findLang("en")
    }
  }
}
