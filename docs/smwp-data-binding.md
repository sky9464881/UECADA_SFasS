# SMWP(WebSCADA) 페이지 ⇄ UECADA Local DB 연동 가이드

> 2026-05-19 기준: UECADA → SMWP 진입은 자동 로그인 프록시(`/swmp-proxy/login`)를 사용하지 않고,
> `WebScadaOverlay.vue` iframe 에 `VITE_SWMP_DEFAULT_URL` + pageId(`#LDV_A`, `#ED_CAST` 등)를 직접 표시한다.
> 아래 자동 로그인/`swmp-launch.html` 내용은 과거 방식 참고용이다.

> 대상: 사용자가 올려준 이미지의 **SMWP 페이지 편집기** ("공정 라인" 페이지, 우측 패널 **연결** 탭의 `열기 시 / 실행 시 / 닫기 시`).
> 목표: 페이지 위의 "데이터 연결" 자리표시 라벨들이 **UECADA Spring Boot 백엔드 REST API → MySQL** 의 실시간 값으로 채워지도록 SMWP 안에 스크립트를 심는다.

---

## 0. 전체 아키텍처 (한 번에 보기)

```
[UECADA Frontend (Vue, :5173)]
        │
        │  (1) "공정 라인" 클릭
        ▼
[swmp-launch.html] ── POST /swmp-proxy/login ──► [UECADA Spring Boot (:8080)]
        │                                              │
        │                                              │ 서버-서버 (CORS 없음)
        │                                              ▼
        │                                    [SMWP / KingPortal (:11005)]
        │                                              │
        │◄──────────── redirectUrl (token 쿼리) ───────┘
        ▼
[SMWP 화면 #LDV]
```

> **로그인 프록시**: 브라우저가 SMWP 에 직접 `fetch` 하면 CORS 로 막힙니다.
> `POST /swmp-proxy/login` 만 백엔드에 두고, SMWP 로그인은 서버에서 대행합니다.

### 이전 다이어그램 (직통 — 사용 안 함)

```
[swmp-launch.html] ── (3) POST {SMWP}/api/Login/UserLogin ──► CORS 차단 가능
[SMWP / KingPortal Web (:11005)] ─── (4) 토큰 반환
        │
        │  (5) location.replace(SMWP/?token=...&Pro=yhh0518#LDV)
        ▼
[SMWP "공정 라인" 페이지 로드]
        │
        ├── (6a) 열기 시: 초기 데이터 1회 fetch ─────────┐
        ├── (6b) 실행 시: 주기 fetch (1~5초) ───────────┤
        └── (6c) 닫기 시: 타이머 해제                   │
                                                       ▼
                       fetch('http://<UECADA-API>:8080/api/lines') etc.
                                                       │
                                                       ▼
                              [UECADA Spring Boot (:8080)]
                                                       │
                                                       ▼
                                            [MySQL `uecada` DB]
```

핵심 포인트
- **UECADA 백엔드 API** 는 *이미* 다 갖춰져 있다. SMWP 가 직접 fetch 만 하면 끝.
- **CORS**: 백엔드 `CorsConfig` 에 `222.108.180.36:*` 허용 추가됨.
- SMWP 페이지 스크립트는 **순수 JavaScript** (브라우저 위에서 도는 KingPortal 런타임). `fetch` / `XMLHttpRequest` 모두 사용 가능.

---

## 1. UECADA 백엔드 API 매핑 표

SMWP 페이지의 자리표시 라벨 ↔ 백엔드 API ↔ MySQL 테이블 매핑.

