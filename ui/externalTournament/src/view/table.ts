import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import ExternalTournamentCtrl from '../ctrl';
import { BaseGame } from '../interfaces';
import { player as renderPlayer, preloadUserTips, bind } from './util';
import playerInfo from './playerInfo'

export default function table(ctrl: ExternalTournamentCtrl): VNode {
  return ctrl.data.finished.length ? h('div.tour-ext__table', [
    playerInfo(ctrl) || upcoming(ctrl, true),
    finished(ctrl)
  ]) : (playerInfo(ctrl) || upcoming(ctrl, false))
}

function upcoming(ctrl: ExternalTournamentCtrl, table: boolean): VNode {
  const dateFormatter = getDateFormatter();
  return h('div.tour-ext__games' + (table ? '' : '.tour-ext__upcoming'), [
    h('h2', 'Upcoming games'),
    ctrl.data.upcoming.length ? h('table.slist', 
      h('tbody', ctrl.data.upcoming.map(c => 
        h('tr', {
            hook: bind('click', _ => window.lidraughts.redirect('/' + c.id))
          },
          [
            h('td', c.startsAt ? dateFormatter(new Date(c.startsAt)) : 'Open'),
            h('td', renderPlayers(c))
          ]
        )
      ))
    ) : h('div.empty', ctrl.trans.noarg('none'))
  ]);
}

function finished(ctrl: ExternalTournamentCtrl): VNode | null {
  const dateFormatter = getDateFormatter(),
    draughtsResult = ctrl.data.draughtsResult;
  return ctrl.data.finished.length ? h('div.tour-ext__games.tour-ext__finished', [
    h('h2', ctrl.trans.noarg('recentlyFinished')),
      h('table.slist', h('tbody',
        ctrl.data.finished.map(g => h('tr', [
          h('td', 
            h('a.text',
              { attrs: { href: '/' + g.id } },
              dateFormatter(new Date(g.createdAt))
            )
          ),
          h('td', renderPlayers(g)),
          h('td',
            h('div.result', g.winner ? 
              (g.winner == 'white' ? (draughtsResult ? '2-0' : '1-0') : (draughtsResult ? '0-2' : '0-1')) :
              (draughtsResult ? '1-1' : '½-½')
            )
          )
        ]))
      ))
  ]) : null;
}

function renderPlayers(g: BaseGame) {
  return h('div.players', { 
      hook: {
        insert: vnode => preloadUserTips(vnode.elm as HTMLElement)
      }
    },
    [
      playerWrapper(g, 'white'),
      playerWrapper(g, 'black')
    ]
  );
}

const playerWrapper = (g: BaseGame, c: Color) =>
  h(`div.player.color-icon.is.${c}.text`, renderPlayer(g[c], true, true));

let cachedDateFormatter: (date: Date) => string;

function getDateFormatter(): (date: Date) => string {
  if (!cachedDateFormatter) cachedDateFormatter = (window.Intl && Intl.DateTimeFormat) ?
    new Intl.DateTimeFormat(document.documentElement!.lang, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric'
    }).format : function(d) { return d.toLocaleString(); }

  return cachedDateFormatter;
}