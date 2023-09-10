import AnalyseCtrl from './ctrl';
import { h } from 'snabbdom'
import { initialFen } from 'draughts';
import { MaybeVNodes } from './interfaces';
import { ops as treeOps } from 'tree';
import { toggleCoordinates } from 'draughtsground/fen';
import { san2alg } from 'draughts';

interface PdnNode {
  ply: Ply;
  displayPly?: Ply;
  san?: San;
}

type Move = {
  index: number;
  white: San | null;
  black: San | null;
}

function renderNodesTxt(nodes: PdnNode[], algebraic: boolean): string {
  if (!nodes[0]) return '';
  if (!nodes[0].san) nodes = nodes.slice(1);
  if (!nodes[0]) return '';
  var s = nodes[0].ply % 2 === 1 ? '' : Math.floor((nodes[0].ply + 1) / 2) + '... ';
  nodes.forEach(function (node, i) {
    if (node.ply === 0) return;
    if (node.ply % 2 === 1) s += ((node.ply + 1) / 2) + '. '
    s += (algebraic ? san2alg(node.san) : node.san) + ((i + 9) % 8 === 0 ? '\n' : ' ');
  });
  return s.trim();
}

export function renderFullTxt(ctrl: AnalyseCtrl, fromNode?: boolean): string {
  var g = ctrl.data.game;
  var txt = renderNodesTxt(fromNode ? treeOps.mainlineNodeList(ctrl.node) : ctrl.tree.getNodeList(ctrl.path), ctrl.isAlgebraic());
  var tags: Array<[string, string]> = [];
  if (g.variant.key !== 'standard' && g.variant.key !== 'fromPosition') {
    tags.push(g.variant.gameType ? ['GameType', g.variant.gameType] : ['Variant', g.variant.name]);
  }
  if (fromNode) {
    const fen = ctrl.tree.nodeAtPath(ctrl.path.slice(0, -2)).fen;
    tags.push(['FEN', toggleCoordinates(fen, ctrl.isAlgebraic())]);
  } else if (g.initialFen && g.initialFen !== initialFen)
    tags.push(['FEN', toggleCoordinates(g.initialFen, ctrl.isAlgebraic())]);
  if (tags.length)
    txt = tags.map(function (t) {
      return '[' + t[0] + ' "' + t[1] + '"]';
    }).join('\n') + '\n\n' + txt;
  return txt;
}

function groupMoves(nodes: PdnNode[]): Move[] {
  const moves: Move[] = []
  const startPly = nodes[0].displayPly || nodes[0].ply

  let lastIndex = -1
  if (startPly % 2 === 0) {
    // black is the first move
    lastIndex = Math.floor((startPly + 1) / 2)
    moves.push({
      index: lastIndex,
      black: nodes[0].san!,
      white: null,
    })
    nodes = nodes.slice(1)
  }

  nodes.forEach(node => {
    const dply = node.displayPly || node.ply
    if (dply === 0 || !node.san) return

    const cindex = (dply + 1) / 2
    if (cindex !== lastIndex && dply % 2 === 1) {
      moves.push({
        index: cindex,
        white: node.san,
        black: null,
      })
      lastIndex = cindex
    } else {
      const curMove = moves[moves.length - 1]
      if (dply % 2 === 1) {
        if (node.san.includes('x')) {
          curMove.white += node.san.slice(node.san.indexOf('x'))
        } else {
          curMove.white += ` ${node.san}`
        }
      } else {
        if (!curMove.black) {
          curMove.black = node.san  
        } else if (node.san.includes('x')) {
          curMove.black += node.san.slice(node.san.indexOf('x'))
        } else {
          curMove.black += ` ${node.san}`
        }
      }
    }
  })

  return moves
}

export function renderNodesHtml(nodes: PdnNode[], algebraic: boolean): MaybeVNodes {
  if (!nodes[0]) return []
  if (!nodes[0].san) nodes = nodes.slice(1)
  if (!nodes[0]) return []

  const tags: MaybeVNodes = []
  groupMoves(nodes).forEach(({ black, white, index }) => {
    tags.push(h('index', index + (white ? '.' : '...')))
    if (white) tags.push(h('san', algebraic ? san2alg(white) : white))
    if (black) tags.push(h('san', algebraic ? san2alg(black) : black))
  })
  return tags
}
