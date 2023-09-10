import { prop } from 'common';
import { ForecastCtrl, ForecastData, ForecastStep } from './interfaces';
import { AnalyseData } from '../interfaces';
import { countGhosts } from 'draughtsground/fen';

function setDisplayPlies(forecasts: ForecastStep[][]) {
  for (const fc of forecasts) {
    for (const node of fc) {
      if (countGhosts(node.fen)) {
        node.displayPly = node.ply + 1
      }
    }
  }
  return forecasts
}

function unmergedLength(fc: ForecastStep[]): number {
  if (fc.length <= 1) return fc.length

  let len = 1
  for (let i = 1; i < fc.length; i++) {
    const fc1 = fc[i].displayPly || fc[i].ply
    const fc2 = fc[i - 1].displayPly || fc[i - 1].ply
    if (fc1 && fc2 && fc1 > fc2)
      len++
  }
  return len
}

export function make(cfg: ForecastData, data: AnalyseData, redraw: () => void): ForecastCtrl {

  const saveUrl = `/${data.game.id}${data.player.id}/forecasts`;

  let forecasts = setDisplayPlies(cfg.steps || []);
  let skipSteps = 0;
  const loading = prop(false);

  function keyOf(fc: ForecastStep[]): string {
    return fc.map(node => node.ply + ':' + node.uci).join(',');
  }

  function isPrefix(fc1: ForecastStep[], fc2: ForecastStep[]): boolean {
    return fc1.length >= fc2.length && keyOf(fc1).startsWith(keyOf(fc2));
  }

  function findStartingWithNode(node: ForecastStep): ForecastStep[][] {
    return forecasts.filter(function (fc) {
      return isPrefix(fc, [node]);
    });
  }

  function collides(fc1: ForecastStep[], fc2: ForecastStep[]): boolean {
    let n = 0;
    for (let i = 0, max = Math.min(fc1.length, fc2.length); i < max; i++) {
      if (fc1[i].uci !== fc2[i].uci) {
        if (cfg.onMyTurn) return n !== 0 && n % 2 === 0;
        return n % 2 === 1;
      }
      if (!fc1[i].displayPly || fc1[i].displayPly === fc1[i].ply) {
        n++;
      }
    }
    return true;
  }

  function truncate(fc: ForecastStep[]): ForecastStep[] {
    let fc2 = fc;
    const requiredPlyMod = cfg.onMyTurn ? 1 : 0

    // must end with player move
    while (fc2.length && unmergedLength(fc2) % 2 !== requiredPlyMod) {
      fc2 = fc2.slice(0, -1);
    }
    return fc2.slice(0, 30);
  }

  function truncateNodes(fc: Tree.Node[]): Tree.Node[] {
    const requiredPlyMod = cfg.onMyTurn ? 1 : 0

    // must end with player move
    return (fc.length % 2 !== requiredPlyMod ? fc.slice(0, -1) : fc).slice(0, 30);
  }

  function isLongEnough(fc: ForecastStep[]): boolean {
    return fc.length >= (cfg.onMyTurn ? 1 : 2);
  }

  function fixAll() {
    // remove contained forecasts
    forecasts = forecasts.filter(function (fc, i) {
      return forecasts.filter(function (f, j) {
        return i !== j && isPrefix(f, fc)
      }).length === 0;
    });
    // remove colliding forecasts
    forecasts = forecasts.filter(function (fc, i) {
      return forecasts.filter(function (f, j) {
        return i < j && collides(f, fc)
      }).length === 0;
    });
  }

  fixAll();

  function reloadToLastPly() {
    loading(true);
    redraw();
    history.replaceState(null, '', '#last');
    window.lidraughts.reload();
  };

  function isCandidate(fc: ForecastStep[]): boolean {
    fc = truncate(fc);
    if (!isLongEnough(fc)) return false;
    var collisions = forecasts.filter(function (f) {
      return isPrefix(f, fc);
    });
    if (collisions.length) return false;
    return true;
  };

  function save() {
    if (cfg.onMyTurn) return;
    loading(true);
    redraw();
    $.ajax({
      method: 'POST',
      url: saveUrl,
      data: JSON.stringify(forecasts),
      contentType: 'application/json'
    }).then(function (data) {
      if (data.reload) reloadToLastPly();
      else {
        loading(false);
        forecasts = setDisplayPlies(data.steps || []);
      }
      redraw();
    });
  };

  function playAndSave(node: ForecastStep) {
    if (!cfg.onMyTurn) return;
    loading(true);
    redraw();
    $.ajax({
      method: 'POST',
      url: saveUrl + '/' + node.uci,
      data: JSON.stringify(findStartingWithNode(node).filter(function (fc) {
        return fc.length > 1;
      }).map(function (fc) {
        return fc.slice(1);
      })),
      contentType: 'application/json'
    }).then(function (data) {
      if (data.reload) reloadToLastPly();
      else {
        loading(false);
        forecasts = setDisplayPlies(data.steps || []);
      }
      redraw();
    });
  }

  return {
    addNodes(fc: ForecastStep[]): void {
      fc = truncate(fc);
      if (!isCandidate(fc)) return;
      forecasts.push(fc);
      fixAll();
      save();
    },
    isCandidate,
    removeIndex(index) {
      forecasts = forecasts.filter((_, i) => i !== index)
      save();
    },
    list: () => forecasts,
    truncate,
    truncateNodes,
    loading,
    onMyTurn: !!cfg.onMyTurn,
    findStartingWithNode,
    playAndSave,
    reloadToLastPly,
    skipSteps
  };

}
