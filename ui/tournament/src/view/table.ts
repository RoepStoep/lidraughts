import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { opposite } from 'draughtsground/util';
import { player as renderPlayer, bind, onInsert } from './util';
import { Duel, DuelPlayer, DuelTeams, TeamBattle, FeaturedGame } from '../interfaces';
import { teamName } from './battle';
import TournamentController from '../ctrl';

function featuredPlayer(game: FeaturedGame, color: Color, draughtsResult: boolean) {
  const player = game[color];
  const clock = game.c || game.clock; // temporary BC, remove me
  return h('span.mini-game__player', [
    h('span.mini-game__user', [
    h('strong', '#' + player.rank),
    renderPlayer(player, true, true, false, false),
    player.berserk ? h('i', {
      attrs: {
        'data-icon': '`',
        title: 'Berserk'
      }
    }) : null
  ]),
  clock ? 
    h(`span.mini-game__clock.mini-game__clock--${color}`, {
      attrs: { 'data-time': clock[color] }
    }) : 
    h('span.mini-game__result', game.winner ? 
      (game.winner == color ? (draughtsResult ? '2' : '1') : '0') :
      (draughtsResult ? '1' : '½')
    )
  ]);
}

function featured(game: FeaturedGame, draughtsResult: boolean): VNode {
  const board = game.board;
  return h(`div.tour__featured.mini-game.mini-game-${game.id}.mini-game--init.is2d.is${board.key}`, {
    attrs: {
      'data-state': `${game.fen}|${board.size[0]}x${board.size[1]}|${game.orientation}|${game.lastMove || ''}`,
      'data-live': game.id
    },
    hook: onInsert(window.lidraughts.powertip.manualUserIn)
  }, [
    featuredPlayer(game, opposite(game.orientation), draughtsResult),
    h('a.cg-wrap', {
      attrs: {
        href: `/${game.id}/${game.orientation}`
      }
    }),
    featuredPlayer(game, game.orientation, draughtsResult)
  ]);
}

function duelPlayerMeta(p: DuelPlayer) {
  const title64 = p.t && p.t.endsWith('-64');
  return [
    h('em.rank', '#' + p.k),
    p.t ? h(
      'em.title',
      title64 ? { attrs: {'data-title64': true } } : (p.t == 'BOT' ? { attrs: {'data-bot': true } } : {}),
      title64 ? p.t.slice(0, p.t.length - 3) : p.t
    ) : null,
    h('em.rating', '' + p.r)
  ];
}

function renderDuel(battle?: TeamBattle, duelTeams?: DuelTeams) {
  return (d: Duel) => h('a.glpt', {
    key: d.id,
    attrs: { href: '/' + d.id }
  }, [
    battle && duelTeams ? h('line.t', [0, 1].map(i =>
      teamName(battle, duelTeams[d.p[i].n.toLowerCase()])
    )) : undefined,
    h('line.a', [
      h('strong', d.p[0].n),
      h('span', duelPlayerMeta(d.p[1]).reverse())
    ]),
    h('line.b', [
      h('span', duelPlayerMeta(d.p[0])),
      h('strong', d.p[1].n)
    ])
  ]);
}

const initMiniGame = (node: VNode) => window.lidraughts.miniGame.initAll(node.elm as HTMLElement);

export default function(ctrl: TournamentController): VNode {
  return h('div.tour__table', {
    hook: {
      insert: initMiniGame,
      postpatch: initMiniGame
    }
  }, [
    ctrl.data.featured ? featured(ctrl.data.featured, ctrl.data.draughtsResult) : null,
    ctrl.data.duels.length ? h('section.tour__duels', {
      hook: bind('click', _ => !ctrl.disableClicks)
    }, [
      h('h2', 'Top games')
    ].concat(ctrl.data.duels.map(renderDuel(ctrl.data.teamBattle, ctrl.data.duelTeams)))) : null
  ]);
};
