import { h } from 'snabbdom'
import { VNode,  } from 'snabbdom/vnode';
import ExternalTournamentCtrl from '../ctrl';
import { player as lidraughtsPlayer, result as renderResult, fmjdPlayer, onInsert, bind, userNameHtml } from './util';
import { MaybeVNodes, Pager, PlayerInfo, GameResult } from '../interfaces';

function playerTr(ctrl: ExternalTournamentCtrl, p: PlayerInfo) {
  const noarg = ctrl.trans.noarg,
    draughtsResult = ctrl.data.draughtsResult,
    userId = p.user.id,
    winChar = draughtsResult ? '2' : '1',
    drawChar = draughtsResult ? '1' : '½';
  return h('tr', {
      key: userId,
      class: {
        me: ctrl.isMe(p.user),
        active: ctrl.playerInfoId === userId
      },
      hook: bind('click', _ => ctrl.showPlayerInfo(p), ctrl.redraw)
    }, [
      h('td.rank', p.rank ? [p.rank] : []),
      h('td.player', ctrl.data.displayFmjd ? fmjdPlayer(p, false, true) : lidraughtsPlayer(p, false, true, false)),
      h('td.games' + (ctrl.data.rounds ? '.rounds' : ''),
        h('div',
          p.sheet.map(r => {
            if (r.b) return h('bye', title(noarg('bye')), r.b === 2 ? winChar : drawChar);
            else if (r.b === 0) return h('r');
            return gameResult(ctrl, r);
          }).concat(
            [...Array(Math.max(ctrl.data.roundsPlayed || 0, p.sheet.length) - p.sheet.length)].map(_ => h('r'))
          )
        )),
      h('td.points', title(noarg('points')), '' + (draughtsResult ? p.points : p.points / 2)),
    ])
}

function gameResult(ctrl: ExternalTournamentCtrl, r: GameResult) {
  const color = r.c ? 'white' : 'black',
    microMatch = r.mm?.length,
    tag = microMatch ? 'mm.' : 'a.glpt.';
  return h(tag + (r.o ? 'ongoing' : (r.w === true ? 'win' : (r.w === false ? 'loss' : 'draw'))), 
    {
      attrs: r.g ? {
        key: r.g,
        href: `/${r.g}${color === 'white' ? '' : '/black'}`
      } : undefined,
      hook: microMatch ? 
        onInsert(el => microMatchPowertip(el, resultHtml(ctrl, r.mm!, r))) : 
        onInsert(window.lidraughts.powertip.manualGame)
    }, 
    renderResult(r, ctrl.data.draughtsResult)
  )
}

function microMatchPowertip(el: HTMLElement, html: string) {
  $(el).powerTip({
    intentPollInterval: 200,
    placement: 'w',
    smartPlacement: true,
    closeDelay: 200,
    popupId: 'microMatch'
  }).data('powertip', html).on({
    powerTipRender: () => {
      const tip = $('#microMatch');
      if (tip.length) {
        window.lidraughts.powertip.manualGameIn(tip[0]);
        tip.on({
          mouseleave: () => setTimeout(() => tip.hide(), 200),
          click: e => {
            const href = ((e.target as HTMLElement).parentNode as HTMLElement).getAttribute('data-href');
            if (href) window.open(href, '_blank');
          }
        });
      }
    }
  });
}

function resultHtml(ctrl: ExternalTournamentCtrl, mm: GameResult[], parent: GameResult) {
  const draughtsResult = ctrl.data.draughtsResult,
    displayFmjd = ctrl.data.displayFmjd;
  let table = ''
  mm.forEach((r, i) => {
    const classes = 'glpt' + (r.w === true ? ' win' : (r.w === false ? ' loss' : ''));
    const tr = `<tr class="${classes}" data-href="/${r.g + (r.c === false ? '/black' : '')}">`
    const th = `<th>#${i + 1}</th>`;
    const td1 = `<td>${userNameHtml(displayFmjd ? parent.fmjd || parent.user : parent.user)}</td>`;
    const td2 = `<td class="rating">${displayFmjd ? parent.fmjd?.rating || '' : (r.rating + (r.provisional ? '?' : ''))}</td>`;
    const td3 = `<td class="is color-icon ${r.c ? 'white' : 'black'}"></td>`;
    const td4 = `<td>${renderResult(r, draughtsResult)}</td>`;
    table += tr + th + td1 + td2 + td3 + td4 + '</tr>';
  }, '');
  let headerKey = 'microMatch';
  if (!parent.o && mm.length == 2 ) {
    const points = getPoints(mm[0]) + getPoints(mm[1]);
    if (points > 2) headerKey = 'microMatchWin'
    else if (points === 2) headerKey = 'microMatchDraw'
    else headerKey = 'microMatchLoss'
  }
  return `<span class="header">${ctrl.trans.noarg(headerKey)}</span><table>${table}</table>`;
}

function getPoints(p?: GameResult) {
  if (!p) return 0;
  else if (p.w === true || p.b === 2) return 2;
  else if (p.w === false || p.b === 0) return 0;
  return p.o ? 0 : 1;
}

const title = (str: string) => ({ attrs: { title: str } });

let lastBody: MaybeVNodes | undefined;

const preloadUserTips = (vn: VNode) => window.lidraughts.powertip.manualUserIn(vn.elm as HTMLElement);

export default function standing(ctrl: ExternalTournamentCtrl, pag: Pager): VNode {
  const tableBody = pag.currentPageResults ?
    pag.currentPageResults.map(res => playerTr(ctrl, res)) : lastBody;
  if (pag.currentPageResults) lastBody = tableBody;
  return h('table.slist.tour-ext__standing', {
    class: {
      loading: !pag.currentPageResults,
      long: !!ctrl.data.rounds && ctrl.data.rounds > 10,
      xlong: !!ctrl.data.rounds && ctrl.data.rounds > 20,
    },
  }, [
    h('tbody', {
      hook: {
        insert: preloadUserTips,
        update(_, vnode) { preloadUserTips(vnode) }
      }
    }, tableBody || [])
  ]);
}
