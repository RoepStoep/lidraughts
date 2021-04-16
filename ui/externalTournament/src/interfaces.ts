import { VNode } from 'snabbdom/vnode'

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];
export type Redraw = () => void;

export interface ExternalTournamentOpts {
  data: ExternalTournamentData;
  element: HTMLElement;
  $side: JQuery;
  socketSend: SocketSend;
  chat: any;
  i18n: any;
  classes: string | null;
  draughtsResult: boolean;
}

export interface ExternalTournamentData {
  id: string;
  createdBy: string;
  name: string;
  players: ExternalPlayer[];
  upcoming: Challenge[];
  ongoing: Board[];
  finished: Game[];
  me?: MyInfo;
  playerInfo?: PlayerInfo;
  socketVersion?: number;
}

export interface MyInfo {
  userId: string;
  canJoin?: boolean;
}

export interface PlayerInfo {
  user: LightUser;
  sheet: GameResult[];
}

export interface GameResult extends Player {
  g: string; // game
  w?: boolean; // won
  c: boolean; // color
}

export interface BaseGame {
  id: string;
  variant: VariantData;
  white: Player;
  black: Player;
}

export interface Challenge extends BaseGame {
  startsAt?: string;
}

export interface Game extends BaseGame {
  createdAt: string;
  winner?: Color;
}

export interface ExternalPlayer {
  user: LightUser;
  joined: boolean;
}

export interface Player {
  user: LightUser;
  rating: number;
  provisional?: boolean;
}

export interface Board extends BaseGame {
  fen: string;
  lastMove?: string;
  orientation: Color;
  clock?: {
    white: number;
    black: number;
  }
  winner?: Color;
}

export interface VariantData {
  key: VariantKey
  board: BoardData
}
