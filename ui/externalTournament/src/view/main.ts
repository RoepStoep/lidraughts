import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { Pager } from '../interfaces';
import * as pagination from '../pagination';
import { dataIcon, bind, spinner } from './util';
import table from './table'
import standing from './standing'
import ongoing from './boards'

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
        joinTournament(ctrl) || joinGame(ctrl),
        controls(ctrl, pag),
        standing(ctrl, pag),
        ongoing(ctrl)
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

function joinTournament(ctrl: ExternalTournamentCtrl): VNode | null {
  return ctrl.data.me?.canJoin ? h('div.tour-ext__main__join-tournament', [
    'You have been invited to play in this tournament!',
    ctrl.joinSpinner ? spinner() : h('div.choices', [
      h('a.button.button-empty.button-red', {
        hook: bind('click', ctrl.answer(false), ctrl.redraw)
      }, ctrl.trans.noarg('decline')),
      h('a.button.button-green', {
        attrs: dataIcon('G'),
        hook: bind('click', ctrl.answer(true), ctrl.redraw)
      }, ctrl.trans.noarg('join'))
    ])
  ]) : null;
}

function joinGame(ctrl: ExternalTournamentCtrl): VNode | null {
  return ctrl.data.me?.canJoin ? h('div.tour-ext__main__join-game.button.is.is-after', [
    ctrl.trans('youArePlaying'), h('br'),
    ctrl.trans('joinTheGame')
  ]) : null;
}

function header(ctrl: ExternalTournamentCtrl): VNode {
  return h('div.tour-ext__main__header', [
    h('i.img', { attrs: dataIcon('g') }),
    h('h1', ctrl.data.name),
  ]);
}