| SMWP 라벨 (이미지) | 의미 | API | 응답 필드 | DB 테이블·컬럼 |
|---|---|---|---|---|
| 공정 라인 / OEE / 가동률 | 라인 KPI | `GET /api/lines?factoryId=FACTORY-01` | `latestOee`, `balanceRate`, `uph`, `upmh`, `productivity` | `line_kpi_log`, `line` |
| 기준점 / 최대값 / 신호량 적정 | 설비 운전 메트릭 | `GET /api/equipments` | `cycleTimeSec`, `currentAmp`, `temperatureC`, `vibrationMmS` | `equipment` + 실시간 메모리 store (`/api/demo/metrics/push`) |
| Centro Command / 재가공량 | 설비 가동 로그 | `GET /api/equipments` | `defectCount`, `utilizationRate` | `equipment_operation_log` |
| Command list — 총량/표준/재고량 | 설비별 OK/NG/CT | `GET /api/equipments` | `defectCount`, `cycleTimeSec` | `equipment_operation_log` |
| type data — 설비 상태 | 설비 STATUS | `GET /api/equipment-status?equipIds=...` | `statusCode` ('RUNNING'/'STANDBY'/'ALARM'/'MAINTENANCE'), `updatedAt` | `equipment_status` |
| 차트 — 진동/전류 시계열 | 진동 raw 시리즈 | `GET /api/equipments/{code}/vibration-windows/raw-series?limit=5&maxPoints=800` | `points[]` | `vibration_window` + `data/raw_windows/*.json` |
| 사이클 타임 | 평균 CT | `GET /api/lines` 의 `latestOee` 와 같이 묶임 | `avgCycleTime`(라인 단위는 line_kpi_log) | `line_kpi_log.avg_cycle_time` |
| 장비도 (가동률) | 설비별 가동률 | `GET /api/equipments` | `utilizationRate` | `equipment_kpi_log` + 실시간 store |
| 알람 카운트 / 알람 리스트 | 알람 상태 | `GET /api/alarms?status=OPEN` | rows | `alarm` |
| 도넛 — 상태 분포 | 대시보드 도넛 | `GET /api/dashboard/frontend` | `statusDonut.running/standby/alarm/maintenance` | 집계 |

> **TIP**: 모든 응답은 JSON. SMWP 의 KingPortal 런타임은 일반 브라우저이므로 표준 `fetch + json()` 조합이 가장 단순합니다.

전체 엔드포인트(추가):
```
GET  /api/lines?factoryId=
GET  /api/equipments?factoryId=
GET  /api/equipment-status?equipIds=EQ-001,EQ-002
GET  /api/alarms?status=OPEN
GET  /api/dashboard/frontend
GET  /api/dashboard/summary
GET  /api/alarm-histories?limit=100
GET  /api/equipments/{code}/analysis-results
GET  /api/equipments/{code}/vibration-windows/raw-series
POST /api/demo/metrics/push      ← 외부에서 1초 데이터 주입 (옵션)
```

---

## 2. SMWP 페이지 편집기 ─ 우측 "연결" 탭 사용법

이미지의 **속성 / 연결 / 태그 / 사용자 정의 속성 / 객체** 탭 중 **연결** 탭에는 3개 슬롯이 있다.

| 슬롯 | 트리거 | 권장 용도 |
|---|---|---|
| **열기 시** (OnOpen) | 페이지가 로드된 직후 1회 | 초기 변수/태그 채움, 1회 짜리 마스터 데이터 로드, 타이머 시작 |
| **실행 시** (OnRun) | KingPortal 의 기본 스캔 주기 (보통 250~1000ms) 또는 사용자가 등록한 Timer 마다 반복 | 폴링이 필요한 실시간 값 fetch |
| **닫기 시** (OnClose) | 페이지 이탈 직전 | `clearInterval` 등 리소스 해제 |

> 페이지 자체에 스크립트를 매다는 게 아니라, **"페이지 (page) 컨테이너"** 또는 **"라벨 / 버튼"** 등 객체별로도 동일한 슬롯이 존재합니다. 페이지 단위 폴링 한 곳에서 받아서 여러 라벨 변수에 분배하는 패턴이 **가장 깔끔**합니다.

---

## 3. 페이지 변수(태그) 설계 — 이게 절반이다

스크립트가 fetch 한 값을 **어딘가에 담아야** 라벨에 바인딩됩니다. SMWP / KingPortal 에서 사용할 수 있는 저장소:

1. **프로젝트 태그 (Tag DB)** — 가장 정공법. `Pro=yhh0518` 프로젝트의 변수 사전에서 `LINE_A_OEE` 같은 태그를 만들고, 페이지 라벨의 "텍스트" 속성에 `LINE_A_OEE` 바인딩. 스크립트에서는 `SetTagValue('LINE_A_OEE', 87.5)` 류 API로 쓰기.
2. **페이지 사용자 정의 속성** (이미지의 "사용자 정의 속성" 탭) — 페이지 안에서만 쓰는 임시 변수.
3. **window 전역** — 비공식이지만 가장 빠른 프로토타이핑.

