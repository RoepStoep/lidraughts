import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import * as draughtsground from './ground';
import { bind, onInsert, dataIcon, spinner, bindMobileMousedown } from './util';
import { getPlayer, playable } from 'game';
import * as router from 'game/router';
import statusView from 'game/view/status';
import { path as treePath } from 'tree';
import { render as renderTreeView } from './treeView/treeView';
import * as control from './control';
import { view as actionMenu } from './actionMenu';
import renderClocks from './clocks';
import * as pdnExport from './pdnExport';
import forecastView from './forecast/forecastView';
import { view as cevalView } from 'ceval';
import { view as keyboardView } from './keyboard';
import explorerView from './explorer/explorerView';
import retroView from './retrospect/retroView';
import practiceView from './practice/practiceView';
import anaCacheView from './anaCache'
import * as gbEdit from './study/gamebook/gamebookEdit';
import * as gbPlay from './study/gamebook/gamebookPlayView';
import { StudyCtrl } from './study/interfaces';
import * as studyView from './study/studyView';
import * as studyPracticeView from './study/practice/studyPracticeView';
import { view as forkView } from './fork'
import { render as acplView } from './acpl'
import AnalyseCtrl from './ctrl';
import { ConcealOf } from './interfaces';
import relayManager from './study/relay/relayManagerView';
import relayIntro from './study/relay/relayIntroView';
import renderPlayerBars from './study/playerBars';
import serverSideUnderboard from './serverSideUnderboard';
import * as gridHacks from './gridHacks';
import { toggleCoordinates } from 'draughtsground/fen';

const li = window.lidraughts;

function renderResult(ctrl: AnalyseCtrl): VNode[] {
  let result: string | undefined;
  if (ctrl.data.game.status.id >= 30) switch (ctrl.data.game.winner) {
    case 'white':
      result = ctrl.data.pref.draughtsResult ? '2-0' : '1-0';
      break;
    case 'black':
      result = ctrl.data.pref.draughtsResult ? '0-2' : '0-1';
      break;
    default:
      result = ctrl.data.pref.draughtsResult ? '1-1' : '½-½';
  }
  const tags: VNode[] = [];
  if (result) {
    tags.push(h('div.result', result));
    const winner = getPlayer(ctrl.data, ctrl.data.game.winner!),
      statusText = statusView(ctrl);
    tags.push(h('div.status', [
      statusText,
      winner ? (statusText.length !== 0 ? ', ' : '') + ctrl.trans(winner.color == 'white' ? 'whiteIsVictorious' : 'blackIsVictorious') : null
    ]));
  }
  return tags;
}

function makeConcealOf(ctrl: AnalyseCtrl): ConcealOf | undefined {
  const conceal = (ctrl.study && ctrl.study.data.chapter.conceal !== undefined) ? {
    owner: ctrl.study.isChapterOwner(),
    ply: ctrl.study.data.chapter.conceal
  } : null;
  if (conceal) return function (isMainline: boolean) {
    return function (path: Tree.Path, node: Tree.Node) {
      if (!conceal || (isMainline && conceal.ply >= node.ply)) return null;
      if (treePath.contains(ctrl.path, path)) return null;
      return conceal.owner ? 'conceal' : 'hide';
    };
  };
}

function renderAnalyse(ctrl: AnalyseCtrl, concealOf?: ConcealOf) {
  return h('div.analyse__moves.areplay', [
    (ctrl.embed && ctrl.study) ? h('div.chapter-name', ctrl.study.currentChapter().name) : null,
    renderOpeningBox(ctrl),
    renderTreeView(ctrl, concealOf),
  ].concat(renderResult(ctrl)));
}

function wheel(ctrl: AnalyseCtrl, e: WheelEvent) {
  const target = e.target as HTMLElement;
  if (target.tagName !== 'PIECE' && target.tagName !== 'SQUARE' && target.tagName !== 'CG-BOARD') return;
  e.preventDefault();
  if (e.deltaY > 0) control.next(ctrl);
  else if (e.deltaY < 0) control.prev(ctrl);
  ctrl.redraw();
  return false;
}

