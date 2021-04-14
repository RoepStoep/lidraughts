$(function() {
  var maxGames = 21,
    trans = window.lidraughts.trans(window.lidraughts.collectionI18n),
    editState = false,
    $gameList = $('.page-menu__content.now-playing');
  if (!$gameList) return;

  var checkMaxGames = function() {
    if ($gameList.children().length >= maxGames) {
      alert('At most ' + maxGames + ' games can be added!');
      return false;
    }
    return true;
  };
  var checkExistingGames = function(boardHtml) {
    var href = boardHtml.indexOf('href="');
    if (href === -1) return false;
    href += 6;
    var gameId = boardHtml.slice(href, boardHtml.indexOf('"', href));
    if (gameId[0] === '/') gameId = gameId.slice(1);
    if (gameId.endsWith('/white')) {
      gameId = gameId.slice(0, gameId.indexOf('/'));
    }
    var gameIds = getGameIds();
    if (gameIds.indexOf(gameId) !== -1) {
      alert('This game is already in the collection!');
      return false;
    }
    return true;
  }
  var getGameId = function($elm, short) {
    var href = $elm.attr('href');
    if (href && href[0] === '/') {
      href = href.slice(1);
      if (href.endsWith('/white') || (short && href.indexOf('/') !== -1)) {
        href = href.slice(0, href.indexOf('/'));
      }
    }
    return href;
  }
  var getGameIds = function(short) {
    var gameIds = [];
    $gameList.children().each(function() {
      var gameId = getGameId($(this).children().first(), short);
      if (gameId) gameIds.push(gameId);
    });
    return gameIds;
  }
  var getUserId = function($board, black) {
    const users = $board.find('.mini-game__user');
    if (users.length != 2) return;
    return black ? $(users[0]).data('userid') : $(users[1]).data('userid');
  }
  var getFinishedUserIds = function() {
    const userIds = [];
    $gameList.children().each(function() {
      const self = $(this);
      if (self.find('.mini-game__result').length) {
        const userId = getUserId(self);
        if (userId && !userIds.includes(userId)) {
          userIds.push(userId);
        }
      }
    });
    return userIds;
  }
  var getCollectionHref = function(gameIds) {
    const url = window.location.protocol + '//' + window.location.hostname + '/games/collection';
    return gameIds.length ? (url + '?games=' + encodeURIComponent(gameIds.join(','))) : url;
  }
  var updateCollection = function(noHref) {
    const hasFinished = getFinishedUserIds().length ? true : false;
    $('#links-next').toggleClass('visible', hasFinished);
    if (noHref) return;
    var gameIds = getGameIds(),
      $collectionTitle = $('.champion.collection-title');
    if ($collectionTitle.length) {
      $collectionTitle.html(gameIds.length ? trans.plural('nbGames', gameIds.length) : ' - ');
    }
    window.lidraughts.debounce(function() {
      window.history.replaceState(null, '', getCollectionHref(gameIds));
    }, 100)();
  }
  var parseUsername = function($el) {
    var html = $el.html(),
      br = html.indexOf('<br');
    return br !== -1 ? html.slice(0, br) : html;
  }
  var buildEditWrapper = function($board) {
    const removeButton = '<a class="edit-button remove-game" title="' + trans.noarg('removeGame') + '" data-icon="q"></a>';
    if ($board.find('.mini-game__result').length) {
      const userid = getUserId($board);
      if (userid) {
        nextGameButton = '<a class="edit-button next-game" title="' + trans('reloadWithCurrentGameOfX', userid) + '" data-icon="P"></a>';
        return removeButton + nextGameButton;
      }
    }
    return removeButton;
  }
  var setEditState = function(newState) {
    if (editState && editState == newState) {
      $gameList.find('div.edit-overlay').remove();
    } else {
      editState = newState;
    }
    if (editState) {
      $gameList.children().each(function() {
        var self = $(this),
          $board = self.find('.mini-game'),
          flipButton = '<a class="edit-button flip-game" title="' + trans.noarg('flipBoard') + '" data-icon="B"></a>';
        function bindRemoveButton() {
          self.find('a.remove-game').on('click', (ev) => {
            if (editState) {
              ev.stopPropagation();
              self.remove();
              updateCollection();
            }
          });
        };
        function bindNextGameButton() {
          self.find('a.next-game').on('click', (ev) => {
            ev.stopPropagation();
            fetchNewGames([getUserId(self)], self);
          });
        };
        self.append('<div class="edit-overlay">' + flipButton + '<div class="edit-wrapper">' + buildEditWrapper($board) + '</div></div>');
        self.find('a.flip-game').on('click', (ev) => {
          var $gameLink = self.find('a:not(.edit-button)'),
            gameId = getGameId($gameLink, true);
          if (editState && $board.length && gameId) {
            ev.stopPropagation();
            const state = $board.data('state').split('|'),
              color = state[2] === 'white' ? 'black' : 'white',
              $wrap = $board.find('.cg-wrap'),
              players = self.find('.mini-game__player');
            if (players.length === 2) {
              const $white = $(players[1]), 
                $black = $(players[0]);
              $wrap.before($white);
              $wrap.after($black);
            }
            state[2] = color;
            $board.data('state', state.join('|'));
            $gameLink.attr('href', '/' + gameId + (color === 'black' ? '/' + color : ''));
            $wrap.data('draughtsground').set({ orientation: color });
            self.find('.edit-wrapper').html(buildEditWrapper($board));
            bindRemoveButton();
            bindNextGameButton();
            updateCollection();
          }
        });
        bindRemoveButton();
        bindNextGameButton();
        self.find('.edit-overlay').on('click', () => setEditState(false));
      });
    } else {
      $gameList.find('div.edit-overlay').remove();
    }
  };
  var submitGameId = function() {
    var $gameId = $('#collection-gameid'),
      gameId = $gameId.val();
    if (!gameId || !checkMaxGames()) return;
    var urlStart = window.location.hostname + '/',
      urlIndex = gameId.indexOf(urlStart);
    if (urlIndex !== -1) {
      gameId = gameId.slice(urlIndex + urlStart.length);
    }
    if (gameId.length < 8) return;
    $.ajax({
      method: 'get',
      url: '/' + gameId + '/mini?userid=1',
      success: (res) => {
        if (checkExistingGames(res)) {
          insertBoard(res);
          $gameId.val('');
        }
        $gameId.focus();
      }
    });
  };
  var submitUsername = function() {
    var $username = $('#collection-recent'),
      username = $username.val();
    if (!username || !checkMaxGames()) return;
    $.ajax({
      method: 'get',
      url: '/@/' + username + '/recent',
      success: (res) => {
        if (checkExistingGames(res)) {
          insertBoard(res);
          $username.typeahead('val', '');
        }
        $username.focus();
      }
    });
  };
  var fetchNewGames = function(userIds, parent) {
    $.ajax({
      method: 'get',
      url: '/games/collection/next?userids=' + encodeURIComponent(userIds.join(',')),
      success: (newGames) => {
        let updated = false;
        (parent || $gameList.children()).each(function() {
          const self = $(this);
          if (self.find('.mini-game__result').length) {
            const userId = getUserId(self),
              newGame = userId && newGames.hasOwnProperty(userId) && newGames[userId];
            if (newGame) {
              self.html(newGame);
              updated = true;
            }
          }
        });
        if (updated) {
          window.lidraughts.pubsub.emit('content_loaded');
          setEditState(editState);
          updateCollection();
        }
      }
    });
  }
  var insertBoard = function(board) {
    $gameList.append('<div>' + board + '</div>');
    window.lidraughts.pubsub.emit('content_loaded');
    setEditState(editState);
    updateCollection();
  };

  var processFinish = function(e) {
    if ($gameList.find('.mini-game-' + e.id).length) {
      setEditState(editState);
      updateCollection(true);
    }
  };

  window.lidraughts.pubsub.on('game.finish', processFinish);

  $('#submit-gameid').on('click', submitGameId);
  $('#collection-gameid').on('keypress', (ev) => {
    if (ev.keyCode === 13) submitGameId();
  });

  $('#submit-username').on('click', submitUsername);
  $('#collection-recent').on('keypress', (ev) => {
    if (ev.keyCode === 13) submitUsername();
  });

  $('#links-copy').on('click', () => {
    setEditState(false);
    copyTextToClipboard(getCollectionHref(getGameIds()));
  });
  $('#links-edit').on('click', () => {
    setEditState(!editState);
  });
  $('#links-next').on('click', () => {
    var userIds = getFinishedUserIds();
    if (userIds.length) fetchNewGames(userIds);
  });

  updateCollection(true);
});

function copyTextToClipboard(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text);
    return;
  }
  var textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.style.top = '0';
  textArea.style.left = '0';
  textArea.style.position = 'fixed';
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  document.execCommand('copy');
  document.body.removeChild(textArea);
}