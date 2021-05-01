import makeSocket from './socket';
import xhr from './xhr';
import throttle from 'common/throttle';
import { myPage, players } from './pagination';
import { ExternalTournamentData, ExternalTournamentOpts, BasePlayer, Pages, Standing } from './interfaces';
import { ExternalTournamentSocket } from './socket';

export default class ExternalTournamentCtrl {

  opts: ExternalTournamentOpts;
  data: ExternalTournamentData;
  trans: Trans;
  socket: ExternalTournamentSocket;
  page: number;
  pages: Pages = {};
  lastPageDisplayed: number | undefined;
  focusOnMe: boolean;
  joinSpinner: boolean = false;
  playerInfoId?: string;
  searching: boolean = false;
  redraw: () => void;

  private lastStorage = window.lidraughts.storage.make('last-redirect');

  constructor(opts: ExternalTournamentOpts, redraw: () => void) {
    this.opts = opts;
    this.data = opts.data;
    this.redraw = redraw;
    this.trans = window.lidraughts.trans(opts.i18n);
    this.socket = makeSocket(opts.socketSend, this);
    this.page = this.data.standing.page;
    this.focusOnMe = !!this.data.me;
    this.loadPage(this.data.standing);
    this.scrollToMe();
    this.redirectToMyGame();
  }

  reload = (data: ExternalTournamentData): void => {
    if (this.data.chat === 'players' && !!this.data.me?.canJoin !== !!data.me?.canJoin) {
      // reload the page to show/hide players-only chat
      window.lidraughts.reload();
    }
    this.data = {...this.data, ...data};
    this.data.me = data.me; // to account for removal on withdraw
    this.loadPage(this.data.standing);
    if (this.focusOnMe) this.scrollToMe();
    this.joinSpinner = false;
    this.redirectToMyGame();
    this.redrawNbRounds();
  };

  isCreator = () => this.data.me?.userId === this.data.createdBy.id;
  isMe = (user: LightUser) => this.data.me?.userId === user.id;

  myGameId = () => this.data.me?.gameId;

  displayFmjd = () => this.data.userDisplay === 'fmjd';

  answer = (accept: boolean) => () => {
    xhr.answer(this, accept);
    this.joinSpinner = true;
    this.focusOnMe = true;
  }

  private redirectToMyGame() {
    const gameId = this.myGameId();
    if (gameId) this.redirectFirst(gameId);
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

  scrollToMe = () => {
    const page = myPage(this);
    if (page && page !== this.page) this.setPage(page);
  };

  loadPage = (data: Standing) => {
    this.pages[data.page] = this.readStanding(data).players;
  }

  setPage = (page: number) => {
    this.page = page;
    xhr.loadPage(this, page);
  };

  toggleFocusOnMe = () => {
    if (this.data.me) {
      this.focusOnMe = !this.focusOnMe;
      if (this.focusOnMe) this.scrollToMe();
    }
  };

  toggleSearch = () => this.searching = !this.searching;

  jumpToPageOf = (name: string) => {
    const userId = name.toLowerCase();
    xhr.loadPageOf(this, userId).then(data => {
      this.loadPage(data);
      this.page = data.page;
      this.searching = false;
      this.focusOnMe = false;
      this.pages[this.page].filter(p => p.user.id == userId).forEach(this.showPlayerInfo);
      this.redraw();
    });
  }

  userSetPage = (page: number) => {
    this.focusOnMe = false;
    this.setPage(page);
  };

  userNextPage = () => this.userSetPage(this.page + 1);
  userPrevPage = () => this.userSetPage(this.page - 1);
  userLastPage = () => this.userSetPage(players(this).nbPages);

  showPlayerInfo = (player: BasePlayer) => {
    this.playerInfoId = this.playerInfoId === player.user.id ? undefined : player.user.id;
    if (this.playerInfoId) xhr.playerInfo(this, this.playerInfoId);
  };

  private redrawNbRounds = () =>
    this.data.roundsPlayed && $('.tour-ext__meta__round').text(`${this.data.roundsPlayed}/${this.data.nbRounds}`);

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

  private readStanding = (standing: Standing) => ({
    ...standing,
    players: standing.players
  });
}
