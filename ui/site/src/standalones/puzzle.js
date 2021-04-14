const el = document.querySelector('#daily-puzzle');
const board = el.querySelector('.mini-board');
board.target = '_blank';
board.innerHTML = '<div class="cg-wrap">';
const [fen, boardSize, orientation, lm] = board.getAttribute('data-state').split('|');
Draughtsground(board.firstChild, {
  coordinates: 0,
  boardSize: boardSize ? boardSize.split('x').map(s => parseInt(s)) : [10, 10],
  resizable: false,
  drawable: { enabled: false, visible: false },
  viewOnly: true,
  fen: fen,
  lastMove: lm && [lm.slice(-4, -2), lm.slice(-2)],
  orientation: orientation
});

function resize() {
  if (el.offsetHeight > window.innerHeight)
    el.style.maxWidth = (window.innerHeight - el.querySelector('span.text').offsetHeight) + 'px';
}
resize();
window.addEventListener('resize', resize);
