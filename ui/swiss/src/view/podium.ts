import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import SwissCtrl from '../ctrl';
import { PodiumPlayer } from '../interfaces';
import { userName } from './util';

function podiumStats(p: PodiumPlayer, trans: Trans, draughtsResult: boolean): VNode {
  const noarg = trans.noarg;
  return h('table.stats', [
    h('tr', [h('th', noarg('points')), h('td', '' + (draughtsResult ? p.points * 2 : p.points))]),
    h('tr', [h('th', noarg('tieBreak')), h('td', '' + p.tieBreak)]),
    p.performance ? h('tr', [h('th', noarg('performance')), h('td', '' + p.performance)]) : null
  ]);
}

function podiumPosition(p: PodiumPlayer, pos: string, trans: Trans, draughtsResult: boolean): VNode | undefined {
  return p ? h('div.' + pos, {
    class: {
      engine: !!p.engine
    }
  }, [
    h('div.trophy'),
    h('a.text.ulpt.user-link', {
      attrs: { href: '/@/' + p.user.name }
    }, userName(p.user)),
    podiumStats(p, trans, draughtsResult)
  ]) : undefined;
}

export default function podium(ctrl: SwissCtrl) {
  const p = ctrl.data.podium || [];
  return h('div.swiss__podium', [
    podiumPosition(p[1], 'second', ctrl.trans, ctrl.draughtsResult),
    podiumPosition(p[0], 'first', ctrl.trans, ctrl.draughtsResult),
    podiumPosition(p[2], 'third', ctrl.trans, ctrl.draughtsResult)
  ]);
}
