import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import ExternalTournamentCtrl from '../ctrl';
import { dataIcon, bind, spinner, userLink, fmjdLink } from './util';

const oneDayInSeconds = 60 * 60 * 24;

function startClock(time) {
  return {
    insert: vnode => $(vnode.elm as HTMLElement).clock({ time })
  };
}

function clock(ctrl: ExternalTournamentCtrl): VNode | undefined {
  const d = ctrl.data;
  if (d.secondsToStart && d.startsAt) {
    if (d.secondsToStart > oneDayInSeconds) return h('div.clock', [
      h('time.timeago.shy', {
        attrs: {
          title: new Date(d.startsAt).toLocaleString(),
          datetime: Date.now() + d.secondsToStart * 1000
        },
        hook: {
          insert(vnode) {
            d.secondsToStart && (vnode.elm as HTMLElement).setAttribute('datetime', '' + (Date.now() + d.secondsToStart * 1000));
          }
        }
      })
    ]);
    return h('div.clock.clock-created', [
      h('span.shy', ctrl.trans.noarg('startingIn')),
      h('span.time.text', {
        hook: startClock(d.secondsToStart)
      })
    ]);
  }
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
  if (ctrl.data.autoStartGames) lines.push(h('span', noarg('yourGamesStartAutomatically')))
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

export function header(ctrl: ExternalTournamentCtrl): VNode {
  return h('div.tour-ext__main__header', [
    h('i.img', { attrs: dataIcon('g') }),
    h('h1', ctrl.data.name),
    clock(ctrl)
  ]);
}

export function joinButtons(ctrl: ExternalTournamentCtrl) : VNode | null {
  return joinTournament(ctrl) || joinGame(ctrl)
}
