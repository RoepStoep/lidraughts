import { Ctrl } from '../interfaces';

export default function status(ctrl: Ctrl): string {
  const noarg = ctrl.trans.noarg, d = ctrl.data;
  switch (d.game.status.name) {
    case 'started':
      return noarg('playingRightNow');
    case 'aborted':
      return noarg('gameAborted');
    case 'mate':
      return ''; //noarg('checkmate');
    case 'resign':
      return noarg(d.game.winner == 'white' ? 'blackResigned' : 'whiteResigned');
    case 'stalemate':
      return noarg('stalemate');
    case 'timeout':
      switch (d.game.winner) {
        case 'white':
          return noarg('blackLeftTheGame');
        case 'black':
          return noarg('whiteLeftTheGame');
      }
      return noarg('draw');
    case 'draw':
      return noarg('draw');
    case 'outoftime':
      return noarg('timeOut');
    case 'noStart':
      return (d.game.winner == 'white' ? 'Black' : 'White') + ' didn\'t move';
    case 'cheat':
      return 'Cheat detected';
    case 'variantEnd':
      switch (d.game.variant.key) {
        case 'breakthrough':
          return noarg('promotion');
      }
      return noarg('variantEnding');
    default:
      return d.game.status.name;
  }
}
