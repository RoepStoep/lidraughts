import { json } from 'common/xhr';
import ExternalTournamentCtrl from './ctrl';

// when the tournament no longer exists
const onFail = () => window.lidraughts.reload();

const reload = (ctrl: ExternalTournamentCtrl) =>
  json(`/tournament/external/${ctrl.data.id}`).then(data => {
    ctrl.reload(data);
    ctrl.redraw();
  }).catch(onFail);

export default {
  reloadNow: reload
};