function inputs(ctrl: AnalyseCtrl): VNode | undefined {
  if (ctrl.ongoing || !ctrl.data.userAnalysis) return;
  if (ctrl.redirecting) return spinner();
  return h('div.copyables', [
    h('div.pair', [
      h('label.name', 'FEN'),
      h('input.copyable.autoselect.analyse__underboard__fen', {
        attrs: {
          spellCheck: false,
          value: ctrl.isAlgebraic() ? toggleCoordinates(ctrl.node.fen, true) : ctrl.node.fen
        },
        hook: bind('change', e => {
          const value = (e.target as HTMLInputElement).value;
          if (value !== ctrl.node.fen) ctrl.changeFen(value);
        })
      })
    ]),
    h('div.pdn', [
      h('div.pair', [
        h('label.name', 'PDN'),
        h('textarea.copyable.autoselect', {
          attrs: { spellCheck: false },
          hook: {
            postpatch: (_, vnode) => {
              (vnode.elm as HTMLInputElement).value = pdnExport.renderFullTxt(ctrl);
            }
          }
        }),
        h('button.button.button-thin.action.text', {
          attrs: dataIcon('G'),
          hook: bind('click', _ => {
            const pdn = $('.copyables .pdn textarea').val();
            if (pdn !== pdnExport.renderFullTxt(ctrl)) ctrl.changePdn(pdn);
          }, ctrl.redraw)
        }, ctrl.trans.noarg('importPdn'))
      ])
    ])
  ]);
}

function jumpButton(icon: string, effect: string, enabled: boolean): VNode {
  return h('button.fbt', {
    class: { disabled: !enabled },
    attrs: { 'data-act': effect, 'data-icon': icon }
  });
}

function dataAct(e: Event): string | null {
  const target = e.target as HTMLElement;
  return target.getAttribute('data-act') ||
    (target.parentNode as HTMLElement).getAttribute('data-act');
}


function repeater(ctrl: AnalyseCtrl, action: 'prev' | 'next') {
  const repeat = function() {
    control[action](ctrl);
    ctrl.redraw();
    delay = Math.max(100, delay - delay / 15);
    timeout = setTimeout(repeat, delay);
  };
  let delay = 350;
  let timeout = setTimeout(repeat, 500);
  control[action](ctrl);
  const eventName = window.lidraughts.hasTouchEvents ? 'touchend' : 'mouseup';
  document.addEventListener(eventName, () => clearTimeout(timeout), {once: true});
}

