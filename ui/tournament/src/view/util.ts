import { Attrs } from 'snabbdom/modules/attributes'
import { h } from 'snabbdom'
import { Hooks } from 'snabbdom/hooks'
import { VNode } from 'snabbdom/vnode';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el =>
    el.addEventListener(eventName, e => {
      const res = f(e);
      if (redraw) redraw();
      return res;
    })
  );
}

export function onInsert(f: (element: HTMLElement) => void): Hooks {
  return {
    insert(vnode) {
      f(vnode.elm as HTMLElement)
    }
  };
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon
  };
}

export function ratio2percent(r: number) {
  return Math.round(100 * r) + '%';
}

export function playerName(p) {
  if (!p.title) return p.name;
  const title64 = p.title.endsWith('-64');
  return [
    h(
      'span.title',
      title64 ? { attrs: {'data-title64': true } } : (p.title == 'BOT' ? { attrs: {'data-bot': true } } : {}),
      title64 ? p.title.slice(0, p.title.length - 3) : p.title
    ), 
    ' ' + p.name
  ];
}

export function player(p, asLink: boolean, withRating: boolean, defender: boolean, withRatingDiff: boolean = true, leader: boolean = false) {
  let ratingDiff;
  if (p.ratingDiff > 0) ratingDiff = h('span.positive', {
    attrs: { 'data-icon': 'N' }
  }, '' + p.ratingDiff);
  else if (p.ratingDiff < 0) ratingDiff = h('span.negative', {
    attrs: { 'data-icon': 'M' }
  }, '' + -p.ratingDiff);
  const rating = ' ' + p.rating + (p.provisional ? '?' : ''),
    fullName = playerName(p);

  return h('a.ulpt.user-link' + (fullName.length > 15 ? '.long' : ''), {
    attrs: asLink ? { href: '/@/' + p.name } : { 'data-href': '/@/' + p.name },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h(
      'span.name' + (defender ? '.defender' : (leader ? '.leader' : '')),
      defender ? { attrs: dataIcon('5') } : (
        leader ? { attrs: dataIcon('8') } : {}
      ), fullName),
    withRating ? h('span.progress', withRatingDiff ? [rating, ratingDiff] : [rating]) : null
  ]);
}

export function numberRow(name: string, value: any, typ?: string) {
  return h('tr', [h('th', name), h('td',
    typ === 'raw' ? value : (typ === 'percent' ? (
      value[1] > 0 ? ratio2percent(value[0] / value[1]) : 0
    ) : window.lidraughts.numberFormat(value))
  )]);
}

export function spinner(): VNode {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' }
      })])]);
}
