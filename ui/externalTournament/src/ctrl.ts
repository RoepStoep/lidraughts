import makeSocket from './socket';
import xhr from './xhr';
import throttle from 'common/throttle';
import { ExternalTournamentData, ExternalTournamentOpts, ExternalPlayer, PlayerInfo } from './interfaces';
import { ExternalTournamentSocket } from './socket';

export default class ExternalTournamentCtrl {

  opts: ExternalTournamentOpts;
  data: ExternalTournamentData;
  trans: Trans;
  socket: ExternalTournamentSocket;
  joinSpinner: boolean = false;
  playerInfoId?: string;
  redraw: () => void;

  private lastStorage = window.lidraughts.storage.make('last-redirect');

  constructor(opts: ExternalTournamentOpts, redraw: () => void) {
    this.opts = opts;
    this.data = opts.data;
    this.redraw = redraw;
    this.trans = window.lidraughts.trans(opts.i18n);
    this.socket = makeSocket(opts.socketSend, this);
  }

  isCreator = () => this.data.me?.userId === this.data.createdBy;
  isMe = (user: LightUser) => this.data.me?.userId === user.id;

  answer = (accept: boolean) => () => {
    xhr.answer(this, accept);
    this.joinSpinner = true;
  }

  showPlayerInfo = (player: ExternalPlayer | PlayerInfo) => {
    this.playerInfoId = this.playerInfoId === player.user.id ? undefined : player.user.id;
    if (this.playerInfoId) xhr.playerInfo(this, this.playerInfoId);
  };

  reload = (data: ExternalTournamentData): void => {
    this.data = {...this.data, ...data};
    this.joinSpinner = false;
  };

  askReload = () => {
    this.reloadSoon();
  }

  private reloadSoonThrottle: () => void;

  private reloadSoon = () => {
    if (!this.reloadSoonThrottle) this.reloadSoonThrottle = throttle(
      2000,
      () => xhr.reloadNow(this)
    );
    this.reloadSoonThrottle();
  }

  redirectFirst = (gameId: string, rightNow?: boolean) => {
    const delay = (rightNow || document.hasFocus()) ? 10 : (1000 + Math.random() * 500);
    setTimeout(() => {
      if (this.lastStorage.get() !== gameId) {
        this.lastStorage.set(gameId);
        window.lidraughts.redirect('/' + gameId);
      }
    }, delay);
  };
}
