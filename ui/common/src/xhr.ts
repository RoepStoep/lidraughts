export const json = (url: string, init: RequestInit = {}): Promise<any> =>
  fetch(url, {
    headers: { 'Accept': 'application/vnd.lidraughts.v3+json' },
    cache: 'no-cache',
    credentials: 'same-origin',
    ...init
  })
  .then(res => {
    if (res.ok) return res.json();
    throw res.statusText;
  });

export const text = (url: string, init: RequestInit = {}): Promise<any> =>
  textRaw(url, init).then(res => {
    if (res.ok) return res.text();
    throw res.statusText;
  });

export const textRaw = (url: string, init: RequestInit = {}): Promise<any> =>
  fetch(url, {
    cache: 'no-cache',
    credentials: 'same-origin',
    ...init
  })

export function form(data: any) {
  const formData = new FormData();
  for (let k of Object.keys(data)) formData.append(k, data[k]);
  return formData;
}