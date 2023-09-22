import { h } from 'snabbdom'
import { VNode } from 'snabbdom/vnode';
import { Hooks } from 'snabbdom/hooks'
import { Attrs } from 'snabbdom/modules/attributes'

export const emptyRedButton = 'button.button.button-red.button-empty';

export function plyColor(ply: number): Color {
  return (ply % 2 === 0) ? 'white' : 'black';
}

export function bindMobileMousedown(el: HTMLElement, f: (e: Event) => any, redraw?: () => void) {
  el.addEventListener(window.lidraughts.mousedownEvent, e => {
    f(e);
    e.preventDefault();
    if (redraw) redraw();
  })
}

function listenTo(el: HTMLElement, eventName: string, f: (e: Event) => any, redraw?: () => void) {
  el.addEventListener(eventName, e => {
    const res = f(e);
    if (res === false) e.preventDefault();
    if (redraw) redraw();
    return res;
  })
}

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return onInsert(el => listenTo(el, eventName, f, redraw));
}

export function bindSubmit(f: (e: Event) => any, redraw?: () => void): Hooks {
  return bind('submit', e => {
    e.preventDefault();
    return f(e);
  }, redraw);
}

export function onInsert<A extends HTMLElement>(f: (element: A) => void): Hooks {
  return {
    insert: vnode => f(vnode.elm as A)
  };
}

export function readOnlyProp<A>(value: A): () => A {
  return function (): A {
    return value;
  };
}

export function dataIcon(icon: string): Attrs {
  return {
    'data-icon': icon
  };
}

export function iconTag(icon: string) {
  return h('i', { attrs: dataIcon(icon) });
}

export function plyToTurn(ply: number): number {
  return Math.floor((ply - 1) / 2) + 1;
}

export function nodeFullName(node: Tree.Node) {
  const renderPly = (node.displayPly ? node.displayPly : node.ply);
  if (node.san) return plyToTurn(renderPly) + (
    renderPly % 2 === 1 ? '.' : '...'
  ) + ' ' + (node.alg || node.san!);
  return 'Initial position';
}

export function plural(noun: string, nb: number): string {
  return nb + ' ' + (nb === 1 ? noun : noun + 's');
}

export function titleNameToId(titleName: string): string {
  if (!titleName) return '';
  const split = titleName.split(' ');
  return (split.length === 1 ? split[0] : split[1]).toLowerCase();
}

export function shortenTitleName(titleName: string): string {
  const split = titleName.split(' ');
  if (split.length <= 1) {
    return split[0];
  } else if (split[0].endsWith('-64')) {
    return split[0].slice(0, split[0].length - 3) + ' ' + split[1];
  }
  return titleName;
}

export function spinner(): VNode {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' }
      })])]);
}

export function innerHTML<A>(a: A, toHtml: (a: A) => string): Hooks {
  return {
    insert(vnode) {
      (vnode.elm as HTMLElement).innerHTML = toHtml(a);
      vnode.data!.cachedA = a;
    },
    postpatch(old, vnode) {
      if (old.data!.cachedA !== a) {
        (vnode.elm as HTMLElement).innerHTML = toHtml(a);
      }
      vnode.data!.cachedA = a;
    }
  };
}

export function richHTML(text: string, newLines: boolean = true): Hooks {
  return innerHTML(text, t => enrichText(t, newLines));
}

export function baseUrl() {
  return `${window.location.protocol}//${window.location.host}`;
}

export function toYouTubeEmbed(url: string): string | undefined {
  const embedUrl = toYouTubeEmbedUrl(url);
  if (embedUrl) return `<div class="embed"><iframe width="100%" src="${embedUrl}" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></div>`;
}

export function toTwitchEmbed(url: string): string | undefined {
  const embedUrl = toTwitchEmbedUrl(url);
  if (embedUrl) return `<div class="embed"><iframe width="100%" src="${embedUrl}" frameborder="0" allowfullscreen></iframe></div>`;
}

function toTwitchEmbedUrl(url) {
  if (!url) return;
  var m = url.match(/(?:https?:\/\/)?(?:www\.)?(?:twitch.tv)\/([^"&?/ ]+)/i);
  if (m) return `https://player.twitch.tv/?channel=${m[1]}&parent=${location.hostname}&autoplay=false`;
}

function toYouTubeEmbedUrl(url) {
  if (!url) return;
  var m = url.match(/(?:https?:\/\/)?(?:www\.)?(?:youtube\.com|youtu\.be)\/(?:watch)?(?:\?v=)?([^"&?\/ ]{11})(?:\?|&|)(\S*)/i);
  if (!m) return;
  var start = 0;
  m[2].split('&').forEach(function(p) {
    var s = p.split('=');
    if (s[0] === 't' || s[0] === 'start') {
      if (s[1].match(/^\d+$/)) start = parseInt(s[1]);
      else {
        var n = s[1].match(/(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?/);
        start = (parseInt(n[1]) || 0) * 3600 + (parseInt(n[2]) || 0) * 60 + (parseInt(n[3]) || 0);
      }
    }
  });
  var params = 'modestbranding=1&rel=0&controls=2&iv_load_policy=3' + (start ? '&start=' + start : '');
  return 'https://www.youtube.com/embed/' + m[1] + '?' + params;
}

const commentYoutubeRegex = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/(?:.*?(?:[?&]v=)|v\/)|youtu\.be\/)(?:[^"&?\/ ]{11})\b/i;
const commentTwitchRegex = /(?:https?:\/\/)?(?:www\.)?(?:twitch.tv)\/([^"&?/ ]+)(?:\?|&|)(\S*)/i;
const imgUrlRegex = /\.(jpg|jpeg|png|gif)$/;
const newLineRegex = /\n/g;

function imageTag(url: string): string | undefined {
  if (imgUrlRegex.test(url)) return `<img src="${url}" class="embed"/>`;
}

function toLink(url: string) {
  if (commentYoutubeRegex.test(url)) return toYouTubeEmbed(url) || url;
  if (commentTwitchRegex.test(url)) return toTwitchEmbed(url) || url;
  const show = imageTag(url) || url.replace(/https?:\/\//, '');
  return '<a target="_blank" rel="nofollow noopener noreferrer" href="' + url + '">' + show + '</a>';
}

export function enrichText(text: string, allowNewlines: boolean = true): string {
  let html = autolink(window.lidraughts.escapeHtml(text), toLink);
  if (allowNewlines) html = html.replace(newLineRegex, '<br>');
  return html;
}

// from https://github.com/bryanwoods/autolink-js/blob/master/autolink.js
const linkRegex = /(^|[\s\n]|<[A-Za-z]*\/?>)((?:https?|ftp):\/\/[\-A-Z0-9+\u0026\u2019@#\/%?=()~_|!:,.;]*[\-A-Z0-9+\u0026@#\/%=~()_|])/gi;

export function autolink(str: string, callback: (str: string) => string): string {
  return str.replace(linkRegex, (_, space, url) => space + callback(url));
}

export function option(value: string, current: string | undefined, name: string) {
  return h('option', {
    attrs: {
      value: value,
      selected: value === current
    },
  }, name);
}

export function scrollTo(el: HTMLElement | undefined, target: HTMLElement |  null) {
  if (el && target) el.scrollTop = target.offsetTop - el.offsetHeight / 2 + target.offsetHeight / 2;
}

export function pieceCount(fen: Fen) {
  // max: W:W31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50:B1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20:H0:F1
  // min: W:WK5:BK46:H0:F1
  return fen.split(',').length + 1
}
