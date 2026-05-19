// SMWP "공정 라인" 페이지 — 닫기 시 (OnClose)
// 우측 패널 [연결] 탭 → [닫기 시] 슬롯에 그대로 붙여 넣기.

(function () {
  if (window.__uecadaPoller) {
    clearInterval(window.__uecadaPoller);
    window.__uecadaPoller = null;
  }
  delete window.__uecada;
  delete window.__uecadaRunOnce;
  console.log('[UECADA] OnClose — timer released');
})();
