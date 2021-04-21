import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { player as lidraughtsPlayer, fmjdPlayer, spinner, bind, userName, dataIcon, numberRow, stringRow, userLink, fmjdLink, result  } from './util';
import { FmjdPlayer } from '../interfaces';
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
    displayFmjd = ctrl.data.displayFmjd,
    games = data.sheet.filter(p => p.g).length,
    wins = data.sheet.filter(p => p.w).length,
    points = data.sheet.reduce((r, p) => r + (p.w === true ? 1.0 : (p.w === false ? 0.0 : 0.5)), 0),
    ratedGames = data.sheet.filter(p => p.g && (displayFmjd ? p.fmjd?.rating : p.rating)),
    avgOp = ratedGames.length ?
        Math.round(ratedGames.reduce((r, p) => r + ((displayFmjd ? p.fmjd?.rating : p.rating) || 0), 0) / ratedGames.length) :
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
        displayFmjd ? fmjdPlayer(data, true, false) : lidraughtsPlayer(data, true, false, false)
      ]),
      renderFmjdInfo(data.fmjd, data.user, noarg, displayFmjd),
      h('table.tour-info', [
          numberRow(noarg('gamesPlayed'), games),
          ...(games ? [
            numberRow(noarg('points'), draughtsResult ? points * 2 : points, 'raw'),
            numberRow(noarg('winRate'), [wins, games], 'percent'),
            avgOp ? numberRow(noarg('averageOpponent'), avgOp, 'raw') : null
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
        const round = data.sheet.length - i,
            res = result(p, draughtsResult);
        if (p.b === 0) return null
        else if (p.b) return h('tr.' + p, {
            key: round
          }, [
            h('th', '' + round),
            h('td.outcome', { attrs: { colspan: 3 } }, noarg('bye')),
            h('td', res)
          ]);
        return h('tr.glpt.' + (p.w === true ? '.win' : (p.w === false ? '.loss' : '')), {
          key: round,
          attrs: { 'data-href': '/' + p.g + (p.c === false ? '/black' : '') },
          hook: {
            destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
          }
        }, [
          h('th', '' + round),
          h('td', userName(displayFmjd ? p.fmjd || p.user : p.user)),
          h('td', '' + (displayFmjd ? p.fmjd?.rating || '' : (p.rating + (p.provisional ? '?' : '')))),
          h('td.is.color-icon.' + (p.c ? 'white' : 'black')),
          h('td', res)
        ]);
      }))
    ])
  ]);
};

function renderFmjdInfo(p: FmjdPlayer | undefined, u: LightUser, noarg: TransNoArg, displayFmjd: boolean) {
  return p ? h('div.fmjd-info', [
    h('div' + (displayFmjd ? '.photo-only' : ''), [
      h('img.photo', {
        attrs: { 
          src: p.picUrl,
          title: p.name
         },
        hook: bind('error', e => {
          const el = e.target as HTMLElement;
          el.setAttribute('src', '/assets/images/no-profile-pic.png');
          el.setAttribute('title', 'No picture');
          el.className += ' not-found';
        }) 
      }),
      !displayFmjd ? h('span.full-name', p.name) : null
    ]),
    h('div.fmjd-fields' + (!displayFmjd ? '.with-name' : ''),
      h('table', [
        h('tr', [
          h('th', 'FMJD ID'), 
          h('td', fmjdLink(p.id, noarg('toFmjdProfile')))
        ]),
        h('tr', [
          h('th', noarg('countryOrRegion')), 
          h('td', [
            h('img.flag', {
              attrs: { 
                src: `/assets/images/flags/${p.country.code}.png`,
                title: p.country.code === '_unknown' ? noarg('unknown') : p.country.name
              }
            }),
            p.country.code[0] !== '_' ? p.country.code : ''
          ])
        ]),
        stringRow(noarg('rating'), p.rating ? '' + p.rating : '-'),
        p.title ? stringRow(noarg('draughtsTitle'), p.title) : null,
        !displayFmjd ? null : h('tr', [
          h('th', noarg('username')), 
          h('td', userLink(u, false))
        ])
      ])
    )
  ]) : null;
}

function setup(vnode: VNode) {
  const el = vnode.elm as HTMLElement, p = window.lidraughts.powertip;
  p.manualUserIn(el);
  p.manualGameIn(el);
}