> KingPortal 버전마다 API 이름이 조금씩 다릅니다. 자주 보이는 형태:
> - `SetVarValue("tag", val)` / `GetVarValue("tag")`  (구버전)
> - `$KP.tag.set("tag", val)` / `$KP.tag.get("tag")` (신버전)
> - `KingPortal.Tag.Write("tag", val)`
> - SDK 가 노출되지 않는 경우 → 객체 `.Value` 속성 직접 대입 (예: `objCycleTimeLabel.Caption = "12.3s"`)
>
> **현재 SMWP 가 어떤 API를 노출하는지 확인하는 가장 빠른 방법**: 열기 시에 `console.log(Object.keys(window))` 박아서 키 한 번 보고 결정.

샘플 코드는 **호환성 위주**로 작성합니다 — 태그 API 가 없는 환경에서도 라벨 객체 `.Caption` / `.Text` 에 바로 쓰는 폴백 포함.

---

## 4. 샘플 — 열기 시 (OnOpen)

페이지가 열릴 때 1회 실행. 마스터 데이터(라인/설비 목록) 로드 + 폴링 타이머 시작.

```javascript
// === SMWP "공정 라인" 페이지 — 열기 시 (OnOpen) ===
//
// 목적
// 1) UECADA 백엔드와 통신할 베이스 URL을 잡는다.
// 2) 라인/설비 메타데이터를 1회 fetch 해서 페이지에 캐시한다.
// 3) 폴링 타이머를 띄워 "실행 시"가 매 1초 호출되도록 한다.
//
// 주의 — 본 스크립트는 SMWP 페이지가 222.108.180.36 에서 로드되는 경우를 가정.
//        백엔드 CORS 에 222.108.180.36 가 이미 허용되어 있어야 한다 (CorsConfig.java).

(function () {
  // ─────────── 1) 설정 ───────────
  // 운영 시에는 SMWP 프로젝트 변수에 박아두고 GetVarValue("UECADA_API_BASE") 로 읽는 게 좋다.
  // SMWP 외부 프로그램에서 localhost는 SMWP 실행 PC를 뜻하므로 백엔드 PC의 LAN IP를 사용한다.
  var UECADA = {
    apiBase: 'http://192.168.0.25:8080',  // 백엔드 호스트:포트 (ipconfig 의 IPv4 로 변경)
    factoryId: 'FACTORY-01',
    pollMs: 1000,                         // 1초 주기 (대시보드 데모와 동일)
    timeoutMs: 4000                       // 단일 fetch 타임아웃
  };
  window.__uecada = UECADA;               // 다른 슬롯(실행 시/닫기 시)에서 공유

  // ─────────── 2) 공통 fetch 래퍼 ───────────
  // - AbortController 로 타임아웃
  // - 실패 시 null 반환 (페이지가 죽으면 안 되므로)
  UECADA.fetchJson = function (path) {
    var ctl = new AbortController();
    var to = setTimeout(function () { ctl.abort(); }, UECADA.timeoutMs);
    return fetch(UECADA.apiBase + path, { signal: ctl.signal })
      .then(function (r) { clearTimeout(to); return r.ok ? r.json() : null; })
      .catch(function () { clearTimeout(to); return null; });
  };

  // ─────────── 3) 태그/라벨 쓰기 헬퍼 ───────────
  // KingPortal 의 SetVarValue 가 있으면 사용, 없으면 window 전역에 누적
  UECADA.write = function (tag, value) {
    try {
      if (typeof SetVarValue === 'function') { SetVarValue(tag, value); return; }
      if (window.$KP && $KP.tag && $KP.tag.set) { $KP.tag.set(tag, value); return; }
    } catch (e) {}
    if (!window.__uecadaTags) window.__uecadaTags = {};
    window.__uecadaTags[tag] = value;
    // 라벨에 직접 바인딩된 경우(객체명으로 찾아 .Caption 갱신)
    try {
      var obj = (typeof FindObject === 'function') ? FindObject(tag) : null;
      if (obj) {
        if ('Caption' in obj) obj.Caption = String(value);
        else if ('Text' in obj) obj.Text = String(value);
      }
    } catch (e) {}
  };

  // ─────────── 4) 마스터 메타데이터 1회 로드 ───────────
  Promise.all([
    UECADA.fetchJson('/api/lines?factoryId=' + encodeURIComponent(UECADA.factoryId)),
    UECADA.fetchJson('/api/equipments?factoryId=' + encodeURIComponent(UECADA.factoryId))
  ]).then(function (res) {
    UECADA.lines = res[0] || [];
    UECADA.equipments = res[1] || [];

    // 자주 쓰는 ID 목록 — 실행 시에서 equipment-status 호출 시 사용
    UECADA.equipIds = UECADA.equipments.map(function (e) { return e.equipmentCode; });

    // 페이지에 라인 개수/설비 개수 한 번 표시
    UECADA.write('LINE_COUNT', UECADA.lines.length);
    UECADA.write('EQUIP_COUNT', UECADA.equipments.length);
    UECADA.write('UECADA_STATUS', '연결됨');
  }).catch(function () {
    UECADA.write('UECADA_STATUS', '백엔드 연결 실패');
  });

  // ─────────── 5) 폴링 타이머 시작 ───────────
  // 실행 시 슬롯의 등록이 페이지 라이프사이클에 따라 안 돌 수도 있어서,
  // 안전망 차원에서 자체 setInterval 로 동일 함수를 호출한다.
  if (typeof window.__uecadaPoller === 'undefined') {
    window.__uecadaPoller = setInterval(function () {
      if (typeof window.__uecadaRunOnce === 'function') window.__uecadaRunOnce();
    }, UECADA.pollMs);
  }

  console.log('[UECADA] OnOpen ready', UECADA);
})();
```

