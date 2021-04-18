import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { player as renderPlayer, spinner, bind, userName, dataIcon, numberRow, stringRow  } from './util';
import { FmjdPlayer, GameResult } from '../interfaces';
import ExternalTournamentCtrl from '../ctrl';

export default function(ctrl: ExternalTournamentCtrl): VNode | undefined {
  if (!ctrl.playerInfoId) return;
  const data = ctrl.data.playerInfo,
    tag = 'div.tour-ext__player-info';
  if (data?.user.id !== ctrl.playerInfoId) return h(tag, [
    h('div.stats', [
      h('h2', ctrl.playerInfoId),
      spinner()
    ])
  ]);
  const noarg = ctrl.trans.noarg,
    draughtsResult = ctrl.data.draughtsResult,
    games = data.sheet.filter(p => p.g).length,
    wins = data.sheet.filter(p => p.w).length,
    points = data.sheet.reduce((r, p) => r + (p.w === true ? 1.0 : (p.w === false ? 0.0 : 0.5)), 0),
    avgOp: number | undefined = games ?
        Math.round(data.sheet.reduce((r, p) => r + (p.rating || 1), 0) / games) :
        undefined;
  return h(tag, {
    hook: {
      insert: setup,
      postpatch(_, vnode) { setup(vnode) }
    }
  }, [
    h('a.close', {
      attrs: dataIcon('L'),
      hook: bind('click', () => ctrl.showPlayerInfo(data), ctrl.redraw)
    }),
    h('div.stats', [
      h('h2', [
        data.rank ? h('span.rank', data.rank + '. ') : null,
        renderPlayer(data, true, false)
      ]),
      renderFmjdInfo(data.fmjd, noarg),
      h('table.tour-info', [
          numberRow(noarg('points'), draughtsResult ? points * 2 : points, 'raw'),
          ...(games ? [
            numberRow(noarg('winRate'), [wins, games], 'percent'),
            numberRow(noarg('averageOpponent'), avgOp, 'raw')
          ] : [])
      ])
    ]),
    h('div', [
      h('table.games.sublist', {
        hook: bind('click', e => {
          const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
          if (href) window.open(href, '_blank');
        })
      }, data.sheet.map((p, i) => {
        const round = games - i,
            res = result(p, draughtsResult);
        return h('tr.glpt.' + (p.w === true ? '.win' : (p.w === false ? '.loss' : '')), {
          key: round,
          attrs: { 'data-href': '/' + p.g + (p.c === false ? '/black' : '') },
          hook: {
            destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
          }
        }, [
          h('th', '' + round),
          h('td', userName(p.user)),
          h('td', '' + p.rating),
          h('td.is.color-icon.' + (p.c ? 'white' : 'black')),
          h('td', res)
        ]);
      }))
    ])
  ]);
};

function renderFmjdInfo(p: FmjdPlayer | undefined, noarg: TransNoArg) {
  return p ? h('div.fmjd-info', [
    h('div', [
      h('img', {
        attrs: { src: p.picUrl },
        hook: bind('error', e => (e.target as HTMLElement).setAttribute('src', '/assets/images/streamer-nopic.svg'))
      }),
      h('a.name', {
        attrs: { 
          href: 'https://www.fmjd.org/?p=pcard&id=' + p.id,
          target: '_blank',
          rel: 'noopener'
        }
      }, p.name)
    ]),
    h('table', [
      stringRow('FMJD ID', p.id),
      stringRow('Country', p.country),
      stringRow(noarg('rating'), p.rating ? '' + p.rating : '-'),
      p.title ? stringRow('Title', p.title) : null
    ])
   ]) : null;
  }

function result(p: GameResult, draughtsResult: boolean): string {
  switch (p.w) {
    case true:
      return draughtsResult ? '2' : '1';
    case false:
      return '0';
    default:
      return draughtsResult ? '1' : '½';
  }
}

function setup(vnode: VNode) {
  const el = vnode.elm as HTMLElement, p = window.lidraughts.powertip;
  p.manualUserIn(el);
  p.manualGameIn(el);
}
