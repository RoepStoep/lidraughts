import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'

import { Redraw, defined } from './util'

export interface PingData {
  ping: number | undefined
  server: number | undefined
}

export interface PingCtrl {
  data: PingData
  trans: Trans
}

export function ctrl(trans: Trans, redraw: Redraw): PingCtrl {

  const data: PingData = {
    ping: undefined,
    server: undefined
  };

  const hub = window.lidraughts.pubsub;

  hub.emit('socket.send', 'moveLat', true);
  hub.on('socket.lag', lag => {
    data.ping = Math.round(lag);
    redraw();
  });
  hub.on('socket.in.mlat', lat => {
    data.server = lat as number;
    redraw();
  });

  return { data, trans };
}

function signalBars(d: PingData) {
  const lagRating =
    !d.ping ? 0 :
    (d.ping < 150) ? 4 :
    (d.ping < 300) ? 3 :
    (d.ping < 500) ? 2 : 1;
  const bars = [];
  for (let i = 1; i <= 4; i++) bars.push(h(i <= lagRating ? 'i' : 'i.off'));
  return h('signal.q' + lagRating, bars);
}

export function view(ctrl: PingCtrl): VNode {

  const d = ctrl.data;

  return h('a.status', { attrs: {href: '/lag'} }, [
    signalBars(d),
    h('span.ping', {
      attrs: { title: 'PING: ' + ctrl.trans.noarg('networkLagBetweenYouAndLidraughts') }
    }, [
      h('em', 'PING'),
      h('strong', defined(d.ping) ? '' + d.ping : '?'),
      h('em', 'ms')
    ]),
    h('span.server', {
      attrs: { title: 'SERVER: ' + ctrl.trans.noarg('timeToProcessAMoveOnLidraughtsServer') }
    }, [
      h('em', 'SERVER'),
      h('strong', defined(d.server) ? '' + d.server : '?'),
      h('em', 'ms')
    ])
  ]);
}

