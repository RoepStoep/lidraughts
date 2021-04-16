import { init } from 'snabbdom';
import { VNode } from 'snabbdom/vnode'
import klass from 'snabbdom/modules/class';
import attributes from 'snabbdom/modules/attributes';
import { Draughtsground } from 'draughtsground';
import { ExternalTournamentOpts } from './interfaces';
import ExternalTournamentCtrl from './ctrl';
import * as chat from 'chat';

const patch = init([klass, attributes]);

import view from './view/main';

export function start(opts: ExternalTournamentOpts) {

  const li = window.lidraughts;
  const element = document.querySelector('main.tour-ext') as HTMLElement;
  li.socket = li.StrongSocket(
    '/tournament/external/' + opts.data.id + '/socket/v3', opts.data.socketVersion || 0, {
      receive: (t: string, d: any) => ctrl.socket.receive(t, d)
    });
  opts.classes = element.getAttribute('class');
  opts.socketSend = li.socket.send;
  opts.element = element;
  opts.$side = $('.tour-ext__side').clone();

  let vnode: VNode;

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  const ctrl = new ExternalTournamentCtrl(opts, redraw);

  const blueprint = view(ctrl);
  element.innerHTML = '';
  vnode = patch(element, blueprint);

  redraw();
};

// that's for the rest of lidraughts to access draughtsground
// without having to include it a second time
window.Draughtsground = Draughtsground;
window.LidraughtsChat = chat;
