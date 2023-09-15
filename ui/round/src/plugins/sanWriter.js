function decomposeUci(uci) {
  const ucis = [];
  if (uci && uci.length > 1) {
      for (let i = 0; i < uci.length; i += 2) {
        ucis.push(uci.substr(i, 2));
      }
  }
  return ucis;
}

function readFen(fen) {
  var fenParts = fen.split(':');
  var board = {
    pieces: {},
    turn: fenParts[0] === 'W'
  };

  for (var i = 0; i < fenParts.length; i++) {
      var clr = fenParts[i].slice(0, 1);
      if ((clr === 'W' || clr === 'B') && fenParts[i].length > 1) {
          var fenPieces = fenParts[i].slice(1).split(',');
          for (var k = 0; k < fenPieces.length; k++) {
              var fieldNumber = fenPieces[k].slice(1), role = fenPieces[k].slice(0, 1);
              if (fieldNumber.length !== 0 && role.length !== 0) {
                  if (fieldNumber.length == 1)
                      fieldNumber = '0' + fieldNumber;
                  board.pieces[fieldNumber] = role;
              }
          }
      }
  }

  return board;
}

function shorten(uci) {
  return (uci && uci.startsWith('0')) ? uci.slice(1) : uci;
}

function sanOf(board, uci, capture) {
  const move = decomposeUci(uci)
  const from = shorten(move[0]), to = shorten(move.slice(-1))
  if (capture)
      return from + 'x' + to;
  else
      return from + '-' + to;
}

export default function sanWriter(fen, ucis, captLen) {
  var board = readFen(fen);
  var capture = captLen && captLen > 0;
  var sans = {}
  ucis.forEach(function(uci) {
    var san = sanOf(board, uci, capture);
    sans[san] = uci;
  });
  return sans;
}