---

## 5. 샘플 — 실행 시 (OnRun)

매 주기(1초) 호출. 라인 KPI + 설비 상태 + 진동 시리즈 fetch 후 라벨 갱신.

```javascript
// === SMWP "공정 라인" 페이지 — 실행 시 (OnRun) ===
//
// 매 1초 호출되어 페이지 라벨/차트를 갱신.
// 열기 시(OnOpen)에서 만들어둔 window.__uecada 를 그대로 사용.

(function () {
  var UECADA = window.__uecada;
  if (!UECADA) return;          // 열기 시 미실행이면 그냥 끝

  // 동일 함수를 OnOpen 의 setInterval 도 호출할 수 있도록 export
  window.__uecadaRunOnce = runOnce;

  function runOnce() {
    // 1) 라인 KPI ───────────────────────────────
    UECADA.fetchJson('/api/lines?factoryId=' + encodeURIComponent(UECADA.factoryId))
      .then(function (lines) {
        if (!lines) return;
        UECADA.lines = lines;

        // 첫 번째 라인을 페이지 헤더로 노출 (예: "공정 라인 → LINE-A")
        var first = lines[0];
        if (first) {
          UECADA.write('LINE_A_OEE', first.latestOee != null ? Math.round(first.latestOee) : '--');
          UECADA.write('LINE_A_UPH', first.uph != null ? Math.round(first.uph) : '--');
          UECADA.write('LINE_A_BAL', first.balanceRate != null ? Math.round(first.balanceRate) : '--');
          UECADA.write('LINE_A_STATUS', first.lineStatus || 'UNKNOWN');
          UECADA.write('LINE_A_ALARMS', first.openAlarmCount || 0);
        }

        // 라인별 ID 리스트로 라벨 배열 일괄 갱신 (선택)
        lines.forEach(function (ln, idx) {
          UECADA.write('LINE_' + (idx + 1) + '_NAME', ln.lineName);
          UECADA.write('LINE_' + (idx + 1) + '_OEE',  ln.latestOee != null ? Math.round(ln.latestOee) : '--');
        });
      });

    // 2) 설비 상태 (그리드/도넛 색칠용) ──────────
    if (UECADA.equipIds && UECADA.equipIds.length) {
      UECADA.fetchJson('/api/equipment-status?equipIds=' + UECADA.equipIds.join(','))
        .then(function (rows) {
          if (!rows) return;
          var counts = { RUNNING: 0, STANDBY: 0, ALARM: 0, MAINTENANCE: 0 };
          rows.forEach(function (r) {
            counts[r.statusCode] = (counts[r.statusCode] || 0) + 1;
            UECADA.write('EQ_' + r.equipId + '_STATUS', r.statusCode);
          });
          UECADA.write('CNT_RUNNING',     counts.RUNNING);
          UECADA.write('CNT_STANDBY',     counts.STANDBY);
          UECADA.write('CNT_ALARM',       counts.ALARM);
          UECADA.write('CNT_MAINTENANCE', counts.MAINTENANCE);
        });
    }

    // 3) 설비 런타임 메트릭 (사이클 타임/온도/전류/진동) ──
    UECADA.fetchJson('/api/equipments?factoryId=' + encodeURIComponent(UECADA.factoryId))
      .then(function (equips) {
        if (!equips || !equips.length) return;
        // 페이지 상단의 "기준점/최대값/신호량 적정" 자리 = 대표 설비 1대 값
        var representative = equips.find(function (e) { return e.processType === '주조'; }) || equips[0];
        UECADA.write('PRIMARY_CYCLE_TIME', representative.cycleTimeSec    != null ? representative.cycleTimeSec.toFixed(1) + 's' : '--');
        UECADA.write('PRIMARY_TEMP',       representative.temperatureC    != null ? representative.temperatureC.toFixed(1) + '℃' : '--');
        UECADA.write('PRIMARY_CURRENT',    representative.currentAmp      != null ? representative.currentAmp.toFixed(2) + 'A' : '--');
        UECADA.write('PRIMARY_VIBRATION',  representative.vibrationMmS    != null ? representative.vibrationMmS.toFixed(2) + 'mm/s' : '--');
        UECADA.write('PRIMARY_DEFECT',     representative.defectCount     != null ? representative.defectCount : 0);
        UECADA.write('PRIMARY_UTIL',       representative.utilizationRate != null ? Math.round(representative.utilizationRate) + '%' : '--');
      });

    // 4) 진동 시계열 (차트 위젯) ──────────────────
    // SMWP 의 "차트모듈" 위젯이 X/Y 데이터 시리즈 속성을 노출한다고 가정.
    var firstEq = (UECADA.equipments || [])[0];
    if (firstEq) {
      UECADA.fetchJson('/api/equipments/' + encodeURIComponent(firstEq.equipmentCode)
                       + '/vibration-windows/raw-series?limit=5&maxPoints=400')
        .then(function (s) {
          if (!s || !s.points) return;
          // chartObj 명은 SMWP 페이지에서 차트 객체 이름과 일치해야 함
          try {
            var chart = (typeof FindObject === 'function') ? FindObject('chartVibration') : null;
            if (chart && chart.SetSeries) {
              chart.SetSeries('vib', s.points.map(function (p) { return [p.t, p.v]; }));
            } else {
              window.__uecadaVibrationSeries = s.points;   // 폴백
            }
          } catch (e) {}
        });
    }
  }

  // 자체 실행 시 슬롯 호출 — OnRun 이 직접 호출되었을 때
  runOnce();
})();
```

