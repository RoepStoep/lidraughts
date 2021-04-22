import { Attrs } from 'snabbdom/modules/attributes'
import { h } from 'snabbdom'
import { Hooks } from 'snabbdom/hooks'
import { VNode } from 'snabbdom/vnode';
import { BasePlayer, PlayerInfo, GameResult } from '../interfaces';

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

export function result(p: GameResult, draughtsResult: boolean): string {
  if (p.w === true || p.b === 2)
    return draughtsResult ? '2' : '1';
  else if (p.w === false || p.b === 0)
    return '0';
  return p.o ? '*' : (draughtsResult ? '1' : '½');
}

export const userName = (u: LightUser | LightFmjdUser, withTitle: boolean = true) => {
  if (!u.title || !withTitle) return [u.name];
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

export const userNameHtml = (u: LightUser | LightFmjdUser, withTitle: boolean = true) => {
  if (!u.title || !withTitle) return u.name;
  const title64 = u.title.endsWith('-64'),
    dataAttr = title64 ? ' data-title64' : (u.title == 'BOT' ? ' data-bot' : '');
  return `<span class="title"${dataAttr}>${title64 ? u.title.slice(0, u.title.length - 3) : u.title}</span> ${u.name}`;
}

export function userLink(u: LightUser, withTitle: boolean = true) {
  return h('a.ulpt.user-link' + (((u.title || '') + u.name).length > 15 ? '.long' : ''), {
    attrs: { href: '/@/' + u.name },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('span.name', userName(u, withTitle))
  ]);
}

export function fmjdLink(fmjdId: string, title?: string) {
  const attrs: any = { 
    href: 'https://www.fmjd.org/?p=pcard&id=' + fmjdId,
    target: '_blank',
    rel: 'noopener'
  };
  if (title) attrs.title = title;
  return h('a', { attrs }, fmjdId);
}

export function drawTime(date: Date) {
  const li = window.lidraughts as any;
  return h('time.timeago', {
    attrs: {
      title: date.toLocaleString(),
    },
    hook: {
      insert(vnode) {
        (vnode.elm as HTMLElement).setAttribute('datetime', '' + date);
      }
    }
  }, li.timeagoLocale ? li.timeago.format(date) : date.toLocaleString());
}

export function player(p: BasePlayer, asLink: boolean, withRating: boolean, displayFmjd: boolean) {
  const name = (displayFmjd && p.fmjd) ? p.fmjd.name : p.user.name,
    title = (displayFmjd && p.fmjd?.title) || p.user.title || '';
  return h('a.ulpt.user-link' + ((title + name).length > 15 ? '.long' : ''), {
    attrs: asLink ? { href: '/@/' + p.user.name } : { 'data-href': '/@/' + p.user.name },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('span.name', (displayFmjd && p.fmjd) ? userName(p.fmjd) : userName(p.user)),
    withRating && !displayFmjd ? h('span.rating', ' ' + p.rating + (p.provisional ? '?' : '')) : null
  ]);
}

export function fmjdPlayer(p: PlayerInfo, asLink: boolean, withRating: boolean) {
  const name = p.fmjd ? p.fmjd.name : p.user.name,
    title = (p.fmjd ? p.fmjd.title : p.user.title) || '';
  return h('a.ulpt.user-link' + ((title + name).length > 15 ? '.long' : ''), {
    attrs: asLink ? { href: '/@/' + p.user.name } : { 'data-href': '/@/' + p.user.name },
    hook: {
      destroy: vnode => $.powerTip.destroy(vnode.elm as HTMLElement)
    }
  }, [
    h('span.name', p.fmjd ? userName(p.fmjd) : userName(p.user)),
    withRating ? h('span.rating', p.fmjd?.rating ? ' ' + p.fmjd.rating : '') : null
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

export function stringRow(name: string, value: string) {
  return h('tr', [
    h('th', name), 
    h('td', value)
  ]);
}

export function spinner(): VNode {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' }
      })])]);
}
