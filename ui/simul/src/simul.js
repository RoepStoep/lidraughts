var status = require('game/status');

function applicantsContainMe(ctrl) {
  return ctrl.data.applicants.filter(function(a) {
    return a.player.id === ctrl.userId;
  }).length > 0
}

function pairingsContainMe(ctrl) {
  return ctrl.data.pairings.filter(function(a) {
    return a.player.id === ctrl.userId;
  }).length > 0
}

module.exports = {
  createdByMe: function(ctrl) {
    return ctrl.userId && ctrl.userId === ctrl.data.host.id;
  },
  amArbiter: function(ctrl) {
    return ctrl.data.arbiter && ctrl.userId && ctrl.userId === ctrl.data.arbiter.id;
  },
  containsMe: function(ctrl) {
    return ctrl.userId && (applicantsContainMe(ctrl) || pairingsContainMe(ctrl));
  },
  candidates: function(ctrl) {
    return ctrl.data.applicants.filter(function(a) {
      return !a.accepted;
    });
  },
  accepted: function(ctrl) {
    return ctrl.data.applicants.filter(function(a) {
      return a.accepted;
    });
  },
  allowed: function(ctrl) {
    return !ctrl.data.allowed ? [] : ctrl.data.allowed.filter(function(a) {
      return !ctrl.data.applicants.find(function (p) { return p.accepted && p.player.id === a.id });
    });
  },
  acceptedContainsMe: function(ctrl) {
    return ctrl.data.applicants.filter(function(a) {
      return a.accepted && a.player.id === ctrl.userId;
    }).length > 0
  },
  myCurrentPairing: function(ctrl) {
    if (!ctrl.userId) return null;
    return ctrl.data.pairings.find(function(p) {
      return p.game.status < status.ids.mate && p.player.id === ctrl.userId;
    });
  }
};
