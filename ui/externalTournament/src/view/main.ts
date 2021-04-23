import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { Pager, playerStatus } from '../interfaces';
import * as pagination from '../pagination';
import { dataIcon, bind, spinner, preloadUserTips, player as renderPlayer, userLink, fmjdLink } from './util';
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

function joinTournament(ctrl: ExternalTournamentCtrl): VNode | null {
  const myInfo = ctrl.data.me,
    noarg = ctrl.trans.noarg,
    viewFmjdTitle = myInfo?.fmjdId ? ctrl.trans('viewX', noarg('fmjdProfile')) : '',
    lines = myInfo?.fmjdId ? [
      h('span', 
        ctrl.trans.vdom('youHaveBeenAssignedFmjdIdX', fmjdLink(myInfo.fmjdId, viewFmjdTitle))
          .concat([' '])
          .concat(ctrl.trans.vdom('contactTournamentOrganizerXIfNotYourId', userLink(ctrl.data.createdBy, false)))
      ),  
      h('span.fmjd-warning', ctrl.trans.vdom('fmjdProfileInformationWillBeVisible', fmjdLink(myInfo.fmjdId, viewFmjdTitle, noarg('fmjdProfile'))))
    ] : [];
  if (ctrl.data.autoStart) lines.push(h('span', noarg('yourGamesStartAutomatically')))
  const intro = noarg('youHaveBeenInvitedToPlay') + (lines.length > 1 ? ' ' + noarg('pleaseReadTheFollowingCarefully') : '');
  return myInfo?.canJoin ? h('div.tour-ext__main__join-tournament', [
    h('div.explanation', [h('span.first', intro)].concat(
      lines.length === 1 ? [lines[0]] : [h('ul', lines.map(t => h('li', t)))])
    ),
    h('div.choices', ctrl.joinSpinner ? spinner() : [
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
  const gameId = ctrl.myGameId();
  return gameId ? h('a.tour-ext__main__join-game.button.is.is-after', {
    attrs: { href: '/' + gameId }
  }, [
    ctrl.trans.noarg('youArePlaying'), h('br'),
    ctrl.trans.noarg('joinTheGame')
  ]) : null;
}

function header(ctrl: ExternalTournamentCtrl): VNode {
  return h('div.tour-ext__main__header', [
    h('i.img', { attrs: dataIcon('g') }),
    h('h1', ctrl.data.name),
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

