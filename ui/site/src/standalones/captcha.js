$(function() {
  lidraughts.requestIdleCallback(function() {
    setTimeout(function() {
      $('div.captcha').each(function() {
        const $captcha = $(this),
          $board = $captcha.find('.mini-board'),
          $input = $captcha.find('input').val(''),
          cg = $board.data('draughtsground'),
          fen = cg.getFen(),
          destsObj = $board.data('moves'),
          destsMap = new Map();
        for (let k in destsObj) destsMap.set(k, destsObj[k].match(/.{2}/g));
        const dests = Object.fromEntries(destsMap);
        cg.set({
          turnColor: cg.state.orientation,
          boardSize: [10, 10],
          captureLength: 1,
          movable: {
            free: false,
            dests,
            color: cg.state.orientation,
            events: {
              after(orig, dest) {
                $captcha.removeClass('success failure');
                submit(orig + ' ' + dest);
              }
            }
          }
        });

        const submit = function(solution) {
          $input.val(solution);
          $.ajax({
            url: $captcha.data('check-url'),
            data: {
              solution
            },
            success: function(data) {
              $captcha.toggleClass('success', data == 1).toggleClass('failure', data != 1);
              if (data == 1) $board.data('draughtsground').stop();
              else setTimeout(function() {
                cg.set({
                  fen: fen,
                  turnColor: cg.state.orientation,
                  movable: { dests }
                });
              }, 300);
            }
          });
        };
      });
    }, 1000);
  });
});
