import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import { prop, Prop } from 'common';
import AnalyseController from '../ctrl';
import { makeConfig as makeCgConfig } from '../ground';
import { Draughtsground } from 'draughtsground';
import { Redraw, AnalyseData, MaybeVNodes } from '../interfaces';
import { Player } from 'game';
import { renderSan, renderPieces, renderBoard, styleSetting } from 'nvui/draughts';
import { renderSetting } from 'nvui/setting';
import { Notify } from 'nvui/notify';
import { Style } from 'nvui/draughts';
import { commands } from 'nvui/command';
import * as moveView from '../moveView';
import { bind } from '../util';

window.lidraughts.AnalyseNVUI = function(redraw: Redraw) {

  const notify = new Notify(redraw),
    moveStyle = styleSetting(),
    analysisInProgress = prop(false);

  window.lidraughts.pubsub.on('analysis.server.progress', (data: AnalyseData) => {
    if (data.analysis && !data.analysis.partial) notify.set('Server-side analysis complete')
  });

  return {
    render(ctrl: AnalyseController): VNode {
      const d = ctrl.data, style = moveStyle.get();
      if (!ctrl.draughtsground) ctrl.draughtsground = Draughtsground(document.createElement("div"), {
        ...makeCgConfig(ctrl),
        animation: { enabled: false },
        drawable: { enabled: false },
        coordinates: 0
      });
      return h('main.analyse', [
        h('div.nvui', [
          h('h1', 'Textual representation'),
          h('h2', 'Game info'),
          ...(['white', 'black'].map((color: Color) => h('p', [
            color + ' player: ',
            renderPlayer(ctrl, playerByColor(d, color))
          ]))),
          h('p', `${d.game.rated ? 'Rated' : 'Casual'} ${d.game.perf}`),
          d.clock ? h('p', `Clock: ${d.clock.initial / 60} + ${d.clock.increment}`) : null,
          h('h2', 'Moves'),
          h('p.moves', {
            attrs: {
              role : 'log',
              'aria-live': 'off'
            }
          }, renderMainline(ctrl.mainline, ctrl.path, style)),
          h('h2', 'Pieces'),
          h('div.pieces', renderPieces(ctrl.draughtsground.state.pieces)),
          h('h2', 'Current position'),
          h('p.position', {
            attrs: {
              'aria-live' : 'assertive',
              'aria-atomic' : true
            }
          }, renderCurrentNode(ctrl.node, style)),
          h('h2', 'Move form'),
          h('form', {
            hook: {
              insert(vnode) {
                const $form = $(vnode.elm as HTMLFormElement),
                  $input = $form.find('.move').val('').focus();
                $form.submit(onSubmit(ctrl, notify.set, $input));
              }
            }
          }, [
            h('label', [
              'Command input',
              h('input.move.mousetrap', {
                attrs: {
                  name: 'move',
                  'type': 'text',
                  autocomplete: 'off',
                  autofocus: true
                }
              })
            ])
          ]),
          notify.render(),
          // h('h2', 'Actions'),
          // h('div.actions', tableInner(ctrl)),
          h('h2', 'Computer analysis'),
          ...(renderAcpl(ctrl, style) || [requestAnalysisButton(ctrl, analysisInProgress, notify.set)]),
          h('h2', 'Board'),
          h('pre.board', renderBoard(ctrl.draughtsground.state.pieces, ctrl.draughtsground.state.boardSize, ctrl.data.player.color)),
          h('div.content', {
            hook: {
              insert: vnode => {
                $(vnode.elm as HTMLElement).append($('.blind-content').removeClass('none'));
              }
            }
          }),
          h('h2', 'Settings'),
          h('label', [
            'Move notation',
            renderSetting(moveStyle, ctrl.redraw)
          ]),
          h('h2', 'Keyboard shortcuts'),
          h('p', [
            'Use arrow keys to navigate in the game.'
          ]),
          h('h2', 'Commands'),
          h('p', [
            'Type these commands in the command input.', h('br'),
            commands.piece.help, h('br'),
            commands.scan.help, h('br')
          ])
        ])
      ]);
    }
  };
}

function onSubmit(ctrl: AnalyseController, notify: (txt: string) => void, $input: JQuery) {
  return function() {
    const input = $input.val();
    if (input[0] === '/') onCommand(ctrl, notify, input.slice(1));
    else notify('Invalid command');
    return false;
  };
}