function controls(ctrl: AnalyseCtrl) {
  const canJumpPrev = ctrl.path !== '',
    canJumpNext = !!ctrl.node.children[0],
    menuIsOpen = ctrl.actionMenu.open,
    multiBoardMenu = ctrl.study && ctrl.study.relay && ctrl.study.members.canContribute() && ctrl.study.multiBoardMenu,
    showFullCaptureHint = !ctrl.embed && !ctrl.data.isAnon && !ctrl.data.pref.fullCapture && li.once('fullcapture-info-seen'),
    noarg = ctrl.trans.noarg;
  return h('div.analyse__controls.analyse-controls', {
    hook: onInsert(el => {
      bindMobileMousedown(el, e => {
        const action = dataAct(e);
        if (action === 'prev' || action === 'next') repeater(ctrl, action);
        else if (action === 'first') control.first(ctrl);
        else if (action === 'last') control.last(ctrl);
        else if (action === 'explorer') ctrl.toggleExplorer();
        else if (action === 'practice') ctrl.togglePractice();
        else if (action === 'menu') {
          ctrl.actionMenu.toggle();
          if (ctrl.study && ctrl.study.multiBoardMenu)
            ctrl.study.multiBoardMenu.open = false;
        } else if (action === 'multiboard-menu') {
          ctrl.actionMenu.open = false;
          if (ctrl.study && ctrl.study.multiBoardMenu)
            ctrl.study.multiBoardMenu.toggle();
        }
      }, ctrl.redraw);
      if (showFullCaptureHint) {
        setTimeout(() => {
          $(el).powerTip({
            closeDelay: 200,
            offset: 20,
            placement: 'n',
            manual: true
          }).data(
            'powertipjq', 
            $(el).children('.fullcapture-info').clone().removeClass('none').on('click', function() {
              $(el).powerTip('hide');
            })
          ).powerTip('show')
          setTimeout(() => $(el).powerTip('hide'), 14000);
        }, 1500);
      }
    })
  }, [
      ctrl.embed ? null : h('div.features', ctrl.studyPractice ? [
        h('a.fbt', {
          attrs: {
            title: noarg('analysis'),
            target: '_blank',
            href: ctrl.studyPractice.analysisUrl(),
            'data-icon': 'A'
          }
        })
      ] : [
        multiBoardMenu ? h('button.fbt', {
          class: { active: multiBoardMenu.open },
          attrs: {
            title: 'Multiboard',
            'data-act': 'multiboard-menu',
            'data-icon': ''
          }
        }) : null,
          /*h('button.fbt', {
            attrs: {
              title: noarg('openingExplorerAndTablebase'),
              'data-act': 'explorer',
              'data-icon': ']'
            },
            class: {
              hidden: menuIsOpen || !ctrl.explorer.allowed() || !!ctrl.retro,
              active: ctrl.explorer.enabled()
            }
          }), null*/
          ctrl.ceval.possible && ctrl.ceval.allowed() && !ctrl.isGamebook() ? h('button.fbt', {
            attrs: {
              title: noarg('practiceWithComputer'),
              'data-act': 'practice',
              'data-icon': ''
            },
            class: {
              hidden: menuIsOpen || !!ctrl.retro,
              active: !!ctrl.practice
            }
          }) : null
        ]),
      h('div.jumps', [
        jumpButton('W', 'first', canJumpPrev),
        jumpButton('Y', 'prev', canJumpPrev),
        jumpButton('X', 'next', canJumpNext),
        jumpButton('V', 'last', canJumpNext)
      ]),
      showFullCaptureHint ? h('div.fullcapture-info.info.none', [
        h('strong.title.text', { attrs: dataIcon('') }, noarg('speedUpYourAnalysis')),
        h('span.content', { 
          hook: onInsert(el => { 
            el.innerHTML = ctrl.trans(
              'enableFullCaptureAtXPreferences',
              '<i><a href="/account/preferences/game-behavior">' + noarg('gameBehavior') + '</a></i>'
            )
          })
        })
      ]) : null,
      h('div'),
      ctrl.studyPractice ? h('div.noop') : h('button.fbt', {
        class: { active: menuIsOpen },
        attrs: {
          title: noarg('menu'),
          'data-act': 'menu',
          'data-icon': '['
        }
      })
    ]);
}

function renderOpeningBox(ctrl: AnalyseCtrl) {
  let opening = ctrl.tree.getOpening(ctrl.nodeList);
  if (!opening && !ctrl.path) opening = ctrl.data.game.opening;
  if (opening) return h('div.opening_box', {
    attrs: { title: opening.code + ' ' + opening.name }
  }, [
      h('strong', opening.code),
      ' ' + opening.name
    ]);
}

function forceInnerCoords(ctrl: AnalyseCtrl, v: boolean) {
  if (ctrl.data.pref.coords == 2)
    $('body').toggleClass('coords-in', v).toggleClass('coords-out', !v);
}

function addChapterId(study: StudyCtrl | undefined, cssClass: string) {
  return cssClass + (study && study.data.chapter ? '.' + study.data.chapter.id : '');
}

