import throttle from 'common/throttle';
import { json } from 'common/xhr';
import ExternalTournamentCtrl from './ctrl';

// when the tournament no longer exists
const onFail = () => window.lidraughts.reload();

const loadPage = (ctrl: ExternalTournamentCtrl, p: number) =>
  json(`/tournament/external/${ctrl.data.id}/standing/${p}`).then(data => {
    ctrl.loadPage(data);
    ctrl.redraw();
  });

const loadPageOf = (ctrl: ExternalTournamentCtrl, userId: string): Promise<any> =>
  json(`/tournament/external/${ctrl.data.id}/page-of/${userId}`);

const reload = (ctrl: ExternalTournamentCtrl) =>
  json(`/tournament/external/${ctrl.data.id}?page=${ctrl.focusOnMe ? '' : ctrl.page}&playerInfo=${ctrl.playerInfoId || ''}`).then(data => {
    ctrl.reload(data);
    ctrl.redraw();
  }).catch(onFail);

const answer = (ctrl: ExternalTournamentCtrl, accept: boolean) =>
  json(`/tournament/external/${ctrl.data.id}/answer`, {
    method: 'post',
    body: JSON.stringify({ accept }),
    headers: { 'Content-Type': 'application/json' },
  }).catch(onFail);

const playerInfo = (ctrl: ExternalTournamentCtrl, userId: string) =>
  json(`/tournament/external/${ctrl.data.id}/player/${userId}`).then(data => {
    ctrl.data.playerInfo = data;
    ctrl.playerInfoId = userId
    ctrl.redraw();
  }).catch(onFail);

const readSheetMin = (str: string) =>
  str ? str.split('|').map(s => {
    return {
      g: s.slice(0, 8),
      w: (s[8] == 'w' ? true : (s[8] == 'l' ? false : undefined))
    };
  }) : [];

export default {
  answer: throttle(1000, answer),
  loadPage: throttle(1000, loadPage),
  loadPageOf,
  reloadNow: reload,
  playerInfo,
  readSheetMin
};
