package controllers

import lidraughts.app._
import lidraughts.hub.lightTeam._
import lidraughts.simul.crud.CrudForm.{ empty => emptyForm }
import lidraughts.user.UserRepo
import views._

object SimulCrud extends LidraughtsController {

  private def env = Env.simul
  private def forms = lidraughts.simul.SimulForm
  private def crud = env.crudApi
  private def teamApi = Env.team.api

  def index(page: Int) = Secure(_.ManageSimul) { implicit ctx => me =>
    crud.paginator(page) map { paginator =>
      html.simul.crud.index(paginator)
    }
  }

  def edit(id: String) = Secure(_.ManageSimul) { implicit ctx => me =>
    OptionFuResult(crud one id) { simul =>
      crud.editForm(simul) map { form => Ok(html.simul.crud.edit(simul, form)) }
    }
  }

  def update(id: String) = SecureBody(_.ManageSimul) { implicit ctx => me =>
    OptionFuResult(crud one id) { simul =>
      UserRepo.byId(simul.hostId) flatMap { host =>
        UserRepo.byId(simul.arbiterId) flatMap { arbiter =>
          implicit val req = ctx.body
          crud.editForm(simul, host, arbiter).bindFromRequest.fold(
            err => BadRequest(html.simul.crud.edit(
              simul,
              forms.applyVariants.bindFromRequest.fold(
                err2 => err,
                data => err.copy(value = emptyForm.copy(variants = data.variants).some)
              )
            )).fuccess,
            rawData => validateTeam(rawData) flatMap { data =>
              UserRepo.named(data.hostName) flatMap {
                case Some(newHost) =>
                  UserRepo.named(data.arbiterName) flatMap { newArbiter =>
                    crud.update(simul, data, newHost, newArbiter) inject Redirect(routes.SimulCrud.edit(id))
                  }
                case _ => BadRequest(s"New host ${data.hostName} not found").fuccess
              }
            }
          )
        }
      }
    }
  }

  def form = Secure(_.ManageSimul) { implicit ctx => me =>
    Ok(html.simul.crud.create(crud.createForm)).fuccess
  }

  def create = SecureBody(_.ManageSimul) { implicit ctx => me =>
    implicit val req = ctx.body
    crud.createForm.bindFromRequest.fold(
      err => BadRequest(html.simul.crud.create(
        forms.applyVariants.bindFromRequest.fold(
          err2 => err,
          data => err.copy(value = emptyForm.copy(variants = data.variants).some)
        )
      )).fuccess,
      rawData => validateTeam(rawData) flatMap { data =>
        UserRepo.named(data.hostName) flatMap {
          case Some(host) =>
            UserRepo.named(data.arbiterName) flatMap { arbiter =>
              crud.create(data, host, arbiter) map { simul =>
                Redirect(routes.SimulCrud.edit(simul.id))
              }
            }
          case _ => BadRequest(s"Host ${data.hostName} not found").fuccess
        }
      }
    )
  }

  private def validateTeam(data: lidraughts.simul.crud.CrudForm.Data): Fu[lidraughts.simul.crud.CrudForm.Data] =
    data.team.??(teamApi.light).map(team => if (team.isDefined) data else data.copy(team = None))
}
