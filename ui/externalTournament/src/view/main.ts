import { h } from 'snabbdom'
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';
import { player as renderPlayer } from './util';
import * as boards from './boards';
import { BaseGame } from '../interfaces';

export default function(ctrl: ExternalTournamentCtrl) {
  const d = ctrl.data,
    dateFormatter = getDateFormatter();
  return h('main.' + ctrl.opts.classes,{
    hook: {
      postpatch() {
        window.lidraughts.miniGame.initAll();
      }
    }
  }, [
    h('aside.tour-ext__side', {
      hook: onInsert(el => {
        $(el).replaceWith(ctrl.opts.$side);
        ctrl.opts.chat && window.lidraughts.makeChat(ctrl.opts.chat);
      })
    }),
    h('div.tour-ext__underchat', {
      hook: onInsert(el => {
        $(el).replaceWith($('.tour-ext__underchat.none').removeClass('none'));
      })
    }),
    h('div.tour-ext__main',
      h('div.box.box-pad', [
        h('h1.text.tour-title', d.name),
        d.ongoing.length ? h('h2', 'Currently playing') : null,
        d.ongoing.length ? boards.many(d.ongoing, ctrl.opts.draughtsResult) : null,
        h('h2', 'Upcoming games'),
        h('table.slist.slist-pad', 
          h('tbody',
            d.upcoming.map(c => h('tr', [
              h('td', 
                h('a.text',
                  { attrs: { href: '/' + c.id } },
                  c.startsAt ? dateFormatter(new Date(c.startsAt)) : 'Unknown'
                )
              ),
              h('td', renderPlayers(c))
            ]))
          )
        ),
        h('h2', 'Finished games'),
        h('table.slist.slist-pad', 
          h('tbody',
            d.finished.map(g => h('tr', [
              h('td', 
                h('a.text',
                  { attrs: { href: '/' + g.id } },
                  dateFormatter(new Date(g.createdAt))
                )
              ),
              h('td', renderPlayers(g)),
              h('td',
                h('div.result', g.winner ? 
                  (g.winner == 'white' ? (ctrl.opts.draughtsResult ? '2-0' : '1-0') : (ctrl.opts.draughtsResult ? '0-2' : '0-1')) :
                  (ctrl.opts.draughtsResult ? '1-1' : '½-½')
                )
              )
            ]))
          )
        ),
      ])
    ),
    ctrl.opts.chat ? h('div.chat__members.none', [
      h('span.number', '\xa0'), ' ', ctrl.trans.noarg('spectators'), ' ', h('span.list')
    ]) : null
  ]);
}

function renderPlayers(g: BaseGame) {
  return h('div.players', { 
      hook: {
        insert(vnode) {
          window.lidraughts.powertip.manualUserIn(vnode.elm as HTMLElement);
        }
      }
    },
    [
      playerWrapper(g, 'white'),
      playerWrapper(g, 'black')
    ]
  );
}

const playerWrapper = (g: BaseGame, c: Color) =>
  h(`div.player.color-icon.is.${c}.text`, renderPlayer(g[c], true, true));

let cachedDateFormatter: (date: Date) => string;

function getDateFormatter(): (date: Date) => string {
  if (!cachedDateFormatter) cachedDateFormatter = (window.Intl && Intl.DateTimeFormat) ?
    new Intl.DateTimeFormat(document.documentElement!.lang, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric'
    }).format : function(d) { return d.toLocaleString(); }

  return cachedDateFormatter;
}