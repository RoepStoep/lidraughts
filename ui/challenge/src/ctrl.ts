import * as xhr from './xhr';
import notify from 'common/notification';
import { Ctrl, ChallengeOpts, ChallengeData, ChallengeUser } from './interfaces';

const li = window.lidraughts;

export default function(opts: ChallengeOpts, data: ChallengeData, redraw: () => void): Ctrl {

  let trans = (key: string, ..._: Array<string | number>) => key;
  let redirecting = false;

  function update(d: ChallengeData) {
    data = d;
    if (d.i18n) trans = li.trans(d.i18n);
    opts.setCount(countActiveIn());
    notifyNew();
  }

  function countActiveIn() {
    return data.in.filter(c => !c.declined).length;
  }

  function notifyNew() {
    data.in.forEach(c => {
      if (li.once('c-' + c.id)) {
        if (!li.quietMode && data.in.length <= 3) {
          opts.show();
          li.sound.newChallenge();
        }
        const pushSubsribed = parseInt(li.storage.get('push-subscribed') || '0', 10) + 86400000 >= Date.now(); // 24h
        !pushSubsribed && c.challenger && notify(showUser(c.challenger) + ' challenges you!');
        opts.pulse();
      }
    });
  }

  function showUser(user: ChallengeUser) {
    var rating = user.rating + (user.provisional ? '?' : '');
    var fullName = (user.title ? (user.title.endsWith('-64') ? user.title.slice(0, user.title.length - 3) : user.title) + ' ' : '') + user.name;
    return fullName + ' (' + rating + ')';
  }

  update(data);

  return {
    data: () => data,
    trans: () => trans,
    update,
    decline(id) {
      data.in.forEach(c => {
        if (c.id === id) {
          c.declined = true;
          xhr.decline(id);
        }
      });
    },
    cancel(id) {
      data.out.forEach(c => {
        if (c.id === id) {
          c.declined = true;
          xhr.cancel(id);
        }
      });
    },
    redirecting: () => redirecting,
    onRedirect() {
      redirecting = true;
      li.raf(redraw);
    }
  };
};
