var m = require('mithril');
var simul = require('../simul');
var util = require('./util');
var text = require('../text');
var xhr = require('../xhr');

function byName(a, b) {
  if (a.player.id < b.player.id)
    return -1;
  else if (a.player.id > b.player.id)
    return 1;
  return 0;
}

function randomButton(ctrl, candidates) {
  return candidates.length ? m('a.button.text', {
    'data-icon': 'E',
    onclick: function() {
      var randomCandidate = candidates[Math.floor(Math.random() * candidates.length)];
      xhr.accept(randomCandidate.player ? randomCandidate.player.id : randomCandidate.id)(ctrl);
    }
  }, ctrl.trans.noarg('acceptRandomCandidate')) : null;
}

function startOrCancel(ctrl, accepted) {
  var canCancel = !ctrl.data.unique;
  return accepted.length > 1 ?
    m('a.button.button-green.text', {
      'data-icon': 'G',
      onclick: function() { xhr.start(ctrl) }
    }, ctrl.trans.noarg('start')) : (canCancel ? m('a.button.button-red.text', {
      'data-icon': 'L',
      onclick: function() {
        if (confirm(ctrl.trans.noarg('deleteThisSimul'))) xhr.abort(ctrl);
      }
    }, ctrl.trans.noarg('cancel')) : null);
}

