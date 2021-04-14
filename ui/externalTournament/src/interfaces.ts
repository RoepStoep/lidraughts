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
  name: string;
  upcoming: Challenge[];
  ongoing: Board[];
  finished: Game[];
  socketVersion?: number;
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
