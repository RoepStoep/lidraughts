import { h } from 'snabbdom'
import ExternalTournamentCtrl from '../ctrl';
import { userTip, dataIcon, preloadUserTips } from './util';

export default function(ctrl: ExternalTournamentCtrl) {
  const isCreator = ctrl.isCreator();
  return ctrl.data.players.length ? h('div.tour-ext__main__players', [
    h('h2', 'Players'),
    h('table.slist', 
      h('tbody', {
          hook: {
            insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
            update(_, vnode) { preloadUserTips(vnode.elm as HTMLElement) }
          }
        },
        ctrl.data.players.map(p => 
          h('tr' + (ctrl.isMe(p.user) ? '.me' : ''), [
            isCreator ? h('td.status', h('i', {
              attrs: dataIcon(p.joined ? 'E' : 'L')
            })) : null,
            h('td.player', userTip(p.user))
          ])
        )
      )
    )
  ]) : null;
}
