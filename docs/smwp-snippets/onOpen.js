// SMWP "공정 라인" 페이지 — 열기 시 (OnOpen)
// 우측 패널 [연결] 탭 → [열기 시] 슬롯에 그대로 붙여 넣기.
// 자세한 설명: docs/smwp-data-binding.md 4절.

(function () {
  var UECADA = {
    apiBase: 'http://localhost:8080',
    factoryId: 'FACTORY-01',
    pollMs: 1000,
    timeoutMs: 4000
  };
  window.__uecada = UECADA;

  UECADA.fetchJson = function (path) {
    var ctl = new AbortController();
    var to = setTimeout(function () { ctl.abort(); }, UECADA.timeoutMs);
    return fetch(UECADA.apiBase + path, { signal: ctl.signal })
      .then(function (r) { clearTimeout(to); return r.ok ? r.json() : null; })
      .catch(function () { clearTimeout(to); return null; });
  };

  UECADA.write = function (tag, value) {
    try {
      if (typeof SetVarValue === 'function') { SetVarValue(tag, value); return; }
      if (window.$KP && $KP.tag && $KP.tag.set) { $KP.tag.set(tag, value); return; }
    } catch (e) {}
    if (!window.__uecadaTags) window.__uecadaTags = {};
    window.__uecadaTags[tag] = value;
    try {
      var obj = (typeof FindObject === 'function') ? FindObject(tag) : null;
      if (obj) {
        if ('Caption' in obj) obj.Caption = String(value);
        else if ('Text' in obj) obj.Text = String(value);
      }
    } catch (e) {}
  };

  Promise.all([
    UECADA.fetchJson('/api/lines?factoryId=' + encodeURIComponent(UECADA.factoryId)),
    UECADA.fetchJson('/api/equipments?factoryId=' + encodeURIComponent(UECADA.factoryId))
  ]).then(function (res) {
    UECADA.lines = res[0] || [];
    UECADA.equipments = res[1] || [];
    UECADA.equipIds = UECADA.equipments.map(function (e) { return e.equipmentCode; });

    UECADA.write('LINE_COUNT', UECADA.lines.length);
    UECADA.write('EQUIP_COUNT', UECADA.equipments.length);
    UECADA.write('UECADA_STATUS', '연결됨');
  }).catch(function () {
    UECADA.write('UECADA_STATUS', '백엔드 연결 실패');
  });

  if (typeof window.__uecadaPoller === 'undefined') {
    window.__uecadaPoller = setInterval(function () {
      if (typeof window.__uecadaRunOnce === 'function') window.__uecadaRunOnce();
    }, UECADA.pollMs);
  }

  console.log('[UECADA] OnOpen ready', UECADA);
})();
