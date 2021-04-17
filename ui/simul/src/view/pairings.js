const m = require('mithril');
const ceval = require('./ceval');
const util = require('draughtsground/util');

function miniPairing(ctrl) {
  return function(pairing) {
    const game = pairing.game,
      player = pairing.player,
      title64 = player.title && player.title.endsWith('-64');
    return m(`span.mini-game.mini-game-${game.id}.is2d.is${game.board.key}`, {
      class: (ctrl.data.host.gameId === game.id ? 'host ' : '') + (ctrl.evals !== undefined ? 'gauge_displayed' : ''),
      'data-state': `${game.fen}|${game.board.size[0]}x${game.board.size[1]}|${game.orient}|${game.lastMove || ''}`,
      'data-live': game.clock ? game.id : '',
      config(el, isUpdate) {
        if (!isUpdate) {
          window.lidraughts.miniGame.init(el);
          window.lidraughts.powertip.manualUserIn(el);
        }
      }
    }, [
      m('span.mini-game__player', [
        m('a.mini-game__user.ulpt', {
          href: `/@/${player.name}`
        }, [
          m('span.name', 
            !player.title ? [player.name] : [
              m('span.title', 
                title64 ? { 'data-title64': true } : (player.title == 'BOT' ? { 'data-bot': true } : {}),
                title64 ? player.title.slice(0, player.title.length - 3) : player.title
              ),
              ' ',
              player.name
            ]
          ),
          ' ',
          m('span.rating', player.rating)
        ]),
        game.clock ?
          m(`span.mini-game__clock.mini-game__clock--${util.opposite(game.orient)}`, {
            'data-time': game.clock[util.opposite(game.orient)],
            'data-managed': 1
          }) :
          m('span.mini-game__result', game.winner ?
            (game.winner == game.orient ? '0' : (ctrl.pref.draughtsResult ? '2' : '1')) : 
            (ctrl.pref.draughtsResult ? '1' : '½')
          )
      ]),
      m('a.cg-wrap', {
        href: `/${game.id}/${game.orient}`
      }),
      m('span.mini-game__player', [
        m('span'),
        game.clock ?
        m(`span.mini-game__clock.mini-game__clock--${game.orient}`, {
          'data-time': game.clock[game.orient],
          'data-managed': 1
        }) :
        m('span.mini-game__result', game.winner ?
          (game.winner == game.orient ? (ctrl.pref.draughtsResult ? '2' : '1') : '0') : 
          (ctrl.pref.draughtsResult ? '1' : '½')
        )
      ]),
      ctrl.evals !== undefined ? ceval.renderGauge(pairing, ctrl.evals) : null
    ]);
  };
}

module.exports = function(ctrl) {
  return m('div.game-list.now-playing.box__pad', ctrl.data.pairings.map(miniPairing(ctrl)));
};
