import { Attrs } from 'snabbdom/modules/attributes'
import { h } from 'snabbdom'
import { Hooks } from 'snabbdom/hooks'
import { VNode } from 'snabbdom/vnode';
import { Player } from '../interfaces';

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

export function preloadUserTips(el: HTMLElement) {
  window.lidraughts.powertip.manualUserIn(el);
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon
  };
}

export const userName = (u: LightUser) => {
  if (!u.title) return [u.name];
  const title64 = u.title.endsWith('-64');
  return [
    h(
      'span.title',
      title64 ? { attrs: {'data-title64': true } } : (u.title == 'BOT' ? { attrs: {'data-bot': true } } : {}),
      title64 ? u.title.slice(0, u.title.length - 3) : u.title
    ), 
    ' ' + u.name
  ];
}

export function player(p: Player, asLink: boolean, withRating: boolean) {
  return h('a.ulpt.user-link' + (((p.user.title || '') + p.user.name).length > 15 ? '.long' : ''), {
    attrs: asLink ? { href: '/@/' + p.user.name } : { 'data-href': '/@/' + p.user.name },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('span.name', userName(p.user)),
    withRating ? h('span.rating', ' (' + p.rating + (p.provisional ? '?)' : ')')) : null
  ]);
}

export function userTip(u: LightUser) {
  return h('a.ulpt.user-link' + (((u.title || '') + u.name).length > 15 ? '.long' : ''), {
    attrs: { 'data-href': '/@/' + u.name.toLowerCase() },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('span.name', userName(u))
  ]);
}

export const ratio2percent = (r: number) => Math.round(100 * r) + '%';

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
