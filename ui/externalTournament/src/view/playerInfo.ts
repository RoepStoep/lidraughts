import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { player as lidraughtsPlayer, result as renderResult, fmjdPlayer, spinner, bind, userName, dataIcon, numberRow, stringRow, userLink, fmjdLink } from './util';
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
    displayFmjd = ctrl.data.displayFmjd,
    flatSheet = flatten(data.sheet.map(p => p.mm || p)),
    finishedGames = flatSheet.filter(p => p.g && !p.o),
    nbGames = finishedGames.length,
    nbWins = finishedGames.filter(p => p.w).length,
    ratedGames =  displayFmjd ? data.sheet.filter(p => !p.o && (p.g || p.mm) && p.fmjd?.rating) :
                                finishedGames.filter(p => p.rating),
    avgOp = ratedGames.length ?
              Math.round(ratedGames.reduce((r, p) => r + ((displayFmjd ? p.fmjd?.rating : p.rating) || 0), 0) / ratedGames.length) :
              undefined;
  let sheetI = 0;
  for (let i = 0; i < data.sheet.length; i++) {
    if (data.sheet[i].b !== 0) {
      data.sheet[i].i = sheetI;
      sheetI++;
    }
  }
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
          numberRow(noarg('gamesPlayed'), nbGames),
          ...(nbGames ? [
            numberRow(noarg('points'), draughtsResult ? data.points : data.points / 2, 'raw'),
            numberRow(noarg('winRate'), [nbWins, nbGames], 'percent'),
            avgOp ? numberRow(noarg('averageOpponent'), avgOp, 'raw') : null
          ] : [])
      ])
    ]),
    h('div', [
      h('table.games', {
        hook: bind('click', e => {
          const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
          if (href) window.open(href, '_blank');
        })
      }, flatten(data.sheet.map((p, i) => {
        const round = (data.sheet.length - i).toString();
        if (p.b === 0) return null
        const isOdd = p.i! % 2 === 0;
        if (p.b) return h('tr.bye' + (isOdd ?  '.odd' : ''), {
            key: round
          }, [
            h('th', round),
            h('td.outcome', { attrs: { colspan: 3 } }, noarg('bye')),
            h('td', renderResult(p, draughtsResult))
          ]);
        return resultTr(ctrl, p, isOdd, round);
      })))
    ])
  ]);
};

function resultTr(ctrl: ExternalTournamentCtrl, p: GameResult, isOdd: boolean, roundNr: string, mm?: GameResult) {
  const draughtsResult = ctrl.data.draughtsResult,
    displayFmjd = ctrl.data.displayFmjd,
    userData = mm || p;
  if (p.mm?.length) {
    return p.mm.map((r, i) => resultTr(ctrl, r, isOdd, roundNr + '.' + (i + 1), p)).reverse()
  }
  const round = (ctrl.data.microMatches && !mm && p.o) ? roundNr + '.1' : roundNr;
  return h('tr.glpt.' + (p.w === true ? '.win' : (p.w === false ? '.loss' : '')), {
    key: round,
    class: { odd: isOdd },
    attrs: { 'data-href': '/' + p.g + (p.c === false ? '/black' : '') },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('th', round || ''),
    h('td', userName(displayFmjd ? userData.fmjd || userData.user : userData.user)),
    h('td', '' + (displayFmjd ? userData.fmjd?.rating || '' : (p.rating + (p.provisional ? '?' : '')))),
    h('td.is.color-icon.' + (p.c ? 'white' : 'black')),
    h('td', renderResult(p, draughtsResult))
  ]);
}

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

function flatten(arr: any[]) {
  return arr.reduce(function(acc, cur) {
		if (Array.isArray(cur)) {
      flatten(cur).forEach(r => acc.push(r));
		} else {
			acc.push(cur);
		}
    return acc;
  }, [])
}

function setup(vnode: VNode) {
  const el = vnode.elm as HTMLElement, p = window.lidraughts.powertip;
  p.manualUserIn(el);
  p.manualGameIn(el);
}
