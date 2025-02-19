package controllers

import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

import lidraughts.api.Context
import lidraughts.app._
import lidraughts.chat.Chat
import lidraughts.common.paginator.{ Paginator, PaginatorJson }
import lidraughts.common.{ IpAddress, HTTPRequest }
import lidraughts.study.JsonView.JsData
import lidraughts.study.Study.WithChapter
import lidraughts.study.{ Chapter, Order, Study => StudyModel }
import lidraughts.tree.Node.partitionTreeFullUciJsonWriter
import views._

object Study extends LidraughtsController {

  type ListUrl = String => Call

  private def env = Env.study

  private val logger = lidraughts.log("study")

  def search(text: String, page: Int) = OpenBody { implicit ctx =>
    Reasonable(page) {
      if (text.trim.isEmpty)
        env.pager.all(ctx.me, Order.default, page) flatMap { pag =>
          negotiate(
            html = Ok(html.study.list.all(pag, Order.default)).fuccess,
            api = _ => apiStudies(pag)
          )
        }
      else Env.studySearch(ctx.me)(text, page) flatMap { pag =>
        negotiate(
          html = Ok(html.study.list.search(pag, text)).fuccess,
          api = _ => apiStudies(pag)
        )
      }
    }
  }

  def allDefault(page: Int) = all(Order.default.key, page)

  def all(o: String, page: Int) = Open { implicit ctx =>
    Reasonable(page) {
      Order(o) match {
        case Order.Oldest => Redirect(routes.Study.allDefault(page)).fuccess
        case order =>
          env.pager.all(ctx.me, order, page) flatMap { pag =>
            negotiate(
              html = Ok(html.study.list.all(pag, order)).fuccess,
              api = _ => apiStudies(pag)
            )
          }
      }
    }
  }

  def byOwnerDefault(username: String, page: Int) = byOwner(username, Order.default.key, page)

  def byOwner(username: String, order: String, page: Int) = Open { implicit ctx =>
    lidraughts.user.UserRepo.named(username).flatMap {
      _.fold(notFound(ctx)) { owner =>
        env.pager.byOwner(owner, ctx.me, Order(order), page) flatMap { pag =>
          negotiate(
            html = Ok(html.study.list.byOwner(pag, Order(order), owner)).fuccess,
            api = _ => apiStudies(pag)
          )
        }
      }
    }
  }

  def mine(order: String, page: Int) = Auth { implicit ctx => me =>
    env.pager.mine(me, Order(order), page) flatMap { pag =>
      negotiate(
        html = Ok(html.study.list.mine(pag, Order(order), me)).fuccess,
        api = _ => apiStudies(pag)
      )
    }
  }

  def minePublic(order: String, page: Int) = Auth { implicit ctx => me =>
    env.pager.minePublic(me, Order(order), page) flatMap { pag =>
      negotiate(
        html = Ok(html.study.list.minePublic(pag, Order(order), me)).fuccess,
        api = _ => apiStudies(pag)
      )
    }
  }

  def minePrivate(order: String, page: Int) = Auth { implicit ctx => me =>
    env.pager.minePrivate(me, Order(order), page) flatMap { pag =>
      negotiate(
        html = Ok(html.study.list.minePrivate(pag, Order(order), me)).fuccess,
        api = _ => apiStudies(pag)
      )
    }
  }

  def mineMember(order: String, page: Int) = Auth { implicit ctx => me =>
    env.pager.mineMember(me, Order(order), page) flatMap { pag =>
      negotiate(
        html = Ok(html.study.list.mineMember(pag, Order(order), me)).fuccess,
        api = _ => apiStudies(pag)
      )
    }
  }

  def mineLikes(order: String, page: Int) = Auth { implicit ctx => me =>
    env.pager.mineLikes(me, Order(order), page) flatMap { pag =>
      negotiate(
        html = Ok(html.study.list.mineLikes(pag, Order(order), me)).fuccess,
        api = _ => apiStudies(pag)
      )
    }
  }

  private def apiStudies(pager: Paginator[StudyModel.WithChaptersAndLiked]) = {
    implicit val pagerWriter = Writes[StudyModel.WithChaptersAndLiked] { s =>
      Env.study.jsonView.pagerData(s)
    }
    Ok(Json.obj(
      "paginator" -> PaginatorJson(pager)
    )).fuccess
  }

