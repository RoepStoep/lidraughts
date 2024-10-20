import { Prop } from 'common';
import { NotifCtrl } from './notif';
import { AnalyseData, Redraw } from '../interfaces';
import { StudyPracticeCtrl } from './practice/interfaces';
import { StudyChaptersCtrl } from './studyChapters';
import { DescriptionCtrl } from './description';
import GamebookPlayCtrl from './gamebook/gamebookPlayCtrl';
import { GamebookOverride } from './gamebook/interfaces';
import { GlyphCtrl } from './studyGlyph';
import { CommentForm } from './commentForm';
import RelayCtrl from './relay/relayCtrl';
import { ServerEvalCtrl } from './serverEval';
import { MultiBoardCtrl, MultiBoardMenuCtrl } from './multiBoard';

export interface StudyCtrl {
  data: StudyData;
  currentChapter(): StudyChapterMeta;
  socketHandler(t: string, d: any): boolean;
  vm: StudyVm;
  relay?: RelayCtrl;
  multiBoard: MultiBoardCtrl;
  multiBoardMenu?: MultiBoardMenuCtrl;
  form: any;
  members: any;
  chapters: StudyChaptersCtrl;
  notif: NotifCtrl;
  commentForm: CommentForm;
  glyphForm: GlyphCtrl;
  serverEval: ServerEvalCtrl;
  share: any;
  tags: any;
  studyDesc: DescriptionCtrl;
  chapterDesc: DescriptionCtrl;
  toggleLike(): void;
  position(): Position;
  isChapterOwner(): boolean;
  canJumpTo(path: Tree.Path): boolean;
  onJump(): void;
  withPosition(obj: any): any;
  setPath(path: Tree.Path, node: Tree.Node, playedMyself: boolean): void;
  deleteNode(path: Tree.Path): void;
  promote(path: Tree.Path, toMainline: boolean): void;
  setChapter(id: string, force?: boolean, path?: string): void;
  toggleSticky(): void;
  toggleWrite(): void;
  isWriting(): boolean;
  makeChange(t: string, d: any): boolean;
  startTour(): void;
  userJump(path: Tree.Path): void;
  currentNode(): Tree.Node;
  practice?: StudyPracticeCtrl;
  gamebookPlay(): GamebookPlayCtrl | undefined;
  nextChapter(): StudyChapterMeta | undefined;
  mutateCgConfig(config: any): void;
  isUpdatedRecently(): boolean;
  isInternalRelay(): boolean;
  setGamebookOverride(o: GamebookOverride): void;
  explorerGame(gameId: string, insert: boolean): void;
  onPremoveSet(): void;
  redraw: Redraw;
  trans: Trans;
  sri: string;
}

export type Tab = 'intro' | 'members' | 'chapters';
export type ToolTab = 'tags' | 'comments' | 'glyphs' | 'serverEval' | 'share' | 'multiBoard';

export interface StudyVm {
  loading: boolean;
  nextChapterId?: string;
  justSetChapterId?: string;
  tab: Prop<Tab>;
  toolTab: Prop<ToolTab>;
  chapterId: string;
  mode: {
    sticky: boolean;
    write: boolean;
  };
  behind: number;
  updatedAt: number;
  gamebookOverride: GamebookOverride;
}

export interface StudyData {
  id: string;
  name: string;
  members: StudyMemberMap;
  position: Position;
  ownerId: string;
  settings: StudySettings;
  visibility: 'public' | 'unlisted' | 'private';
  createdAt: number;
  from: string;
  likes: number;
  isNew?: boolean
  liked: boolean;
  features: StudyFeatures;
  chapters: StudyChapterMeta[]
  chapter: StudyChapter;
  secondsSinceUpdate: number;
  description?: string;
}

type UserSelection = 'nobody' | 'owner' | 'contributor' | 'member' | 'everyone';

export interface StudySettings {
  computer: UserSelection;
  explorer: UserSelection;
  cloneable: UserSelection;
  chat: UserSelection;
  sticky: Boolean;
  description: Boolean;
}

export interface ReloadData {
  analysis: AnalyseData;
  study: StudyData;
}

interface Position {
  chapterId: string;
  path: Tree.Path;
}

export interface StudyFeatures {
  cloneable: boolean;
  chat: boolean;
  sticky: boolean;
}

export interface StudyChapterMeta {
  id: string;
  name: string;
}

export interface StudyChapterConfig extends StudyChapterMeta {
  orientation: Color;
  description?: string;
  practice: boolean;
  gamebook: boolean;
  conceal?: number;
}

export interface StudyChapter {
  id: string;
  name: string;
  ownerId: string;
  setup: StudyChapterSetup;
  tags: TagArray[]
  practice: boolean;
  conceal?: number;
  gamebook: boolean;
  features: StudyChapterFeatures;
  description?: string;
  relay?: StudyChapterRelay;
}

export interface StudyChapterRelay {
  path: Tree.Path;
  secondsSinceLastMove?: number;
  lastMoveAt?: number;
  runningClock?: number;
}

interface StudyChapterSetup {
  gameId?: string;
  variant: {
    key: string;
    name: string;
  };
  orientation: Color;
  fromFen?: string;
}

interface StudyChapterFeatures {
  computer: boolean;
  explorer: boolean;
}

export type StudyMember = any;

export interface StudyMemberMap {
  [id: string]: StudyMember;
}

export type TagTypes = string[];
export type TagArray = [string, string];

export interface LocalPaths {
  [chapterId: string]: Tree.Path;
}

export interface ChapterPreview {
  id: string
  name: string
  players?: {
    white: ChapterPreviewPlayer
    black: ChapterPreviewPlayer
  }
  orientation: Color
  fen: string
  lastMove?: string
  result?: string
  board: BoardData
}

export interface ChapterPreviewPlayer {
  name: string
  title?: string
  rating?: number
}
