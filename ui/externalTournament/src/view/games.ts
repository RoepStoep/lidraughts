import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import * as boards from './boards';
import ExternalTournamentCtrl from '../ctrl';
import { BaseGame } from '../interfaces';
import { player as renderPlayer, preloadUserTips, bind } from './util';

export function ongoing(ctrl: ExternalTournamentCtrl): VNode | null {
  return ctrl.data.ongoing.length ? h('div.tour-ext__main__ongoing', [
    h('h2', 'Currently playing'),
    boards.many(ctrl.data.ongoing, ctrl.opts.draughtsResult)
  ]) : null;
}

export function upcoming(ctrl: ExternalTournamentCtrl): VNode {
  const dateFormatter = getDateFormatter();
  return h('div.tour-ext__upcoming', [
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

export function finished(ctrl: ExternalTournamentCtrl): VNode | null {
  const dateFormatter = getDateFormatter();
  return ctrl.data.finished.length ? h('div.tour-ext__main__finished', [
    h('h2', 'Finished games'),
      h('table.slist.slist-pad', h('tbody',
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
              (g.winner == 'white' ? (ctrl.opts.draughtsResult ? '2-0' : '1-0') : (ctrl.opts.draughtsResult ? '0-2' : '0-1')) :
              (ctrl.opts.draughtsResult ? '1-1' : '½-½')
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