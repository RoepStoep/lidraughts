package views.html
package account

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.pref.PrefCateg

object bits {

  def categName(categ: lidraughts.pref.PrefCateg)(implicit ctx: Context): String = categ match {
    case PrefCateg.GameDisplay => trans.preferences.display.txt()
    case PrefCateg.DraughtsClock => trans.preferences.draughtsClock.txt()
    case PrefCateg.GameBehavior => trans.preferences.gameBehavior.txt()
    case PrefCateg.Privacy => trans.privacy.txt()
  }
}
