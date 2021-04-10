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
}

export interface ExternalTournamentData {
  id: string;
  name: string;
  upcoming: ChallengeData[];
  finished: GameData[];
  socketVersion?: number;
}

export interface ChallengeData {
  id: string;
  startsAt?: string;
  whitePlayer: string;
  blackPlayer: string;
}

export interface GameData {
  id: string;
  createdAt: string;
  whitePlayer: string;
  blackPlayer: string;
}