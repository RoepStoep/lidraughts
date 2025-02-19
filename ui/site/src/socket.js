function makeAckable(send) {

  var currentId = 1; // increment with each ackable message sent

  var messages = [];

  function resend() {
    var resendCutoff = performance.now() - 2500;
    messages.forEach(function(m) {
      if (m.at < resendCutoff) send(m.t, m.d);
    });
  }

  setInterval(resend, 1000);

  return {
    resend: resend,
    register: function(t, d) {
      d.a = currentId++;
      messages.push({
        t: t,
        d: d,
        at: performance.now()
      });
    },
    gotAck: function(id) {
      messages = messages.filter(function(m) {
        return m.d.a !== id;
      });
    }
  };
}

// versioned events, acks, retries, resync
lidraughts.StrongSocket = function(url, version, settings) {

  var settings = $.extend(true, {}, lidraughts.StrongSocket.defaults, settings);
  var options = settings.options;
  var ws;
  var pingSchedule;
  var connectSchedule;
  var ackable = makeAckable(function(t, d) { send(t, d) });
  var lastPingTime = performance.now();
  var pongCount = 0;
  var averageLag = 0;
  var tryOtherUrl = false;
  var autoReconnect = true;
  var nbConnects = 0;
  var storage = lidraughts.storage.make('surl7');

  var connect = function() {
    destroy();
    autoReconnect = true;
    var params = $.param(settings.params);
    if (version !== false) params += (params ? '&' : '') + 'v=' + version;
    var fullUrl = options.protocol + '//' + baseUrl() + url + '?' + params;
    debug("connection attempt to " + fullUrl);
    try {
      ws = new WebSocket(fullUrl);
      ws.onerror = function(e) {
        onError(e);
      };
      ws.onclose = function() {
        if (autoReconnect) {
          debug('Will autoreconnect in ' + options.autoReconnectDelay);
          scheduleConnect(options.autoReconnectDelay);
        }
      };
      ws.onopen = function() {
        debug("connected to " + fullUrl);
        onSuccess();
        $('body').removeClass('offline').addClass('online').addClass(nbConnects > 1 ? 'reconnected' : '');
        pingNow();
        lidraughts.pubsub.emit('socket.open');
        ackable.resend();
      };
      ws.onmessage = function(e) {
        if (e.data == 0) return pong();
        var m = JSON.parse(e.data);
        // if (Math.random() > 0.5) {
        //   console.log(m, 'skip');
        //   return;
        // }
        if (m.t === 'n') pong();
        // else debug(e.data);
        if (m.t === 'b') m.d.forEach(handle);
        else handle(m);
      };
    } catch (e) {
      onError(e);
    }
    scheduleConnect(options.pingMaxLag);
  };

  var send = function(t, d, o, noRetry) {
    o = o || {};
    var msg = { t: t };
    if (d !== undefined) {
      if (o.withLag) d.l = Math.round(averageLag);
      if (o.millis >= 0) d.s = Math.round(o.millis * 0.1).toString(36);
      msg.d = d;
    }
    if (o.ackable) {
      msg.d = msg.d || {}; // can't ack message without data
      ackable.register(t, msg.d); // adds d.a, the ack ID we expect to get back
    }
    var message = JSON.stringify(msg);
    debug("send " + message);
    try {
      ws.send(message);
    } catch (e) {
      // maybe sent before socket opens,
      // try again a second later.
      if (!noRetry) setTimeout(function() {
        send(t, msg.d, o, true);
      }, 1000);
    }
  };
  lidraughts.pubsub.on('socket.send', send);

  var scheduleConnect = function(delay) {
    if (options.idle) delay = 10 * 1000 + Math.random() * 10 * 1000;
    // debug('schedule connect ' + delay);
    clearTimeout(pingSchedule);
    clearTimeout(connectSchedule);
    connectSchedule = setTimeout(function() {
      $('body').addClass('offline').removeClass('online');
      tryOtherUrl = true;
      connect();
    }, delay);
  };

  var schedulePing = function(delay) {
    clearTimeout(pingSchedule);
    pingSchedule = setTimeout(pingNow, delay);
  };

  var pingNow = function() {
    clearTimeout(pingSchedule);
    clearTimeout(connectSchedule);
    var pingData = (options.isAuth && pongCount % 8 == 2) ? JSON.stringify({
      t: 'p',
      l: Math.round(0.1 * averageLag)
    }) : null;
    try {
      ws.send(pingData);
      lastPingTime = performance.now();
    } catch (e) {
      debug(e, true);
    }
    scheduleConnect(options.pingMaxLag);
  };

  var computePingDelay = function() {
    return options.pingDelay + (options.idle ? 1000 : 0);
  };

  var pong = function() {
    clearTimeout(connectSchedule);
    schedulePing(computePingDelay());
    var currentLag = Math.min(performance.now() - lastPingTime, 10000);
    pongCount++;

    // Average first 4 pings, then switch to decaying average.
    var mix = pongCount > 4 ? 0.1 : 1 / pongCount;
    averageLag += mix * (currentLag - averageLag);

    lidraughts.pubsub.emit('socket.lag', averageLag);
  };

  var handle = function(m) {
    if (m.v) {
      if (m.v <= version) {
        debug("already has event " + m.v);
        return;
      }
      // it's impossible but according to previous login, it happens nonetheless
      if (m.v > version + 1) return lidraughts.reload();
      version = m.v;
    }
    switch (m.t || false) {
      case false:
        break;
      case 'resync':
        lidraughts.reload();
        break;
      case 'simultv':
        var user = location.href.indexOf('/@/'), tv = location.href.lastIndexOf('/tv');
        if (m.d && tv !== -1 && user !== -1 && location.href.substr(tv + 4, 8) !== m.d) {
          setTimeout(function() { // timeout so the viewer can see the move just played
            if (!lidraughts.redirectInProgress) {
              lidraughts.redirect(location.href.slice(user, tv + 3) + '/' + m.d);
            }
          }, 300);
        }
        break;
      case 'ack':
        ackable.gotAck(m.d);
        break;
      default:
        lidraughts.pubsub.emit('socket.in.' + m.t, m.d);
        var processed = settings.receive && settings.receive(m.t, m.d);
        if (!processed && settings.events[m.t]) settings.events[m.t](m.d || null, m);
    }
  };

  var debug = function(msg, always) {
    if (always || options.debug) {
      console.debug("[" + options.name + " " + settings.params.sri + "]", msg);
    }
  };

  var destroy = function() {
    clearTimeout(pingSchedule);
    clearTimeout(connectSchedule);
    disconnect();
    ws = null;
  };

  var disconnect = function(onNextConnect) {
    if (ws) {
      debug("Disconnect");
      autoReconnect = false;
      ws.onerror = ws.onclose = ws.onopen = ws.onmessage = $.noop;
      ws.close();
    }
    if (onNextConnect) options.onNextConnect = onNextConnect;
  };

  var onError = function(e) {
    options.debug = true;
    debug('error: ' + JSON.stringify(e));
    tryOtherUrl = true;
    clearTimeout(pingSchedule);
  };

  var onSuccess = function() {
    nbConnects++;
    if (nbConnects == 1) {
      resolveFirstConnect(send);
      var disconnectTimeout;
      lidraughts.idleTimer(10 * 60 * 1000, function() {
        options.idle = true;
        disconnectTimeout = setTimeout(lidraughts.socket.destroy, 2 * 60 * 60 * 1000);
      }, function() {
        options.idle = false;
        if (ws) clearTimeout(disconnectTimeout);
        else location.reload();
      });
    }
    if (options.onNextConnect) {
      options.onNextConnect();
      delete options.onNextConnect;
    }
  };

  var baseUrl = function() {
    var urls = options.baseUrls, url = storage.get();
    if (!url) {
      url = urls[0];
      storage.set(url);
    } else if (tryOtherUrl) {
      tryOtherUrl = false;
      url = urls[(urls.indexOf(url) + 1) % urls.length];
      storage.set(url);
    }
    return url;
  };

  connect();
  window.addEventListener('unload', destroy);

  return {
    connect: connect,
    disconnect: disconnect,
    send: send,
    destroy: destroy,
    options: options,
    pingInterval: function() {
      return computePingDelay() + averageLag;
    },
    averageLag: function() {
      return averageLag;
    },
    getVersion: function() {
      return version;
    }
  };
};

