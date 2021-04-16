import { h } from 'snabbdom'
import ExternalTournamentCtrl from '../ctrl';
import { userTip, dataIcon, preloadUserTips, onInsert, bind } from './util';

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
          const userId = p.user.id,
            finished = ctrl.data.finished.filter(g => userId === g.white.user.id || userId === g.black.user.id),
            points = finished.reduce((r, g) => {
              const color = g.white.user.id === userId ? 'white' : 'black';  
              return r + (g.winner ? (g.winner == color ? 1.0 : 0.0) : 0.5)
            }, 0);
          return h('tr', {
              key: userId,
              class: {
                me: ctrl.isMe(p.user),
                active: ctrl.playerInfoId === userId
              },
              hook: bind('click', _ => ctrl.showPlayerInfo(p), ctrl.redraw)
            }, [
              isCreator ? h('td.status', h('i', {
                attrs: dataIcon(p.joined ? 'E' : 'L')
              })) : null,
              h('td.player', userTip(p.user)),
              h('td.games',
                h('div', 
                  finished.map(g => {
                    const color = g.white.user.id === userId ? 'white' : 'black',
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
