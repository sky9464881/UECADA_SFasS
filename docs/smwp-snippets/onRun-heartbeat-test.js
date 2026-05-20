// SMWP "공정 라인" 또는 "Equipment Detail" 페이지 — 실행 시 (OnRun) 검증용
// 1초마다 백엔드 /api/smwp/heartbeat 호출 → 카운터, 시간, sin파 값 라벨 갱신
// 라벨 이름은 SMWP 객체 트리에 등록된 이름과 동일해야 한다.
//
// 사전 준비:
// 1. UECADA 백엔드 기동 (localhost:8080)
// 2. (선택) DAS/PLC 수집 프로세스를 함께 실행해 KPI 도 갱신되는지 확인
// 3. SMWP 페이지에 라벨 객체 3개를 만들고 객체명을 아래 TAG_* 와 동일하게 맞추거나,
//    SMWP 태그 시스템에 동일 이름의 변수가 있어야 한다.

(function () {
  var UECADA_BASE = 'http://localhost:8080'
  var POLL_MS = 1000
  var TAG_COUNTER = 'UECADA_HB_COUNTER'
  var TAG_TIME = 'UECADA_HB_TIME'
  var TAG_WAVE = 'UECADA_HB_WAVE'

  function write(tag, value) {
    var asString = String(value)
    try {
      if (typeof SetVarValue === 'function') {
        SetVarValue(tag, asString)
        return
      }
      if (window.$KP && $KP.tag && $KP.tag.set) {
        $KP.tag.set(tag, asString)
        return
      }
    } catch (e) {}
    if (!window.__uecadaTags) window.__uecadaTags = {}
    window.__uecadaTags[tag] = asString
    try {
      var obj = typeof FindObject === 'function' ? FindObject(tag) : null
      if (obj) {
        if ('Caption' in obj) obj.Caption = asString
        else if ('Text' in obj) obj.Text = asString
        else if (obj.thisElement) obj.thisElement.textContent = asString
      }
    } catch (e) {}
  }

  function tick() {
    var ctl = new AbortController()
    var to = setTimeout(function () { ctl.abort() }, 3000)
    fetch(UECADA_BASE + '/api/smwp/heartbeat', { signal: ctl.signal, cache: 'no-store' })
      .then(function (r) { clearTimeout(to); return r.ok ? r.json() : null })
      .then(function (j) {
        if (!j) {
          write(TAG_COUNTER, '연결 실패')
          return
        }
        write(TAG_COUNTER, '#' + j.counter)
        write(TAG_TIME, j.iso || j.epochSeconds)
        write(TAG_WAVE, j.wave)
        console.log('[UECADA hb]', j)
      })
      .catch(function (e) {
        clearTimeout(to)
        write(TAG_COUNTER, '연결 실패: ' + (e && e.message))
      })
  }

  if (window.__uecadaHbTimer) clearInterval(window.__uecadaHbTimer)
  tick()
  window.__uecadaHbTimer = setInterval(tick, POLL_MS)
  window.__uecadaHbStop = function () {
    clearInterval(window.__uecadaHbTimer)
    window.__uecadaHbTimer = null
  }
})()
