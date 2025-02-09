import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode'
import AnalyseCtrl from './ctrl';
import { nodeFullName } from './util';


let cachedDateFormatter: (date: Date) => string;

function getDateFormatter(): (date: Date) => string {
  if (!cachedDateFormatter) cachedDateFormatter = (window.Intl && Intl.DateTimeFormat) ?
    new Intl.DateTimeFormat(document.documentElement!.lang, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    }).format : function(d) { return d.toLocaleString(); }

  return cachedDateFormatter;
}

export default function(ctrl: AnalyseCtrl): VNode | undefined {
  if (!ctrl.data.anaCache) return
  
  const anaNode = ctrl.anaCacheNode(ctrl.path)
  console.log(anaNode)

  const dateFormatter = getDateFormatter();
  return h('div.ana-cache-box.sub-box', [
    h('div.title', 'Cached analysis: ' + nodeFullName(ctrl.node)),
    anaNode ? h('div.info', [
      h('div', 'Move time: ' + dateFormatter(new Date(anaNode.time))),
      h('div', 'Duration: ' + anaNode.centis / 100 + 's'),
      ...(anaNode.entries || []).map(entry =>
        h('div.entry', [
           h('div', 'Time: ' + dateFormatter(new Date(entry.time))),
           h('div', 'IP: ' + entry.ip)
        ])
      )
    ]) : undefined
  ]);
}