export default function(ctrl: AnalyseCtrl): VNode {
  if (ctrl.nvui) return ctrl.nvui.render(ctrl);
  const concealOf = makeConcealOf(ctrl),
    study = ctrl.study,
    showCevalPvs = !(ctrl.retro && ctrl.retro.isSolving()) && !ctrl.practice,
    menuIsOpen = ctrl.actionMenu.open,
    multiBoardMenu = ctrl.study && ctrl.study.multiBoardMenu,
    multiBoardMenuIsOpen = multiBoardMenu && multiBoardMenu.open && ctrl.study && ctrl.study.members.canContribute(),
    gamebookPlay = ctrl.gamebookPlay(),
    gamebookPlayView = gamebookPlay && gbPlay.render(gamebookPlay),
    gamebookEditView = gbEdit.running(ctrl) ? gbEdit.render(ctrl) : undefined,
    playerBars = renderPlayerBars(ctrl),
    clocks = !playerBars && renderClocks(ctrl),
    gaugeOn = ctrl.showEvalGauge(),
    needsInnerCoords = !!gaugeOn || !!playerBars,
    intro = relayIntro(ctrl);
  return h('main.analyse.variant-' + ctrl.data.game.variant.key, {
    hook: {
      insert: vn => {
        forceInnerCoords(ctrl, needsInnerCoords);
        if (!!playerBars != $('body').hasClass('header-margin')) {
          li.raf(() => {
            $('body').toggleClass('header-margin', !!playerBars);
            ctrl.redraw();
          });
        }
        gridHacks.start(vn.elm as HTMLElement);
      },
      update(_, _2) {
        forceInnerCoords(ctrl, needsInnerCoords);
      },
      postpatch(old, vnode) {
        if (old.data!.gaugeOn !== gaugeOn) li.dispatchEvent(document.body, 'draughtsground.resize');
        vnode.data!.gaugeOn = gaugeOn;
      }
    },
    class: {
      'comp-off': !ctrl.showComputer(),
      'gauge-on': gaugeOn,
      'has-players': !!playerBars,
      'has-clocks': !!clocks,
      'has-intro': !!intro,
      'analyse-hunter': ctrl.opts.hunter
    }
  }, [
    ctrl.keyboardHelp ? keyboardView(ctrl) : null,
    study ? studyView.overboard(study) : null,
    intro || h(addChapterId(study, 'div.analyse__board.main-board.is' + ctrl.data.game.variant.board.key), {
      hook: (window.lidraughts.hasTouchEvents || ctrl.gamebookPlay()) ? undefined : bind('wheel', (e: WheelEvent) => wheel(ctrl, e))
    }, [
      ...(clocks || []),
      playerBars ? playerBars[ctrl.bottomIsWhite() ? 1 : 0] : null,
      draughtsground.render(ctrl),
      playerBars ? playerBars[ctrl.bottomIsWhite() ? 0 : 1] : null
    ]),
    (gaugeOn && !intro) ? cevalView.renderGauge(ctrl) : null,
    gamebookPlayView || (intro ? null : h(addChapterId(study, 'div.analyse__tools'), [
      ...(menuIsOpen ? [actionMenu(ctrl)] : (
        (multiBoardMenu && multiBoardMenuIsOpen) ? [multiBoardMenu.view(ctrl.study)] : [
          cevalView.renderCeval(ctrl),
          showCevalPvs ? cevalView.renderPvs(ctrl) : null,
          renderAnalyse(ctrl, concealOf),
          gamebookEditView || forkView(ctrl, concealOf),
          retroView(ctrl) || practiceView(ctrl) || explorerView(ctrl) || anaCacheView(ctrl)
        ]))
    ])),
    (gamebookPlayView || intro) ? null : controls(ctrl),
    (ctrl.embed || intro) ? null : h('div.analyse__underboard', {
      hook: (ctrl.synthetic || playable(ctrl.data)) ? undefined : onInsert(elm => serverSideUnderboard(elm, ctrl))
    }, study ? studyView.underboard(ctrl) : [inputs(ctrl)]),
    intro ? null : acplView(ctrl),
    ctrl.embed ? null : (
      ctrl.studyPractice ? studyPracticeView.side(study!) :
      h('aside.analyse__side', {
        hook: onInsert(elm => {
          ctrl.opts.$side && ctrl.opts.$side.length && $(elm).replaceWith(ctrl.opts.$side);
          $(elm).append($('.streamers').clone().removeClass('none'));
        })
      },
        ctrl.studyPractice ? [studyPracticeView.side(study!)] : (
          study ? [studyView.side(study)] : [
            ctrl.forecast ? forecastView(ctrl, ctrl.forecast) : null,
            (!ctrl.synthetic && playable(ctrl.data)) ? h('div.back-to-game',
              h('a.button.button-empty.text', {
                attrs: {
                  href: router.game(ctrl.data, ctrl.data.player.color),
                  'data-icon': 'i'
                }
              }, ctrl.trans.noarg('backToGame'))
            ) : null
          ]
        )
      )
    ),
    (!intro && study && study.relay) ? relayManager(study.relay) : null,
    ctrl.opts.chat && h('section.mchat', {
      hook: onInsert(_ => {
        if (ctrl.opts.chat.instance) ctrl.opts.chat.instance.destroy();
        ctrl.opts.chat.parseMoves = true;
        li.makeChat(ctrl.opts.chat, chat => {
          ctrl.opts.chat.instance = chat;
        });
      })
    }),
    ctrl.embed ? null : h('div.chat__members.none', {
      hook: onInsert(el => $(el).watchers())
    }, [h('span.number', '\xa0'), ' ', ctrl.trans.noarg('spectators'), ' ', h('span.list')])
  ]);
}
