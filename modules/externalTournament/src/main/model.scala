package lidraughts.externalTournament

case class PlayerInfo(userId: lidraughts.user.User.ID, results: List[PlayerResult])
case class PlayerResult(game: lidraughts.game.Game, color: draughts.Color, win: Option[Boolean])
