import { h } from 'snabbdom'
import { onInsert } from './util';
import ExternalTournamentCtrl from '../ctrl';

export default function(ctrl: ExternalTournamentCtrl) {
  const d = ctrl.data,
    dateFormatter = getDateFormatter();
  return h('main.' + ctrl.opts.classes, [
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
    h('div.tour-ext__main.box', [
      h('h1.text.tour-title', d.name),
      h('h2', 'Upcoming games'),
      h('table.slist.slist-pad', 
        h('tbody',
          d.upcoming.map(c => h('tr', [
            h('td', c.startsAt ? dateFormatter(new Date(c.startsAt)) : 'Unknown'),
            h('td', 
              h('a', 
                { attrs: { href: '/' + c.id } },
                c.whitePlayer + ' vs ' + c.blackPlayer
              )
            )
          ]))
        )
      ),
      h('h2', 'Finished games'),
      h('table.slist.slist-pad', 
        h('tbody',
          d.finished.map(g => h('tr', [
            h('td', dateFormatter(new Date(g.createdAt))),
            h('td', 
              h('a', 
                { attrs: { href: '/' + g.id } },
                g.whitePlayer + ' vs ' + g.blackPlayer
              )
            )
          ]))
        )
      ),
    ]),
    ctrl.opts.chat ? h('div.chat__members.none', [
      h('span.number', '\xa0'), ' ', ctrl.trans.noarg('spectators'), ' ', h('span.list')
    ]) : null
  ]);
}

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