try {
  const data = window.crypto.getRandomValues(new Uint8Array(9));
  lidraughts.StrongSocket.sri = btoa(String.fromCharCode(...data)).replace(/[/+]/g, '_');
} catch(_) {
  lidraughts.StrongSocket.sri = Math.random().toString(36).slice(2, 12);
}

lidraughts.StrongSocket.defaults = {
  events: {
    fen(e) {
      $('.mini-game-' + e.id).each(function() {
        lidraughts.miniGame.update(this, e);
      });
    },
    finish(e) {
      $('.mini-game-' + e.id).each(function() {
        lidraughts.miniGame.finish(this, e.win);
      });
      lidraughts.pubsub.emit('game.finish', e);
    },
    challenges(d) {
      lidraughts.challengeApp.update(d);
    },
    notifications(d) {
      lidraughts.notifyApp.update(d, true);
    }
  },
  params: {
    sri: lidraughts.StrongSocket.sri
  },
  options: {
    name: "unnamed",
    idle: false,
    pingMaxLag: 9000, // time to wait for pong before reseting the connection
    pingDelay: 2500, // time between pong and ping
    autoReconnectDelay: 3500,
    protocol: location.protocol === 'https:' ? 'wss:' : 'ws:',
    baseUrls: (function(d) {
      return [d];
    })(document.body.getAttribute('data-socket-domain'))
  }
};

let resolveFirstConnect;
lidraughts.StrongSocket.firstConnect = new Promise(r => {
  resolveFirstConnect = r;
});