  private def orRelay(id: String, chapterId: Option[String] = None)(f: => Fu[Result])(implicit ctx: Context): Fu[Result] =
    if (HTTPRequest isRedirectable ctx.req) Env.relay.api.getOngoing(lidraughts.relay.Relay.Id(id)) flatMap {
      _.fold(f) { relay =>
        fuccess(Redirect {
          chapterId.fold(routes.Relay.show(relay.slug, relay.id.value)) { c =>
            routes.Relay.chapter(relay.slug, relay.id.value, c)
          }
        })
      }
    }
    else f

  private def showQuery(query: Fu[Option[WithChapter]])(implicit ctx: Context): Fu[Result] =
    OptionFuResult(query) { oldSc =>
      CanViewResult(oldSc.study) {
        for {
          (sc, data) <- getJsonData(oldSc)
          res <- negotiate(
            html = for {
              chat <- chatOf(sc.study)
              sVersion <- env.version(sc.study.id)
              streams <- streamsOf(sc.study)
            } yield Ok(html.study.show(sc.study, data, chat, sVersion, streams)),
            api = _ => chatOf(sc.study).map { chatOpt =>
              Ok(
                Json.obj(
                  "study" -> data.study.add("chat" -> chatOpt.map { c =>
                    lidraughts.chat.JsonView.mobile(
                      chat = c.chat,
                      writeable = ctx.userId.??(sc.study.canChat)
                    )
                  }),
                  "analysis" -> data.analysis
                )
              )
            }
          )
        } yield res
      }
    } map NoCache

  private[controllers] def getJsonData(sc: WithChapter)(implicit ctx: Context): Fu[(WithChapter, JsData)] = for {
    chapters <- Env.study.chapterRepo.orderedMetadataByStudy(sc.study.id)
    (study, resetToChapter) <- Env.study.api.resetIfOld(sc.study, chapters)
    chapter = resetToChapter | sc.chapter
    _ <- Env.user.lightUserApi preloadMany study.members.ids.toList
    _ = if (HTTPRequest isSynchronousHttp ctx.req) Env.study.studyRepo.incViews(study)
    pov = UserAnalysis.makePov(chapter.root.fen.some, chapter.setup.variant)
    analysis <- chapter.serverEval.exists(_.done) ?? Env.analyse.analyser.byId(chapter.id.value)
    division = analysis.isDefined option env.serverEvalMerger.divisionOf(chapter)
    baseData = Env.round.jsonView.userAnalysisJson(pov, ctx.pref, chapter.root.fen.some, chapter.setup.orientation,
      owner = false,
      me = ctx.me,
      division = division)
    studyJson <- Env.study.jsonView(study, chapters, chapter, ctx.me)
  } yield WithChapter(study, chapter) -> JsData(
    study = studyJson,
    analysis = baseData.add(
      "treeParts" -> partitionTreeFullUciJsonWriter.writes {
        lidraughts.study.TreeBuilder(chapter.root, chapter.setup.variant)
      }.some
    ).add("analysis" -> analysis.map { lidraughts.study.ServerEval.toJson(chapter, _) })
  )

  def show(id: String) = Open { implicit ctx =>
    orRelay(id) {
      showQuery(env.api byIdWithChapter id)
    }
  }

  def chapter(id: String, chapterId: String) = Open { implicit ctx =>
    orRelay(id, chapterId.some) {
      showQuery(env.api.byIdWithChapter(id, chapterId))
    }
  }

  def chapterMeta(id: String, chapterId: String) = Open { implicit ctx =>
    env.chapterRepo.byId(chapterId).map {
      _.filter(_.studyId.value == id) ?? { chapter =>
        Ok(env.jsonView.chapterConfig(chapter))
      }
    }
  }

