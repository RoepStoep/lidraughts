import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import ExternalTournamentCtrl from '../ctrl';
import { BaseGame, ExternalTournamentData } from '../interfaces';
import { player as renderPlayer, preloadUserTips, bind, drawTime } from './util';
import playerInfo from './playerInfo'

export default function table(ctrl: ExternalTournamentCtrl): VNode {
  return ctrl.data.finished.length ? h('div.tour-ext__table', [
    playerInfo(ctrl) || upcoming(ctrl, true),
    finished(ctrl)
  ]) : (playerInfo(ctrl) || upcoming(ctrl, false))
}

function upcoming(ctrl: ExternalTournamentCtrl, table: boolean): VNode {
  return h('div.tour-ext__games' + (table ? '' : '.tour-ext__upcoming'), [
    h('h2', ctrl.trans.noarg('upcomingGames')),
    ctrl.data.upcoming.length ? 
      h('table.slist', {
          hook: bind('click', e => {
            const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
            if (href) window.open(href, '_blank');
          })
        }, [
          h('tbody', ctrl.data.upcoming.map((c, i) => {
            const hrefAttr = { attrs: { 'data-href': '/' + c.id } };
            return h('tr', {
                key: i,
                attrs: { 'data-href': '/' + c.id },
                hook: {
                  insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
                  destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
                }
              },
              [
                h('td.date', hrefAttr, c.startsAt ? [drawTime(new Date(c.startsAt))] : ['-']),
                h('td', hrefAttr, renderPlayers(c))
              ]
            )
          }))
        ]
      ) : h('div.empty', ctrl.trans.noarg('none'))
  ]);
}

function allResults(tour: ExternalTournamentData, noarg: TransNoArg): VNode {
  return h('tr',
    h('td.all-results', {
      attrs: { colspan: 3 }
    }, [
      h('a', 
        { attrs: { href: `/tournament/external/${tour.id}/results` } },
        noarg('allResults')
      )
    ])
  );
}

function finished(ctrl: ExternalTournamentCtrl): VNode | null {
  const dateFormatter = getDateFormatter(),
    noarg = ctrl.trans.noarg,
    draughtsResult = ctrl.data.draughtsResult;
  return ctrl.data.finished.length ? h('div.tour-ext__games.tour-ext__finished', [
    h('h2', ctrl.trans.noarg('latestResults')),
    h('table.slist', {
        hook: bind('click', e => {
          const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
          if (href) window.open(href, '_blank');
        })
      }, [
        h('tbody', ctrl.data.finished.map((g, i) => {
          const hrefAttr = { attrs: { 'data-href': '/' + g.id } };
          return h('tr', {
              key: i,
              attrs: { 'data-href': '/' + g.id },
              hook: {
                insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
                destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
              }
            },
            [
              h('td.date', dateFormatter(new Date(g.createdAt))),
              h('td', hrefAttr, renderPlayers(g)),
              h('td', hrefAttr, [
                h('div.result', g.winner ? 
                  (g.winner == 'white' ? (draughtsResult ? '2-0' : '1-0') : (draughtsResult ? '0-2' : '0-1')) :
                  (draughtsResult ? '1-1' : '½-½')
                )
              ])
            ])
          }).concat(ctrl.data.nbFinished > ctrl.data.finished.length ? [allResults(ctrl.data, noarg)] : [])
        )
      ]
    )
  ]) : null;
}

function renderPlayers(g: BaseGame) {
  return [
    playerWrapper(g, 'white'),
    playerWrapper(g, 'black')
  ];
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