function onCommand(ctrl: AnalyseController, notify: (txt: string) => void, c: string) {
  const pieces = ctrl.draughtsground.state.pieces,
    boardSize = ctrl.draughtsground.state.boardSize;
  notify(
    commands.piece.apply(c, pieces) ||
    commands.scan.apply(c, pieces, boardSize, ctrl.data.player.color !== 'white') ||
    `Invalid command: ${c}`
  );
}

const analysisGlyphs = ['?!', '?', '??'];

function renderAcpl(ctrl: AnalyseController, style: Style): MaybeVNodes | undefined {
  const anal = ctrl.data.analysis;
  if (!anal) return undefined;
  const analysisNodes = ctrl.mainline.filter(n => (n.glyphs || []).find(g => analysisGlyphs.includes(g.symbol)));
  const res: Array<VNode> = [];
  ['white', 'black'].forEach((color: Color) => {
    const acpl = anal[color].acpl;
    res.push(h('h3', `${color} player: ${acpl} ACPL`));
    res.push(h('select', {
      hook: bind('change', e => {
        ctrl.jumpToMain(parseInt((e.target as HTMLSelectElement).value));
        ctrl.redraw();
      })
    },
      analysisNodes.filter(n => (n.ply % 2 === 1) === (color === 'white')).map(node =>
        h('option', {
          attrs: {
            value: node.ply,
            selected: node.ply === ctrl.node.ply
          }
        }, [
          moveView.plyToTurn(node.ply),
          renderSan(node.san!, style),
          renderComments(node, style)
        ].join(' '))
      )));
  });
  return res;
}

function requestAnalysisButton(ctrl: AnalyseController, inProgress: Prop<boolean>, notify: (msg: string) => void) {
  if (inProgress()) return h('p', 'Server-side analysis in progress');
  if (ctrl.ongoing || ctrl.synthetic) return undefined;
  return h('button', {
    hook: bind('click', _ =>  {
      $.ajax({
        method: 'post',
        url: `/${ctrl.data.game.id}/request-analysis`,
        success: () => {
          inProgress(true);
          notify('Server-side analysis in progress')
        },
        error: () => notify('Cannot run server-side analysis'),
      });
    })
  }, 'Request a computer analysis');
}

function renderMainline(nodes: Tree.Node[], currentPath: Tree.Path, style: Style) {
  const res: Array<string | VNode> = [];
  let path: Tree.Path = '';
  nodes.forEach(node => {
    if (!node.san || !node.uci) return;
    path += node.id;
    const content: MaybeVNodes = [
      node.ply & 1 ? moveView.plyToTurn(node.ply) + ' ' : null,
      renderSan(node.san, style)
    ];
    res.push(h('move', {
      attrs: { p: path },
      class: { active: path === currentPath }
    }, content));
    res.push(renderComments(node, style));
    res.push(', ');
    if (node.ply % 2 === 0) res.push(h('br'));
  });
  return res;
}

function renderCurrentNode(node: Tree.Node, style: Style): string {
  if (!node.san || !node.uci) return 'Initial position';
  return [
    moveView.plyToTurn(node.ply),
    renderSan(node.san, style),
    renderComments(node, style)
  ].join(' ');
}

function renderComments(node: Tree.Node, style: Style): string {
  if (!node.comments) return '';
  return (node.comments || []).map(c => renderComment(c, style)).join('. ');
}

function renderComment(comment: Tree.Comment, style: Style): string {
  return comment.by === 'lidraughts' ?
    comment.text.replace(/Best move was (.+)\./, (_, san) =>
      'Best move was ' + renderSan(san, style)) :
      comment.text;
}

function renderPlayer(ctrl: AnalyseController, player: Player) {
  return player.ai ? ctrl.trans('aiNameLevelAiLevel', 'Scan', player.ai) : userHtml(ctrl, player);
}

function userHtml(ctrl: AnalyseController, player: Player) {
  const d = ctrl.data,
    user = player.user,
    perf = user ? user.perfs[d.game.perf] : null,
    rating = player.rating ? player.rating : (perf && perf.rating),
    rd = player.ratingDiff,
    ratingDiff = rd ? (rd > 0 ? '+' + rd : ( rd < 0 ? '−' + (-rd) : '')) : '';
  return user ? h('span', [
    h('a', {
      attrs: { href: '/@/' + user.username }
    }, user.title ? `${user.title.endsWith('-64') ? user.title.slice(0, user.title.length - 3) : user.title} ${user.username}` : user.username),
    rating ? ` ${rating}` : ``,
    ' ' + ratingDiff,
  ]) : 'Anonymous';
}

function playerByColor(d: AnalyseData, color: Color) {
  return color === d.player.color ? d.player : d.opponent;
}
