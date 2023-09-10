const headers = {
  'Accept': 'application/vnd.lidraughts.v2+json'
};

export function seeks() {
  return $.ajax({
    url: '/lobby/seeks',
    headers: headers
  });
}

export function nowPlaying() {
  return $.ajax({
    url: '/account/now-playing',
    headers: headers
  }).then(o => o.nowPlaying);
}

export function poolToHook(pool, rated) {
  return $.ajax({
    method: 'POST',
    url: '/setup/hook/' + window.lidraughts.StrongSocket.sri + '?pool=1',
    data: {
      variant: 1,
      timeMode: 1,
      time: pool.lim,
      increment: pool.inc,
      days: 1,
      mode: rated ? 1 : 0,
      color: 'random'
    }
  });
}
