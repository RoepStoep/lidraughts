package lidraughts.i18n

import lidraughts.common.Lang

object I18nDb {

  sealed trait Ref
  case object Site extends Ref
  case object Arena extends Ref
  case object Emails extends Ref
  case object Learn extends Ref
  case object Activity extends Ref
  case object Coordinates extends Ref
  case object Study extends Ref
  case object Patron extends Ref
  case object Broadcast extends Ref
  case object Streamer extends Ref
  case object Tfa extends Ref
  case object Settings extends Ref
  case object Preferences extends Ref
  case object Team extends Ref
  case object PerfStat extends Ref
  case object Search extends Ref
  case object Lag extends Ref
  case object Swiss extends Ref
  case object Challenge extends Ref

  val site: Messages = lidraughts.i18n.db.site.Registry.load
  val arena: Messages = lidraughts.i18n.db.arena.Registry.load
  val emails: Messages = lidraughts.i18n.db.emails.Registry.load
  val learn: Messages = lidraughts.i18n.db.learn.Registry.load
  val activity: Messages = lidraughts.i18n.db.activity.Registry.load
  val coordinates: Messages = lidraughts.i18n.db.coordinates.Registry.load
  val study: Messages = lidraughts.i18n.db.study.Registry.load
  val patron: Messages = lidraughts.i18n.db.patron.Registry.load
  val broadcast: Messages = lidraughts.i18n.db.broadcast.Registry.load
  val streamer: Messages = lidraughts.i18n.db.streamer.Registry.load
  val tfa: Messages = lidraughts.i18n.db.tfa.Registry.load
  val settings: Messages = lidraughts.i18n.db.settings.Registry.load
  val preferences: Messages = lidraughts.i18n.db.preferences.Registry.load
  val team: Messages = lidraughts.i18n.db.team.Registry.load
  val perfStat: Messages = lidraughts.i18n.db.perfStat.Registry.load
  val search: Messages = lidraughts.i18n.db.search.Registry.load
  val lag: Messages = lidraughts.i18n.db.lag.Registry.load
  val swiss: Messages = lidraughts.i18n.db.swiss.Registry.load
  val challenge: Messages = lidraughts.i18n.db.challenge.Registry.load

  def apply(ref: Ref): Messages = ref match {
    case Site => site
    case Arena => arena
    case Emails => emails
    case Learn => learn
    case Activity => activity
    case Coordinates => coordinates
    case Study => study
    case Patron => patron
    case Broadcast => broadcast
    case Streamer => streamer
    case Tfa => tfa
    case Settings => settings
    case Preferences => preferences
    case Team => team
    case PerfStat => perfStat
    case Search => search
    case Lag => lag
    case Swiss => swiss
    case Challenge => challenge
  }

  val langs: Set[Lang] = site.keys.map(Lang.apply)(scala.collection.breakOut)
}
