import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { Pager, playerStatus } from '../interfaces';
import * as pagination from '../pagination';
import { preloadUserTips, player as renderPlayer } from './util';
import table from './table'
import standing from './standing'
import ongoing from './boards'
import { header, joinButtons } from './header'

export default function(ctrl: ExternalTournamentCtrl) {
  const pag = pagination.players(ctrl);
  return h('main.' + ctrl.opts.classes,{
    hook: {
      postpatch() {
        window.lidraughts.miniGame.initAll();
      }
    }
  }, [
    h('aside.tour-ext__side', {
      hook: onInsert(el => {
        $(el).replaceWith(ctrl.opts.$side);
        ctrl.opts.chat && window.lidraughts.makeChat(ctrl.opts.chat);
      })
    }),
    h('div.tour-ext__underchat', {
      hook: onInsert(el => {
        $(el).replaceWith($('.tour-ext__underchat.none').removeClass('none'));
      })
    }),
    table(ctrl),
    h('div.tour-ext__main',
      h('div.box', [
        header(ctrl),
        joinButtons(ctrl),
        controls(ctrl, pag),
        standing(ctrl, pag),
        ongoing(ctrl),
        invited(ctrl)
      ])
    ),
    ctrl.opts.chat ? h('div.chat__members.none', [
      h('span.number', '\xa0'), ' ', ctrl.trans.noarg('spectators'), ' ', h('span.list')
    ]) : null
  ]);
}

function controls(ctrl: ExternalTournamentCtrl, pag: Pager): VNode {
  return h('div.tour-ext__controls', [
    h('div.pager', pagination.renderPager(ctrl, pag))
  ]);
}

function invited(ctrl: ExternalTournamentCtrl) {
  const noarg = ctrl.trans.noarg;
  return ctrl.isCreator() && ctrl.data.invited?.length ? h('div.tour-ext__main__invited', [
    h('h2', ctrl.trans.noarg('invitedPlayers')),
    h('table.slist', 
      h('tbody', {
          hook: {
            insert: vnode => preloadUserTips(vnode.elm as HTMLElement),
            update(_, vnode) { preloadUserTips(vnode.elm as HTMLElement) }
          }
        },
        ctrl.data.invited.map(p => {
          return h('tr', [
            h('td.status' + (p.status === playerStatus.rejected ? '.rejected' : ''), h('i', {
              attrs: {
                'data-icon': (p.status === playerStatus.invited ? 'p' : 'L'),
                title: p.status === playerStatus.invited ? noarg('awaiting') : noarg('rejected')
              }
            })),
            h('td.player', renderPlayer(p, true, false, ctrl.data.displayFmjd))
          ])
        })
      )
    )
  ]) : null;
}