  private[controllers] def chatOf(study: lidraughts.study.Study)(implicit ctx: Context) = {
    !ctx.kid && // no public chats for kids
      ctx.me.fold(true) { // anon can see public chats
        Env.chat.panic.allowed
      }
  } ?? Env.chat.api.userChat.findMine(Chat.Id(study.id.value), ctx.me).map(some)

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    get("sri") ?? { uid =>
      env.api byId id flatMap {
        _.filter(canView) ?? { study =>
          env.socketHandler.join(
            studyId = id,
            uid = lidraughts.socket.Socket.Uid(uid),
            user = ctx.me,
            getSocketVersion,
            apiVersion
          ) map some
        }
      }
    }
  }

  def createAs = AuthBody { implicit ctx => me =>
    implicit val req = ctx.body
    lidraughts.study.DataForm.importGame.form.bindFromRequest.fold(
      err => Redirect(routes.Study.byOwnerDefault(me.username)).fuccess,
      data => for {
        owner <- env.studyRepo.recentByOwner(me.id, 50)
        contrib <- env.studyRepo.recentByContributor(me.id, 50)
        res <- if (owner.isEmpty && contrib.isEmpty) createStudy(data, me)
        else Ok(html.study.create(data, owner, contrib)).fuccess
      } yield res
    )
  }

  def create = AuthBody { implicit ctx => me =>
    implicit val req = ctx.body
    lidraughts.study.DataForm.importGame.form.bindFromRequest.fold(
      err => Redirect(routes.Study.byOwnerDefault(me.username)).fuccess,
      data => createStudy(data, me)
    )
  }

  private def createStudy(data: lidraughts.study.DataForm.importGame.Data, me: lidraughts.user.User)(implicit ctx: Context) =
    env.api.importGame(lidraughts.study.StudyMaker.ImportGame(data), me) flatMap {
      _.fold(notFound) { sc =>
        Redirect(routes.Study.show(sc.study.id.value)).fuccess
      }
    }

  def delete(id: String) = Auth { implicit ctx => me =>
    env.api.byIdAndOwner(id, me) flatMap {
      _ ?? env.api.delete
    } inject Redirect(routes.Study.mine("hot"))
  }

  def clearChat(id: String) = Auth { implicit ctx => me =>
    env.api.isOwner(id, me) flatMap {
      _ ?? Env.chat.api.userChat.clear(Chat.Id(id))
    } inject Redirect(routes.Study.show(id))
  }

  def importPdn(id: String) = AuthBody { implicit ctx => me =>
    implicit val req = ctx.body
    get("sri") ?? { uid =>
      lidraughts.study.DataForm.importPdn.form.bindFromRequest.fold(
        err => {
          logger.info(s"PDN import for $id failed: $err")
          jsonFormError(err)
        },
        data => env.api.importPdns(me, StudyModel.Id(id), data.toChapterDatas, sticky = data.sticky, lidraughts.socket.Socket.Uid(uid))
      )
    }
  }

  private def chaptersToIdNames(chapters: List[Chapter.Metadata]) =
    chapters.map { ch =>
      Chapter.IdName(ch.id, ch.name)
    }

  def embed(id: String, chapterId: String) = Action.async { implicit req =>
    env.api.byIdWithChapter(id, chapterId).map(_.filterNot(_.study.isPrivate)) flatMap {
      _.fold(embedNotFound) {
        case WithChapter(study, chapter) =>
          val gamebookChapters = ~chapter.gamebook && getBool("next", req)
          for {
            chapters <- if (gamebookChapters) env.chapterRepo.orderedGamebookMetadataByStudy(id) else fuccess(List(chapter.metadata))
            chapterIdNames <- if (gamebookChapters) fuccess(chaptersToIdNames(chapters)) else env.chapterRepo.idNames(study.id)
            studyJson <- env.jsonView(study.copy(
              members = lidraughts.study.StudyMembers(Map.empty) // don't need no members
            ), chapters, chapter, none)
            setup = chapter.setup
            initialFen = chapter.root.fen.some
            pov = UserAnalysis.makePov(initialFen, setup.variant)
            baseData = Env.round.jsonView.userAnalysisJson(pov, lidraughts.pref.Pref.default, initialFen, setup.orientation, owner = false, me = none)
            analysis = baseData ++ Json.obj(
              "treeParts" -> partitionTreeFullUciJsonWriter.writes {
                lidraughts.study.TreeBuilder.makeRoot(chapter.root, setup.variant)
              }
            )
            data = lidraughts.study.JsonView.JsData(study = studyJson, analysis = analysis)
            result <- negotiate(
              html = Ok(html.study.embed(study, chapter, chapterIdNames, data)).fuccess,
              api = _ => Ok(Json.obj("study" -> data.study, "analysis" -> data.analysis)).fuccess
            )
          } yield result
      }
    } map NoCache
  }

  private def embedNotFound(implicit req: RequestHeader): Fu[Result] =
    fuccess(NotFound(html.study.embed.notFound))

  def cloneStudy(id: String) = Auth { implicit ctx => me =>
    OptionFuResult(env.api.byId(id)) { study =>
      CanViewResult(study) {
        Ok(html.study.clone(study)).fuccess
      }
    }
  }

  private val CloneLimitPerUser = new lidraughts.memo.RateLimit[lidraughts.user.User.ID](
    credits = 10 * 3,
    duration = 24 hour,
    name = "clone study per user",
    key = "clone_study.user",
    whitelist = () => Env.study.rateLimitDisabledSetting.get().value.map(lidraughts.user.UserRepo.normalize)
  )

  private val CloneLimitPerIP = new lidraughts.memo.RateLimit[IpAddress](
    credits = 25 * 3,
    duration = 24 hour,
    name = "clone study per IP",
    key = "clone_study.ip"
  )

  def cloneApply(id: String) = Auth { implicit ctx => me =>
    implicit val default = ornicar.scalalib.Zero.instance[Fu[Result]](notFound)
    val cost = if (isGranted(_.Coach) || me.hasTitle) 1 else 3
    CloneLimitPerUser(me.id, cost = cost) {
      CloneLimitPerIP(HTTPRequest lastRemoteAddress ctx.req, cost = cost) {
        OptionFuResult(env.api.byId(id)) { prev =>
          CanViewResult(prev) {
            env.api.clone(me, prev) map { study =>
              Redirect(routes.Study.show((study | prev).id.value))
            }
          }
        }
      }
    }
  }

  private val PdnRateLimitPerIp = new lidraughts.memo.RateLimit[IpAddress](
    credits = 30,
    duration = 1 minute,
    name = "export study PDN per ip",
    key = "export.study_pdn.ip"
  )

  def pdn(id: String) = Open { implicit ctx =>
    OnlyHumans {
      PdnRateLimitPerIp(HTTPRequest.lastRemoteAddress(ctx.req)) {
        OptionFuResult(env.api byId id) { study =>
          CanViewResult(study) {
            lidraughts.mon.export.pdn.study()
            env.pdnDump(study) map { pdns =>
              Ok(pdns.mkString("\n\n\n")).withHeaders(
                CONTENT_TYPE -> pdnContentType,
                CONTENT_DISPOSITION -> ("attachment; filename=" + (env.pdnDump filename study))
              )
            }
          }
        }
      }
    }
  }

  def chapterPdn(id: String, chapterId: String) = Open { implicit ctx =>
    OnlyHumans {
      env.api.byIdWithChapter(id, chapterId) flatMap {
        _.fold(notFound) {
          case WithChapter(study, chapter) => CanViewResult(study) {
            lidraughts.mon.export.pdn.studyChapter()
            Ok(env.pdnDump.ofChapter(study, chapter).toString).withHeaders(
              CONTENT_TYPE -> pdnContentType,
              CONTENT_DISPOSITION -> ("attachment; filename=" + (env.pdnDump.filename(study, chapter)))
            ).fuccess
          }
        }
      }
    }
  }

  def multiBoard(id: String, page: Int) = Open { implicit ctx =>
    OptionFuResult(env.api byId id) { study =>
      CanViewResult(study) {
        env.multiBoard.json(study.id, page, getBool("playing")) map { json =>
          Ok(json) as JSON
        }
      }
    }
  }

  private[controllers] def CanViewResult(study: StudyModel)(f: => Fu[Result])(implicit ctx: lidraughts.api.Context) =
    if (canView(study)) f
    else negotiate(
      html = fuccess(Unauthorized(html.site.message.privateStudy(study.ownerId))),
      api = _ => fuccess(Unauthorized(jsonError("This study is now private")))
    )

  private def canView(study: StudyModel)(implicit ctx: lidraughts.api.Context) =
    !study.isPrivate || ctx.userId.exists(study.members.contains)

  private implicit def makeStudyId(id: String): StudyModel.Id = StudyModel.Id(id)
  private implicit def makeChapterId(id: String): Chapter.Id = Chapter.Id(id)

  private[controllers] def streamsOf(study: StudyModel)(implicit ctx: Context): Fu[List[lidraughts.streamer.Stream]] =
    Env.streamer.liveStreamApi.all.flatMap {
      _.streams.filter { s =>
        study.members.members.exists(m => s is m._2.id)
      }.map { stream =>
        (fuccess(ctx.me ?? stream.streamer.is) >>|
          env.isConnected(study.id, stream.streamer.userId)) map { _ option stream }
      }.sequenceFu.map(_.flatten)
    }
}
