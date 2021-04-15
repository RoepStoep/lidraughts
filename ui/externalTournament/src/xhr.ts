import throttle from 'common/throttle';
import { json } from 'common/xhr';
import ExternalTournamentCtrl from './ctrl';

// when the tournament no longer exists
const onFail = () => window.lidraughts.reload();

const reload = (ctrl: ExternalTournamentCtrl) =>
  json(`/tournament/external/${ctrl.data.id}`).then(data => {
    ctrl.reload(data);
    ctrl.redraw();
  }).catch(onFail);

const answer = (ctrl: ExternalTournamentCtrl, accept: boolean) =>
  json(`/tournament/external/${ctrl.data.id}/answer`, {
    method: 'post',
    body: JSON.stringify({ accept }),
    headers: { 'Content-Type': 'application/json' },
  }).catch(onFail);

export default {
  answer: throttle(1000, answer),
  reloadNow: reload
};