module.exports = function(ctrl) {
  var candidates = simul.candidates(ctrl).sort(byName);
  var accepted = simul.accepted(ctrl).sort(byName);
  var isHost = simul.createdByMe(ctrl) || simul.amArbiter(ctrl);
  var isCandidate = function (c) { return candidates.find(function (p) { return p.player.id === c.id }) };
  var allowed = simul.allowed(ctrl).sort(function(a, b) {
    var ca = isCandidate(a), cb = isCandidate(b);
    if (ca && !cb)
      return -1;
    else if (!ca && cb)
      return 1;
    else if (a.id < b.id)
      return -1;
    else if (a.id > b.id)
      return 1;
    return 0;
  });
  var acceptable = !ctrl.data.allowed ? candidates : allowed.filter(function (a) { return isCandidate(a) });
  var mEditCandidates = (ctrl.data.unique && ctrl.toggleCandidates) ? m('tbody', [
    m(ctrl.toggleCandidates ? 'tr.allowed' : 'tr', [
      m('td', { colspan: 2 }, [
        m('label', { 'for': 'add-candidate' }, 'Add allowed player: '),
        m('input.user-autocomplete', {
          'id': 'add-candidate',
          'type': 'text',
          'placeholder': 'Enter username',
          'data-tag': 'span',
          config: function(el, isUpdate) {
            if (!isUpdate) lidraughts.userAutocomplete($(el), {
              focus: 1,
              tag: 'span'
            });
          }
        }),
      ]),
      m('td.action', m('a.button', {
        'data-icon': 'E',
        'title': 'Add to list of allowed candidate players',
        onclick: function() {
          var candidate = $('#add-candidate').val();
          if (candidate && candidate.length > 2) {
            xhr.allow(candidate.toLowerCase())(ctrl);
            $('#add-candidate').typeahead('val', '');
          }
        }
      }))
    ]),
    allowed.map(function(allowed) {
      return m('tr', [
        m('td', { colspan: 2 }, util.player(allowed)),
        m('td.action', m('a.button', {
          'data-icon': 'L',
          title: 'Remove from list of allowed candidate players',
          onclick: function() {
            xhr.disallow(allowed.id)(ctrl);
          }
        }))
      ])
    })
  ]) : null;
  var mEditCandidatesOption = (ctrl.data.unique && (simul.createdByMe(ctrl) || simul.amArbiter(ctrl))) ? m('span.option', {
    'data-icon': '%',
    'title': !ctrl.toggleCandidates ? 'Edit allowed candidates' : ctrl.trans.noarg('backToSimul'),
    onclick: function() {
      ctrl.toggleCandidates = !ctrl.toggleCandidates;
    }
  }) : null
  var mCandidates = m('div.half.candidates',
    m('table.slist.slist-pad',
      m('thead', m('tr', m('th', { colspan: 3 },
        ctrl.toggleCandidates ? 'Edit allowed candidates' : [
          m('strong', candidates.length),
          ctrl.trans.noarg('candidatePlayers')
        ],
        mEditCandidatesOption
      ))),
      mEditCandidates,
      (ctrl.data.unique && ctrl.toggleCandidates) ? null : m('tbody', candidates.map(function(applicant) {
        var variant = util.playerVariant(ctrl, applicant.player);
        return m('tr', {
          key: applicant.player.id,
          class: ctrl.userId === applicant.player.id ? 'me' : ''
        }, [
          m('td', util.player(applicant.player)),
          m('td.variant', {
            'data-icon': variant.icon
          }),
          m('td.action', isHost ? m('a.button', {
            'data-icon': 'E',
            title: ctrl.trans.noarg('accept'),
            onclick: function() {
              xhr.accept(applicant.player.id)(ctrl);
            }
          }) : null)
        ])
      }))
    ));
  var mAllowed = m('div.half.candidates',
    m('table.slist.user_list',
      m('thead', m('tr', m('th', { colspan: 3 },
        ctrl.toggleCandidates ? 'Edit allowed candidates' : [
          ctrl.trans.noarg('allowedPlayers'),
          m('strong', allowed.length),
          ctrl.trans.noarg('candidatePlayers')
        ],
        mEditCandidatesOption
      ))),
      mEditCandidates,
      m('tbody', { 'style': { 'visibility': !ctrl.toggleCandidates ? 'visible' : 'collapse' } }, allowed.map(function(allowed) {
        var candidate = isCandidate(allowed);
        return m('tr', {
          key: allowed.id,
          class: ((ctrl.userId === allowed.id ? 'me' : '') + (!candidate ? ' absent' : '')).trim()
        }, [
          m('td', candidate ? util.player(allowed, candidate.player.rating, candidate.player.provisional, candidate.player.officialRating) : util.player(allowed)),
          m('td.variant', candidate ? {
            'data-icon': util.playerVariant(ctrl, candidate.player).icon
          } : null),
          m('td.action', (isHost && candidate) ? m('a.button', {
            'data-icon': 'E',
            title: ctrl.trans.noarg('accept'),
            onclick: function() {
              xhr.accept(allowed.id)(ctrl);
            }
          }) : null)
        ])
      }))));
  return [
    m('div.box__top', [
      util.title(ctrl),
      m('div.box__top__actions', [
        ctrl.userId ? (
          (simul.createdByMe(ctrl) || simul.amArbiter(ctrl)) ? [
            startOrCancel(ctrl, accepted),
            randomButton(ctrl, acceptable)
          ] : (
            simul.containsMe(ctrl) ? m('a.button', {
              onclick: function() { xhr.withdraw(ctrl) }
            }, ctrl.trans.noarg('withdraw')) : m('a.button.text' + (ctrl.teamBlock ? '.disabled' : ''), {
                'data-icon': 'G',
                onclick: ctrl.teamBlock ? undefined : () => {
                  if (ctrl.data.allowed && !ctrl.data.allowed.find(function (u) { return u.id === ctrl.userId }))
                    alert(ctrl.trans('simulParticipationLimited', ctrl.data.allowed.length));
                  else if (ctrl.data.variants.length === 1)
                    xhr.join(ctrl.data.variants[0].key)(ctrl);
                  else {
                    $.modal($('.simul .continue-with'));
                    $('#modal-wrap .continue-with a').click(function() {
                      $.modal.close();
                      xhr.join($(this).data('variant'))(ctrl);
                    });
                  }
                }
              },
              ctrl.teamBlock ? ctrl.trans('mustBeInTeam', ctrl.data.team.name) : ctrl.trans.noarg('join'))
          )) : m('a.button.text', {
            'data-icon': 'G',
            href: '/login?referrer=' + window.location.pathname
          }, ctrl.trans.noarg('signIn'))
      ])
    ]),
    text.view(ctrl),
    simul.acceptedContainsMe(ctrl) ? m('p.instructions',
      ctrl.trans.noarg('youHaveBeenSelected')
    ) : (
      ((simul.createdByMe(ctrl) || simul.amArbiter(ctrl)) && ctrl.data.applicants.length < 6) ? m('p.instructions',
        ctrl.trans.noarg('shareSimulUrl')
      ) : null
    ),
    m('div.halves',
      !ctrl.data.allowed ? mCandidates : mAllowed,
      m('div.half.accepted', [
        m('table.slist.user_list',
          m('thead', [
            m('tr', m('th', {
              colspan: 3
            }, [
              m('strong', accepted.length),
              ctrl.trans.noarg('acceptedPlayers')
            ])), ((simul.createdByMe(ctrl) || simul.amArbiter(ctrl)) && acceptable.length && !accepted.length) ? m('tr.help',
              m('th',
                ctrl.trans.noarg('acceptSomePlayers'))) : null
          ]),
          m('tbody', accepted.map(function(applicant) {
            var variant = util.playerVariant(ctrl, applicant.player);
            return m('tr', {
              key: applicant.player.id,
              class: ctrl.userId === applicant.player.id ? 'me' : ''
            }, [
              m('td', util.player(applicant.player)),
              m('td.variant', {
                'data-icon': variant.icon
              }),
              m('td.action', isHost ? m('a.button.button-red', {
                'data-icon': 'L',
                onclick: function() {
                  xhr.reject(applicant.player.id)(ctrl);
                }
              }) : null)
            ])
          })))
      ])
    ),
    m('blockquote.pull-quote', [
      m('p', ctrl.data.quote.text),
      m('footer', ctrl.data.quote.author)
    ]),
    m('div.continue-with.none', ctrl.data.variants.map(function(variant) {
      return m('a.button', {
        'data-variant': variant.key
      }, variant.name);
    }))
  ];
};
