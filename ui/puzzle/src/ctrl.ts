import { build as treeBuild, ops as treeOps, path as treePath } from 'tree';
import { ctrl as cevalCtrl, scan2uci, CevalCtrl } from 'ceval';
import { readDests, readCaptureLength, decomposeUci, san2alg } from 'draughts';
import { opposite } from 'draughtsground/util';
import { countGhosts } from 'draughtsground/fen';
import { animationDuration } from 'draughtsground/anim';
import keyboard from './keyboard';
import socketBuild from './socket';
import moveTestBuild from './moveTest';
import mergeSolution from './solution';
import { puzzleUrl } from './util';
import computeAutoShapes from './autoShape';
import { prop } from 'common';
import { storedProp } from 'common/storage';
import throttle from 'common/throttle';
import * as xhr from './xhr';
import * as speech from './speech';
import { sound } from './sound';
import { Api as CgApi } from 'draughtsground/api';
import { Vm, Controller } from './interfaces';

export default function (opts, redraw: () => void): Controller {

  let vm: Vm = {} as Vm;
  let ceval: CevalCtrl
  var data, tree, moveTest;
  const ground = prop<CgApi | undefined>(undefined);
  const threatMode = prop(false);

  // required by ceval
  vm.showComputer = () => vm.mode === 'view';
  vm.showAutoShapes = () => true;

  function setPath(path) {
    vm.path = path;
    vm.nodeList = tree.getNodeList(path);
    vm.node = treeOps.last(vm.nodeList)!;
    vm.mainline = treeOps.mainlineNodeList(tree.root);
  };

  function withGround<A>(f: (cg: CgApi) => A): A | undefined {
    const g = ground();
    if (g) return f(g);
  }

  function initiate(fromData) {
    data = fromData;
    tree = treeBuild(data.game.treeParts ? treeOps.reconstruct(data.game.treeParts, coordSystem()) : ({
      id: "",
      ply: data.history.ply - 1,
      fen: data.puzzle.fen,
      children: [
        {
          id: data.history.id,
          ply: data.history.ply,
          fen: data.history.fen,
          san: data.history.san,
          alg: isAlgebraic() ? san2alg(data.history.san) : undefined,
          uci: data.history.uci,
          tbhit: undefined,
          children: []
        } as Tree.Node
      ]
    } as Tree.Node));
    var initialPath = treePath.fromNodeList(treeOps.mainlineNodeList(tree.root));
    // play | try | view
    vm.mode = 'play';
    vm.loading = false;
    vm.round = undefined;
    vm.voted = undefined;
    vm.justPlayed = undefined;
    vm.resultSent = false;
    vm.lastFeedback = 'init';
    vm.initialPath = initialPath;
    vm.initialNode = tree.nodeAtPath(initialPath);

    setPath(treePath.init(initialPath));
    setTimeout(function () {
      jump(initialPath);
      redraw();
      speech.node(vm.node, false, undefined, isAlgebraic());
    }, 500);

    // just to delay button display
    vm.canViewSolution = false;
    setTimeout(function () {
      vm.canViewSolution = true;
      redraw();
    }, 5000);

    moveTest = moveTestBuild(vm, data.puzzle);

    withGround(function (g) {
      g.setAutoShapes([]);
      g.setShapes([]);
      showGround(g);
    });

    instanciateCeval();

    history.replaceState(null, '', puzzleUrl(data.puzzle.variant.key) + data.puzzle.id);
  };

  var makeCgOpts = function () {
    const node = vm.node;
    const color: Color = node.ply % 2 === 0 ? 'white' : 'black';
    const dests = readDests(node.dests);
    const movable = (vm.mode === 'view' || color === data.puzzle.color) ? {
      color: (dests && Object.keys(dests).length > 0) ? color : null,
      dests: dests || {},
      captLen: readCaptureLength(node.dests),
      captureUci: (opts.pref.fullCapture && node.destsUci && node.destsUci.length) ? node.destsUci : undefined,
      variant: data.puzzle.variant.key
    } : {
        color: null,
        dests: {}
      };
    const config = {
      fen: node.fen,
      orientation: data.puzzle.color,
      turnColor: color,
      movable: movable,
      premovable: {
        enabled: false,
        variant: data.puzzle.variant.key
      },
      boardSize: data.puzzle.variant.board.size,
      coordSystem: coordSystem(),
      lastMove: uciToLastMove(node.uci)
    };
    if (node.ply >= vm.initialNode.ply) {
      if (!dests) {
        // premove while dests are loading from server
        config.turnColor = opposite(color);
        config.movable.color = color;
        config.premovable.enabled = true;
      } else if (vm.mode !== 'view' && color !== data.puzzle.color) {
        config.movable.color = data.puzzle.color;
        config.premovable.enabled = true;
      }
    }
    vm.cgConfig = config;
    return config;
  };

  function showGround(g, noCaptSequences: boolean = false) {
    g.set(makeCgOpts(), noCaptSequences);
    if (!vm.node.dests) getDests();
  };

  function userMove(orig, dest, capture) {
    sound[capture ? 'capture' : 'move']();
    if (opts.pref.fullCapture && vm.node.destsUci) {
      const uci = vm.node.destsUci.find(u => u.slice(0, 2) === orig && u.slice(-2) === dest)
      if (uci) {
        vm.justPlayed = uci.substr(uci.length - 4, 2) as Key;
        sendMove(orig, dest, uci);
        return;
      }
    }
    vm.justPlayed = orig;
    sendMove(orig, dest);
  };

  function sendMove(orig: Key, dest: Key, uci?: string) {
    const move: any = {
      orig: orig,
      dest: dest,
      variant: data.puzzle.variant.key,
      fen: vm.node.fen,
      path: vm.path
    };
    if (uci) move.uci = uci;
    if (opts.pref.fullCapture) move.fullCapture = true;
    socket.sendAnaMove(move);
  };

  function isAlgebraic(): boolean {
    return opts.pref.coordSystem === 1 && data.puzzle.variant.board.key === '64';
  }
  
  function coordSystem(): number {
    return isAlgebraic() ? 1 : 0;
  }

  var getDests = throttle(800, function () {
    if (!vm.node.dests && treePath.contains(vm.path, vm.initialPath) && (vm.node.destreq || 0) < 3) {
      const dests: any = {
        variant: data.puzzle.variant.key,
        fen: vm.node.fen,
        path: vm.path
      };
      if (opts.pref.fullCapture) dests.fullCapture = true;
      socket.sendAnaDests(dests);
      vm.node.destreq = (vm.node.destreq || 0) + 1;
    }
  });

  var uciToLastMove = function (uci) {
    return uci && decomposeUci(uci);
  };

  var addNode = function (node, path) {
    var newPath = tree.addNode(node, path, false, coordSystem());
    if (newPath) { // path can be undefined when solution is clicked in the middle of opponent capt sequence
      const ghosts = countGhosts(node.fen);
      const playedMyself = jump(newPath);
      reorderChildren(path);
      redraw();
      withGround(function (g) { if (!ghosts) g.playPremove(); });

      var progress = moveTest();
      if (progress) applyProgress(progress, ghosts);
      redraw();
      if (ghosts === 0 || playedMyself) {
        speech.node(playedMyself ? node : vm.node, false, undefined, isAlgebraic());
      }
    }
  };

  function reorderChildren(path: Tree.Path, recursive?: boolean) {
    var node = tree.nodeAtPath(path);
    node.children.sort(function (c1, _) {
      if (c1.puzzle === 'fail') return 1;
      if (c1.puzzle === 'retry') return 1;
      if (c1.puzzle === 'good') return -1;
      return 0;
    });
    if (recursive) node.children.forEach(function (child) {
      reorderChildren(path + child.id, true);
    });
  };

  var revertUserMove = function (moveDuration) {
    setTimeout(function () {
      withGround(function (g) { g.cancelPremove(); });
      userJump(treePath.init(vm.path));
      redraw();
    }, Math.max(moveDuration, 500));
  };

  var applyProgress = function (progress, contd) {
    if (progress === 'fail') {
      vm.lastFeedback = 'fail';
      const g = ground();
      revertUserMove(g ? animationDuration(g.state) + 100 : 0);
      if (vm.mode === 'play') {
        vm.canViewSolution = true;
        vm.mode = 'try';
        sendResult(false);
      }
    } else if (progress === 'retry') {
      vm.lastFeedback = 'retry';
      const g = ground();
      revertUserMove(g ? animationDuration(g.state) + 100 : 0);
    } else if (progress === 'win') {
      if (vm.mode !== 'view') {
        if (vm.mode === 'play') sendResult(true);
        vm.lastFeedback = 'win';
        vm.mode = 'view';
        withGround(showGround); // to disable premoves
        startCeval();
      }
    } else if (progress && progress.orig) {
      vm.lastFeedback = 'good';
      const g = ground(),
        duration = g ? animationDuration(g.state) : 300,
        delay = !contd ? Math.max(500, duration) : duration;
      setTimeout(function () {
        if (opts.pref.fullCapture) progress.fullCapture = true;
        socket.sendAnaMove(progress);
      }, Math.max(300, delay));
    }
  };

  function sendResult(win) {
    if (vm.resultSent) return;
    vm.resultSent = true;
    nbToVoteCall(Math.max(0, parseInt(nbToVoteCall()) - 1));
    xhr.round(data.puzzle.id, data.puzzle.variant.key, win).then(function (res) {
      data.user = res.user;
      vm.round = res.round;
      vm.voted = res.voted;
      redraw();
      if (win) speech.success();
    });
  };

  function nextPuzzle() {
    ceval.stop();
    vm.loading = true;
    socket.resetCache();
    redraw();
    xhr.nextPuzzle(data.puzzle.variant.key).done(function (d) {
      vm.round = null;
      vm.loading = false;
      initiate(d);
      redraw();
    });
  };

  function addDests(dests, path, opening, destsUci?: Uci[]) {
    tree.addDests(dests, path, opening, undefined, destsUci);
    if (path === vm.path) {
      withGround(showGround);
      if (gameOver()) {
        ceval.stop();
        redraw();
      }
    }
    withGround(function (g) { g.playPremove(); });
  };

  function instanciateCeval(failsafe: boolean = false) {
    if (ceval) ceval.destroy();
    ceval = cevalCtrl({
      redraw,
      storageKeyPrefix: 'puzzle',
      multiPvDefault: 3,
      variant: data.puzzle.variant,
      possible: true,
      emit: function (ev, work) {
        tree.updateAt(work.path, function (node) {
          if (work.threatMode) {
            if (!node.threat || node.threat.depth <= ev.depth || node.threat.maxDepth < ev.maxDepth)
              node.threat = ev;
          } else if (!node.ceval || node.ceval.depth <= ev.depth || node.ceval.maxDepth < ev.maxDepth)
            node.ceval = ev;
          if (work.path === vm.path) {
            setAutoShapes();
            redraw();
          }
        });
      },
      setAutoShapes: setAutoShapes,
      failsafe: failsafe,
      onCrash: function (e) {
        console.log('Local eval failed!', e);
        if (ceval.pnaclSupported) {
          console.log('Retrying in failsafe mode');
          instanciateCeval(true);
          startCeval();
        }
      }
    });
  };

  function setAutoShapes() {
    withGround(function (g) {
      g.setAutoShapes(computeAutoShapes({
        vm: vm,
        ceval: ceval,
        ground: g,
        threatMode: threatMode(),
        nextNodeBest: nextNodeBest()
      }));
    });
  };

  function canUseCeval() {
    return vm.mode === 'view' && !gameOver();
  };

  function startCeval() {
    if (ceval.enabled() && canUseCeval()) doStartCeval();
  };

  const doStartCeval = throttle(800, function () {
    // only analyze startingposition of multicaptures
    const ghostEnd = vm.nodeList.length && vm.node.displayPly && vm.node.displayPly !== vm.node.ply;
    const path = ghostEnd ? vm.path.slice(2) : vm.path
    const nodeList = ghostEnd ? vm.nodeList.slice(1) : vm.nodeList
    ceval.start(path, nodeList, threatMode(), false, false);
  });

  function nextNodeBest() {
    return treeOps.withMainlineChild(vm.node, function (n) {
      // return n.eval ? n.eval.pvs[0].moves[0] : null;
      return n.eval ? n.eval.best : undefined;
    });
  };

  function playUci(uci) {
    const move = decomposeUci(uci);
    sendMove(move[0], move[move.length - 1], move.length > 2 ? uci : undefined);
  };

  function getCeval() {
    return ceval;
  };

  function toggleCeval() {
    ceval.toggle();
    setAutoShapes();
    startCeval();
    if (!ceval.enabled()) threatMode(false);
    vm.autoScrollRequested = true;
    redraw();
  };

  function toggleThreatMode() {
    if (vm.node.displayPly && vm.node.displayPly !== vm.node.ply) return;
    if (!ceval.enabled()) ceval.toggle();
    if (!ceval.enabled()) return;
    threatMode(!threatMode());
    setAutoShapes();
    startCeval();
    redraw();
  };

  function gameOver() {
    if (vm.node.dests !== '') return false;
    return vm.node.draw ? 'draw' : 'checkmate';
  };

  const playedLastMoveMyself = () =>
    !!vm.justPlayed && !!vm.node.uci && vm.node.uci.substr(vm.node.uci.length - 4, 2) === vm.justPlayed;

  function jump(path, forceSound = false) {
    var pathChanged = path !== vm.path;
    const oldPly = vm.node.displayPly ? vm.node.displayPly : vm.node.ply;
    setPath(path);
    withGround((g) => showGround(g, Math.abs(oldPly - (vm.node.displayPly ? vm.node.displayPly : vm.node.ply)) > 1));
    const playedMyself = playedLastMoveMyself();
    if (pathChanged) {
      if (!vm.node.uci) sound.move(); // initial position
      else if (forceSound || !playedMyself) {
        if (vm.node.san!.includes('x')) sound.capture();
        else sound.move();
      }
      threatMode(false);
      ceval.stop();
      startCeval();
    }
    vm.justPlayed = undefined;
    vm.autoScrollRequested = true;
    window.lidraughts.pubsub.emit('ply', vm.node.ply);
    return playedMyself;
  };

  function userJump(path, forceSound = false) {
    withGround(function (g) {
      g.selectSquare(null);
    });
    jump(path, forceSound);
    const mergedNodes = vm.node.mergedNodes,
      prevSan = playedLastMoveMyself() && mergedNodes && mergedNodes.length > 1 && mergedNodes[mergedNodes.length - 2].san,
      captSan = prevSan ? prevSan.indexOf('x') : -1,
      captKey = (prevSan && captSan !== -1) ? prevSan.slice(captSan + 1) as Key : undefined;
    speech.node(vm.node, true, captKey, isAlgebraic());
  };

  function viewSolution() {
    if (!vm.canViewSolution) return;
    sendResult(false);
    vm.mode = 'view';
    const merged: Tree.Node | undefined = mergeSolution(vm.initialNode, data.puzzle.branch, data.puzzle.color, isAlgebraic());
    reorderChildren(vm.initialPath, true);

    // try and play the solution next move
    var next = vm.node.children[0];
    if (merged) userJump(vm.path.substr(0, vm.path.length - 1) + merged.id.substr(1), true);
    else if (next && next.puzzle === 'good') userJump(vm.path + next.id);
    else {
      var firstGoodPath = treeOps.takePathWhile(vm.mainline, function (node) {
        return node.puzzle !== 'good';
      });
      if (firstGoodPath) userJump(firstGoodPath + tree.nodeAtPath(firstGoodPath).children[0].id);
    }

    vm.autoScrollRequested = true;
    redraw();
    startCeval();
  };

  const socket = socketBuild({
    send: opts.socketSend,
    addNode: addNode,
    addDests: addDests,
    reset: function () {
      withGround(showGround);
      redraw();
    }
  });

  function recentHash(): string {
    return 'ph' + data.puzzle.id + (data.user ? data.user.recent.reduce(function (h, r) {
      return h + r[0];
    }, '') : '');
  }

  const nbToVoteCall = storedProp('puzzle.vote-call', 3);
  let thanksUntil: number | undefined;

  const callToVote = () => parseInt(nbToVoteCall()) < 1;

  const vote = throttle(1000, function (v) {
    if (callToVote()) thanksUntil = Date.now() + 2000;
    nbToVoteCall(5);
    vm.voted = v;
    xhr.vote(data.puzzle.id, data.puzzle.variant.key, v).then(function (res) {
      data.puzzle.vote = res[1];
      redraw();
    });
  });

  initiate(opts.data);

  keyboard({
    vm,
    userJump,
    getCeval,
    toggleCeval,
    toggleThreatMode,
    redraw,
    playBestMove() {
      var uci = nextNodeBest() || (vm.node.ceval && scan2uci(vm.node.ceval.pvs[0].moves[0]));
      if (uci) playUci(uci);
    }
  });

  // If the page loads while being hidden (like when changing settings),
  // draughtsground is not displayed, and the first move is not fully applied.
  // Make sure draughtsground is fully shown when the page goes back to being visible.
  document.addEventListener('visibilitychange', function () {
    window.lidraughts.requestIdleCallback(function () {
      jump(vm.path);
    });
  });

  const getCevalNode = () => (vm.nodeList.length > 1 && vm.node.displayPly && vm.node.displayPly !== vm.node.ply) ? vm.nodeList[vm.nodeList.length - 2] : vm.node;
  
  speech.setup();

  return {
    vm,
    getData() {
      return data;
    },
    getVariantSelect() {
      return (opts.$variantSelect && opts.$variantSelect.length) ? opts.$variantSelect : undefined;
    },
    getTree() {
      return tree;
    },
    ground,
    makeCgOpts,
    userJump,
    viewSolution,
    nextPuzzle,
    recentHash,
    callToVote,
    thanks() {
      return !!thanksUntil && Date.now() < thanksUntil;
    },
    vote,
    getCeval,
    pref: opts.pref,
    trans: window.lidraughts.trans(opts.i18n),
    socketReceive: socket.receive,
    gameOver,
    toggleCeval,
    toggleThreatMode,
    threatMode,
    currentEvals() {
      return { client: getCevalNode().ceval };
    },
    nextNodeBest,
    userMove,
    playUci,
    showEvalGauge() {
      return vm.showComputer() && ceval.enabled();
    },
    getOrientation() {
      return withGround(function (g) { return g.state.orientation })!;
    },
    getNode() {
      return vm.node;
    },
    getCevalNode,
    showComputer: vm.showComputer,
    redraw,
    ongoing: false
  };
}
