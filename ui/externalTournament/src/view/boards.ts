import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { opposite } from 'draughtsground/util';
import { player as renderPlayer } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { Board } from '../interfaces';

export default function ongoing(ctrl: ExternalTournamentCtrl): VNode | null {
  return ctrl.data.ongoing.length ? h('div.tour-ext__main__ongoing', [
    h('h2', 'Currently playing'),
    many(ctrl.data.ongoing, ctrl.data.draughtsResult)
  ]) : null;
}

function many(boards: Board[], draughtsResult: boolean): VNode {
  return h('div.tour-ext__boards.now-playing', boards.map(board => renderBoard(board, draughtsResult)));
}

const renderBoard = (board: Board, draughtsResult: boolean): VNode => {
  const boardSize = board.variant.board;
  return h(`div.tour-ext__board.mini-game.mini-game-${board.id}.mini-game--init.is2d.is${boardSize.key}`, {
    key: board.id,
    attrs: {
      'data-state': `${board.fen}|${boardSize.size[0]}x${boardSize.size[1]}|${board.orientation}|${board.lastMove || ''}`,
      'data-live': board.id
    },
    hook: {
      insert(vnode) {
        window.lidraughts.powertip.manualUserIn(vnode.elm as HTMLElement);
      }
    }
  }, [
    boardPlayer(board, opposite(board.orientation), draughtsResult),
    h('a.cg-wrap', {
      attrs: {
        href: `/${board.id}/${board.orientation}`
      }
    }),
    boardPlayer(board, board.orientation, draughtsResult)
  ]);
}

function boardPlayer(board: Board, color: Color, draughtsResult: boolean) {
  const player = board[color];
  return h('span.mini-game__player', [
    h('span.mini-game__user', [
      player.rank ? h('strong', '#' + player.rank) : null,
      renderPlayer(player, true, true)
    ]),
    board.clock ?
      h(`span.mini-game__clock.mini-game__clock--${color}`, {
        attrs: {
          'data-time': board.clock[color]
        }
      }) :
      h('span.mini-game__result', board.winner ? 
        (board.winner == color ? (draughtsResult ? '2' : '1') : '0') :
        (draughtsResult ? '1' : '½')
      )
  ]);
}