---

## 6. 샘플 — 닫기 시 (OnClose)

```javascript
// === SMWP "공정 라인" 페이지 — 닫기 시 (OnClose) ===
(function () {
  if (window.__uecadaPoller) {
    clearInterval(window.__uecadaPoller);
    window.__uecadaPoller = null;
  }
  delete window.__uecada;
  delete window.__uecadaRunOnce;
  console.log('[UECADA] OnClose — timer released');
})();
```

---

## 7. "공정 라인" 버튼(이미지 좌측 그룹 상단의 헤더 영역) 자동 로그인

이미지에서 사용자가 디자인 중인 페이지에 **"공정 라인" 헤더/버튼** 이 있다. UECADA 쪽 자동 로그인은 이미 끝났고(`SCADA16/Scada123!` → `swmp-launch.html` → SMWP 토큰), SMWP 페이지 안의 "공정 라인" 버튼을 눌렀을 때 해야 할 동작은 보통 다음 두 가지 중 하나:

1. **다른 라인 페이지로 이동** — SMWP 의 `JumpPage("LINE_B")` / `Navigate("LINE_B")` 호출
2. **UECADA 대시보드 팝업으로 회귀** — `window.open('http://localhost:5173/#/factory-layout', '_blank')`

예시 (버튼 객체의 **클릭 시** 슬롯):
```javascript
// SMWP "공정 라인" 버튼 — 클릭 시
(function () {
  // 다음 라인 페이지로 회전 (LINE-A → LINE-B → LINE-C)
  var current = (typeof GetVarValue === 'function') ? GetVarValue('CURRENT_LINE') : 'A';
  var next = current === 'A' ? 'B' : current === 'B' ? 'C' : 'A';
  if (typeof SetVarValue === 'function') SetVarValue('CURRENT_LINE', next);
  if (typeof JumpPage === 'function') JumpPage('LDV_' + next);
})();
```

