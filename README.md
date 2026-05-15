# UECADA · Frontend (Optimized TypeScript)

스마트팩토리 OEE · 알람 · 설비 상태 모니터링 대시보드.
Vue 3 + TypeScript + Vite + Vue Query 기반.

이 브랜치(`feat/optimized-ts`)는 `feat/frontend` 의 단일 JS 데모 페이지를
풀스택 백엔드와 연동되는 **타입 안전 SPA** 로 마이그레이션한 결과물입니다.

---

## 2026-05-15 작업 요약

기능과 시각 디자인은 그대로 유지한 채 번들 크기 · 구조 · DX 를 개선했습니다.

### 번들 / 성능

| 영역 | 변화 |
|---|---|
| **초기 번들 (gzip, 로그인 진입 시점)** | **~515 KB → ~75 KB (-85 %)** |
| `index-*.js` | 715 KB → 5.6 KB |
| `DashboardPage` 청크 | 472 KB → 12 KB |
| ApexCharts (1 MB) / ECharts (458 KB) | 글로벌 등록 제거 → 대시보드 진입 시점 lazy 로드 |
| Vite | `rollupOptions.output.manualChunks` 로 vendor 분리 (`vue`, `vue-libs`, `vue-query`, `axios`, `apexcharts`, `echarts`, `lucide`) |

### 코드 정리

- `_LEGACY_HARDCODED_CATEGORIES_FOR_REFERENCE` 246 줄 dead code 제거
- 미사용 Lucide 아이콘 import 15 개 제거 (`Bell`, `Users`, `MapPinned`, `MessageSquare`, `Wrench` 등)
- 미사용 모듈 `operationLogApi.ts` / `types/operationLog.ts` 삭제
- `EquipmentDetailPage.vue` (1578 → 1271 줄) 에서 카테고리 패널 3 개 추출
  - `components/equipment/EquipmentCategoryGrid.vue`
  - `components/equipment/CategorySummaryPanel.vue`
  - `components/equipment/CategoryEquipmentList.vue`

### TypeScript 마이그레이션

`<script setup lang="ts">` 로 전환한 컴포넌트:
- `DashboardPage.vue`
- `EquipmentDetailPage.vue`
- `LineDetailPage.vue`
- `SwmpTestPage.vue`
- `UserManagementPage.vue`
- `CommunityPage.vue`

`PeriodKey`, `SummaryKey`, `DisplayUser`, `CategoryWithIcon` 등 명시 타입 도입.
`vue-tsc --noEmit` 통과.

### DX (Developer Experience)

- `src/constants/polling.ts` 신설 — `refetchInterval` / `staleTime` / HTTP timeout 단일 소스화
  ```ts
  POLL_INTERVAL_MS = { alarm: 1_000, dashboard: 10_000, lineDetail: 15_000, ... }
  STALE_TIME_MS    = { short: 15_000, medium: 30_000, long: 60_000 }
  HTTP_TIMEOUT_MS  = 30_000
  ```
- 적용 위치: `useDashboard`, `useAlarms`, `useFactoryLayout`, `useLineDetails`,
  `useAlarmInsights`, `useEquipmentCatalog`, `usePosts`, `useUsers`, `api/client.ts`
- `api/interceptors.ts` 에 5xx / Network 오류 핸들러 hook (`setNetworkErrorHandler`) 추가 —
  토스트/로깅을 앱 어디서나 주입 가능

---

## 디렉토리 구조

```
src/
  api/          - axios client · 인터셉터 · 도메인별 API (alarm, dashboard, equipment, line, post, user, analysis)
  components/   - 페이지 + equipment/ 패널 분리 컴포넌트
  composables/  - Vue Query 래퍼 (useDashboard, useAlarms, useLineDetails, ...)
  constants/    - polling.ts (전역 폴링/스테일/타임아웃)
  router/       - Vue Router (hash mode)
  stores/       - Pinia (auth)
  types/        - 도메인 타입 (alarm, analysis, dashboard, equipment, line, post, user)
```

---

## 로컬 실행

```bash
npm install
npm run dev        # vite --host 127.0.0.1
npm run build      # vue-tsc + vite build
npm run typecheck  # vue-tsc --noEmit
```

`.env.example` 을 복사해 `.env` 로 두면 백엔드 API 베이스 URL 등을 주입할 수 있습니다.

---

## 보존 약속

- 공개 API URL / HTTP 메서드 / 응답 필드 시그니처
- 화면에 보이는 정보 · 인터랙션 · 시각 디자인
- 데모용 로컬 토큰 인증 구조

## 후속 작업 후보

- `DashboardPage.vue` (1569 줄) 의 패널 추가 분리
- ApexCharts → ECharts 단일화로 1 MB 청크 제거
- 라인/설비 응답 클라이언트 캐싱 (ETag)
- JWT 통합

---

> 본 브랜치는 기존 `feat/frontend` 의 단일 페이지 JS 데모를 기준으로 갈라져,
> TypeScript · Vue Query · Pinia · Axios 풀스택 대응 SPA 로 재작성된 결과입니다.
