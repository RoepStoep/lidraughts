import { h } from 'snabbdom';
import LobbyController from '../ctrl';

function timer(pov) {
  const date = Date.now() + pov.secondsLeft * 1000;
  return h('time.timeago', {
    hook: {
      insert(vnode) {
        (vnode.elm as HTMLElement).setAttribute('datetime', '' + date);
      }
    }
  }, window.lidraughts.timeago.format(date));
}

export default function(ctrl: LobbyController) {
  return h('div.now-playing',
    ctrl.data.nowPlaying.map(pov => {
      const u = pov.opponent,
        board = pov.variant.board,
        title64 = u.title && u.title.endsWith('-64');
      return h('a.' + pov.variant.key, {
        key: `${pov.gameId}${pov.lastMove}`,
        attrs: { href: '/' + pov.fullId }
      }, [
        h('span.mini-board.cg-wrap.is2d.is' + board.key, {
          attrs: {
            'data-state': `${pov.fen}|${board.size[0]}x${board.size[1]}|${pov.color}|${pov.lastMove}`
          },
          hook: {
            insert(vnode) {
              window.lidraughts.miniBoard.init(vnode.elm as HTMLElement);
            }
          }
        }),
        h('span.meta', [
          u.title && h(
            'span.title', 
            title64 ? { attrs: {'data-title64': true } } : (u.title == 'BOT' ? { attrs: { 'data-bot': true } } : {}), 
            (title64 ? u.title.slice(0, u.title.length - 3) : u.title) + ' '
          ),
          u.ai ? ctrl.trans('aiNameLevelAiLevel', 'Scan', u.ai) : u.username,
          h('span.indicator',
            pov.isMyTurn ?
            (pov.secondsLeft ? timer(pov) : [ctrl.trans.noarg('yourTurn')]) :
            h('span', '\xa0')) // &nbsp;
        ])
      ])
    })
  );
}