---

## 8. 빠른 점검 체크리스트

| 항목 | 확인 방법 |
|---|---|
| 백엔드 떠 있는지 | 백엔드 PC: `curl http://localhost:8080/health`, SMWP PC: `curl http://<백엔드PC-IP>:8080/health` |
| `/api/lines` 응답 | `curl http://<백엔드PC-IP>:8080/api/lines` → JSON 배열 |
| CORS | 브라우저 DevTools → Network → `OPTIONS` 응답 `Access-Control-Allow-Origin: http://222.108.180.36` 포함 |
| 자동 로그인 | UECADA "공정 라인" 버튼 → `swmp-launch.html` 팝업 3단계 모두 초록 |
| 실시간 갱신 | SMWP 페이지의 OEE 라벨이 수집 데이터 갱신 주기에 맞춰 변하는지 확인 |

---

## 9. 자주 막히는 곳 (트러블슈팅)

- **자동 로그인 2단계 빨강**: `swmp-launch` 가 `POST /swmp-proxy/login` 을 호출합니다. 백엔드가 `:8080` 에 떠 있어야 하고, 백엔드 서버에서 `222.108.180.36:11005` 로 나가는 네트워크가 열려 있어야 합니다. Vite 개발 시 `/swmp-proxy/login` 은 `vite.config.ts` 에서 `localhost:8080` 으로 프록시됩니다.
- **CORS 오류 (페이지 스크립트)**: SMWP 페이지에서 `fetch('http://<백엔드PC-IP>:8080/api/...')` 시 백엔드 `CorsConfig` 에 SMWP 호스트가 포함되어야 합니다. 외부 프로그램/WebView가 `Origin: null` 로 요청할 수도 있습니다.
- **mixed content**: SMWP 가 https 인데 UECADA 백엔드가 http 면 브라우저가 차단. 두 쪽 다 http 또는 두 쪽 다 https 로 맞추거나, 리버스 프록시 경유.
- **로그인 401**: SMWP 의 실제 로그인 엔드포인트가 `/api/Login/UserLogin` 이 아닐 수 있음. `swmp-launch.html` 의 `LOGIN_PATHS` 배열에 실제 경로 추가.
- **`SetVarValue` 가 없다는 에러**: KingPortal SDK 이름이 다른 버전. 헬퍼 `UECADA.write` 의 폴백 분기가 `window.__uecadaTags` 에 값을 누적해 두므로, 라벨의 "데이터 연결"을 `window.__uecadaTags.LINE_A_OEE` 같은 표현식으로 잡으면 우회 가능.
- **차트가 안 그려진다**: 차트 객체의 정확한 이름(예: `chartVibration`)이 SMWP 페이지 트리와 일치하는지. 객체 이름은 우측 패널 "객체" 탭에서 확인.

---

## 10. 다음 단계 제안

- (선택) 운영망에서는 DAS/PLC 수집 프로세스가 `/api/lines`, `/api/equipments`, `/api/equipment-status` 응답을 실제 데이터로 갱신하는지 먼저 확인.
- (선택) **HTTPS / 리버스 프록시**: 운영망에서는 SMWP, UECADA, MySQL 모두 같은 도메인 뒤에 두고 nginx 한 번에 정리하면 CORS 가 사라진다.
- (선택) **태그 일괄 정의 SQL**: SMWP 의 `Tag DB import` 가 CSV 를 받는 버전이면, 위 표의 라벨 이름들로 CSV 한 장을 만들면 클릭 한 번에 다 들어간다.
