import { h } from 'snabbdom'
import { Hooks } from 'snabbdom/hooks'

export const hasTouchEvents = 'ontouchstart' in window;

export function bindMobileMousedown(el: HTMLElement, f: (e: Event) => any, redraw?: () => void) {
  el.addEventListener(hasTouchEvents ? 'touchstart' : 'mousedown', e => {
    f(e);
    e.preventDefault();
    if (redraw) redraw();
  })
}

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el =>
    el.addEventListener(eventName, e => {
      const res = f(e);
      if (redraw) redraw();
      return res;
    })
  );
}

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A)
  };
}

export function dataIcon(icon: string) {
  return {
    'data-icon': icon
  };
}

export function spinner() {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' }
      })])]);
}

export function puzzleUrl(variant) {
  return variant === 'standard' ? '/training/' : ('/training/' + variant + '/');
}