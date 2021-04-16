import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { dataIcon, bind, spinner } from './util';
import { ongoing, upcoming, finished } from './games'
import players from './players'
import playerInfo from './playerInfo';

export default function(ctrl: ExternalTournamentCtrl) {
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
    playerInfo(ctrl) || upcoming(ctrl),
    h('div.tour-ext__main',
      h('div.box', [
        header(ctrl),
        joinTournament(ctrl) || joinGame(ctrl),
        players(ctrl),
        ongoing(ctrl),
        finished(ctrl),
      ])
    ),
    ctrl.opts.chat ? h('div.chat__members.none', [
      h('span.number', '\xa0'), ' ', ctrl.trans.noarg('spectators'), ' ', h('span.list')
    ]) : null
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
