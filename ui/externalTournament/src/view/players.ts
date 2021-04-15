import { h } from 'snabbdom'
import ExternalTournamentCtrl from '../ctrl';
import { userTip, dataIcon, preloadUserTips, onInsert } from './util';

export default function(ctrl: ExternalTournamentCtrl) {
  const isCreator = ctrl.isCreator(),
    noarg = ctrl.trans.noarg,
    draughtsResult = ctrl.opts.draughtsResult;
  return ctrl.data.players.length ? h('div.tour-ext__main__players', [
    h('h2', 'Players'),
    h('table.slist', 
      h('tbody', {
          hook: {
            insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
            update(_, vnode) { preloadUserTips(vnode.elm as HTMLElement) }
          }
        },
        ctrl.data.players.map(p => {
          const finished = ctrl.data.finished.filter(g => p.user.id === g.white.user.id || p.user.id === g.black.user.id);
          let points = 0;
          finished.forEach(g => {
            const color = g.white.user.id === p.user.id ? 'white' : 'black';
            points += (g.winner ? (g.winner == color ? 1.0 : 0) : 0.5);
          });
          return h('tr' + (ctrl.isMe(p.user) ? '.me' : ''), [
            isCreator ? h('td.status', h('i', {
              attrs: dataIcon(p.joined ? 'E' : 'L')
            })) : null,
            h('td.player', userTip(p.user)),
            h('td.games',
              h('div', 
                finished.map(g => {
                  const color = g.white.user.id === p.user.id ? 'white' : 'black',
                    result = g.winner ? 
                      (g.winner == color ? (draughtsResult ? '2' : '1') : '0') :
                      (draughtsResult ? '1' : '½')
                  return h('a.glpt.' + (g.winner == color ? 'win' : (g.winner ? 'loss' : 'draw')), {
                    attrs: {
                      key: g.id,
                      href: `/${g.id}`
                    },
                    hook: onInsert(window.lidraughts.powertip.manualGame)
                  }, result)
                })
              )),
            h('td.points', title(noarg('points')), '' + (draughtsResult ? points * 2 : points)),
          ])
        })
      )
    )
  ]) : null;
}

const title = (str: string) => ({ attrs: { title: str } });
