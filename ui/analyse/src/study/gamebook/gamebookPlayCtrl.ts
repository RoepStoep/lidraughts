import AnalyseCtrl from '../../ctrl';
import { path as treePath, ops as treeOps } from 'tree';
import { makeShapesFromUci } from '../../autoShape';
import { fenCompare } from 'draughts'
import { animationDuration } from 'draughtsground/anim';

type Feedback = 'play' | 'good' | 'bad' | 'end';

export interface State {
  feedback: Feedback;
  comment?: string;
  hint?: string;
  showHint: boolean;
  init: boolean; // on root path
}

export default class GamebookPlayCtrl {

  state: State;

  constructor(
    readonly root: AnalyseCtrl,
    readonly chapterId: string,
    readonly trans: Trans,
    readonly redraw: () => void) {

    // ensure all original nodes have a gamebook entry,
    // so we can differentiate original nodes from user-made ones
    treeOps.updateAll(root.tree.root, n => {
      n.gamebook = n.gamebook || {};
      if (n.shapes) n.gamebook.shapes = n.shapes.slice(0);
    });

    this.makeState();
  }

  private makeState = (): void => {
    const node = this.root.node,
    nodeComment = (node.comments || [])[0],
    state: Partial<State> = {
      init: this.root.path === '',
      comment: nodeComment ? nodeComment.text : undefined,
      showHint: false,
    },
    parPath = treePath.init(this.root.path),
    parNode = this.root.tree.nodeAtPath(parPath);
    if (!this.root.onMainline && !this.root.tree.pathIsMainline(parPath)) return;
    if (this.root.onMainline && !node.children[0]) {
      state.feedback = 'end';
    } else if (this.isMyMove()) {
      state.feedback = 'play';
      state.hint = (node.gamebook || {}).hint;
    } else if (this.root.onMainline) {
      state.feedback = 'good';
    } else {
      state.feedback = 'bad';
      if (!state.comment) {
        state.comment = parNode.children[0].gamebook!.deviation;
      }
    }
    this.state = state as State;
    if (!state.comment) {
      const delay = 300 + (this.root.draughtsground ? animationDuration(this.root.draughtsground.state) : 0);
      if (state.feedback === 'good') setTimeout(this.next, Math.max(delay, this.root.path ? 1000 : 300));
      else if (state.feedback === 'bad') setTimeout(this.retry, Math.max(delay, 800));
    }
  }

  isMyMove = () => this.root.turnColor() === this.root.data.orientation;

  retry = () => {
    let path = this.root.path;
    while (path && !this.root.tree.pathIsMainline(path)) path = treePath.init(path);
    this.root.userJump(path);
    if (this.root.embed && this.root.study)
      this.root.study.onJump();
    this.redraw();
  }

  next = () => {
    if (!this.isMyMove()) {
      const child = this.root.node.children[0];
      if (child) this.root.userJump(this.root.path + child.id);
    }
    this.redraw();
  }

  nextUci = () => {
    const child = this.root.node.children[0];
    return child ? child.uci : "";
  }

  peekChild = () => {
    return this.root.node.children.length ? this.root.node.children[0] : undefined;
  }

  peekNextChild = () => {
    const child = this.root.node.children[0];
    return (child && child.children.length) ? child.children[0] : undefined;
  }

  onSpace = () => {
    switch (this.state.feedback) {
      case 'bad':
        this.retry();
        break;
      case 'end':
        const s = this.root.study!,
          c = s.nextChapter();
        if (c) s.setChapter(c.id);
        break;
      default:
        this.next();
    }
  }

  onPremoveSet = () => {
    if (this.state.feedback === 'bad') this.retry();
  }

  hint = () => {
    if (this.state.hint) this.state.showHint = !this.state.showHint;
  }

  solution = () => {
    this.root.draughtsground.setShapes(
      makeShapesFromUci(this.root.node.children[0].uci!, 'green'));
  }

  canJumpTo = (path: Tree.Path) => treePath.contains(this.root.path, path);

  onJump = () => {
    this.makeState();
    // wait for the root ctrl to make the move
    setTimeout(() => this.root.withCg(cg => cg.playPremove()), 100);
  }

  tryJump = (uci: string, fen: string) => {
    const parPath = treePath.init(this.root.path),
      parNode = this.root.tree.nodeAtPath(parPath);
    const node = uci.length > 4 ? parNode : this.root.node;
    for (const child of node.children) {
      if (fenCompare(child.fen, fen))
        return child;
    }
    this.state = {
      init: this.root.path === '',
      comment: node.children[0].gamebook!.deviation,
      showHint: false,
      feedback: 'bad'
    };
    if (!this.state.comment)
      setTimeout(this.retry, 800);
    return undefined;
  }

  onShapeChange = shapes => {
    const node = this.root.node;
    if (node.gamebook && node.gamebook.shapes && !shapes.length) {
      node.shapes = node.gamebook.shapes.slice(0);
      this.root.jump(this.root.path);
    }
  };
}
