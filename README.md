# UECADA

스마트팩토리 OEE · 알람 · 설비 상태 모니터링 시스템.
백엔드: Spring Boot 3 + JPA + MySQL · 프론트: Vue 3 + TypeScript + Vite · 인프라: docker-compose.

---

## 2026-05-15 최적화 작업 요약

기능 동작과 API 계약은 그대로 유지한 채 성능·구조·DX 를 개선했습니다.

### 프론트엔드

| 영역 | 변화 |
|---|---|
| **초기 번들 (gzip, 로그인 진입 시점)** | **~515 KB → ~75 KB (-85%)** |
| `index-*.js` | 715 KB → 5.6 KB |
| `DashboardPage` 청크 | 472 KB → 12 KB |
| ApexCharts (1 MB) / ECharts (458 KB) | 글로벌 등록 제거 → 대시보드 진입 시점 lazy 로드 |
| Dead code | `_LEGACY_HARDCODED_CATEGORIES_FOR_REFERENCE` 246줄 + 미사용 import 15개 + `operationLogApi` 모듈 제거 |
| TypeScript | 6개 `.vue` 전부 `<script setup lang="ts">` 마이그레이션 |
| 폴링 상수 | `src/constants/polling.ts` 단일 소스로 통합 (8개 composable + client 사용) |
| axios 인터셉터 | 401 외에 5xx / Network 오류 hook (`setNetworkErrorHandler`) 추가 |
| 컴포넌트 분리 | `equipment/EquipmentCategoryGrid`, `CategorySummaryPanel`, `CategoryEquipmentList` 추출 |

### 백엔드

| 영역 | 변화 |
|---|---|
| **N+1 제거** | 설비 N대 × `findTopBy…` N회 → `findLatestForEquipmentCodes(List<String>)` 1회. `LineAggregationService`, `DashboardController.statusDistribution` 양쪽 적용 |
| 대시보드 알람 요약 | 진동 모니터가 비어 있을 때 알람 테이블 폴백 추가 (응답 시그니처 유지) |
| OPC UA 경고 | `application-local.yml` 의 `phm.opcua.x-das.enabled` 기본값 `true → false` — 로컬에서 5초마다 출력되던 `Connection refused` 사라짐 |
| `application-*.yml` | 공통 키(`server`, `spring.jpa`)를 `application.yml` 로 DRY |
| 테스트 | `LineAggregationServiceTest` 신규 — N+1 회피 검증 (총 4 → 5건) |

### DB · 인프라

- `database/init/03_indexes.sql` 신설 (idempotent, 추가 전용):
  `alarm(alarm_status, occurred_at)`, `alarm(equipment_code, occurred_at)`, `alarm(occurred_at)`,
  `equipment(location)`, `equipment(process_type)`, `analysis_result(created_at)`, `board_post(created_at)`
- `docker-compose.yml` / `docker-compose.local-infra.yml` 양쪽에 위 SQL 마운트 추가
- `alarm-simulator` 베이스 이미지 `python:3.11-slim → python:3.11-alpine` (~80 MB 절감)

### 검증

- `npm run build` (vue-tsc + vite) — OK
- `./gradlew clean assemble` — OK (13.97s → **6.80s**)
- `./gradlew test` — 5/5 통과
- `ReadLints` 전 영역 신규 오류 0
- 주요 엔드포인트 `/api/dashboard/frontend`, `/api/lines`, `/api/alarms`, `/health` — 200

### 보존 약속 (변경하지 않은 것)

- 공개 API 의 URL · HTTP 메서드 · 응답 필드명 / 타입
- 데모 로컬 토큰 기반 인증 구조 (JWT 통합은 별도 작업)
- 화면에 보이는 정보 · 인터랙션 · 시각 디자인
- DB 스키마 (인덱스 추가만, 컬럼 변경 없음)
- `scripts/alarm-simulator.py` · `alarm-simulator` 서비스

### 후속 작업 후보 (이번 PR 미포함)

- `DashboardPage.vue` 1569줄 패널 분리
- ApexCharts → ECharts 통일로 1 MB 청크 제거
- 라인 / 설비 목록 `@Cacheable` 또는 ETag 캐싱
- JWT 통합

---

## 2026-05-18 통합 작업 요약 (`feat/FE_BE_fin_0518`)

### SMWP(WebSCADA) 연동 — 최소 구성

| 항목 | 설명 |
|------|------|
| **의도** | UECADA에서 **버튼/라인 카드**로 SMWP 화면을 **팝업 창**으로 연다. DB·OPC는 별도(SMWP가 처리). |
| **URL** | `.env`(로컬 전용, git 제외) 또는 `.env.example` — `VITE_SWMP_DEFAULT_URL` 예: `http://222.108.180.36:11005/#LDV` 또는 `?Pro=프로젝트ID#LDV` |
| **코드** | `frontend/UECADA_3/src/composables/useWebScadaLinks.ts` — 호스트 화이트리스트, `window.open(..., 'uecada-webscada')` |
| **UI** | `FactoryLayoutPage.vue` — 상단 **웹스카다** 버튼, 라인 요약 카드 클릭 시 팝업 |
| **테스트 화면** | `SwmpTestPage.vue` — 동일 팝업 (구 iframe 모달 제거) |
| **로그인 연동** | UECADA 로그인 직후 SMWP 자동 오픈·자동 SMWP 로그인 **사용 안 함** (`LoginPage`에서 호출 제거, `.env`에 계정 주입 패턴 제거) |

### 설비 상세 · 공장 레이아웃 UI

- `style.css` — **상세 설비 정보** 우측 상태 배지(경고/ALERT 등) 및 공통 `.pill` / `.line-state` **가운데 정렬**
- 라인·설비 응답 확장 및 설비 상세 페이지 등 프론트/백엔드 조회 경로 정리(라인별 KPI·런타임 연동)

### 로컬 백엔드 기동·스키마

| 항목 | 설명 |
|------|------|
| **데모 KPI SQL** | `database/init/04_demo_metrics.sql` — `line_station_balance`, `equipment_runtime_demo`, `line_kpi_log` 시드 등 |
| **Docker 로컬 DB** | `docker-compose.local-infra.yml`에 `04_demo_metrics.sql` 마운트 추가(최초 `up` 시 적용) |
| **컬럼 타입** | `line_station_balance.station_no` — Hibernate `Integer`와 맞추기 위해 **`INT`** (기존 `TINYINT`와 불일치 시 검증 실패) |
| **버그 수정** | `DemoMetricsService` — `findLatestByLineId` 존재하지 않음 → `findTopByLineIdOrderByRecordedAtDesc` 사용 |

### 실시간 데모(1초 폴링용)

| 처리 | 설명 |
|------|------|
| **Push API** | `POST /api/demo/metrics/push` — 메모리 스토어(`DemoMetricsLiveStore`)에 라인·설비 KPI 반영 → `/api/lines`, 설비 API 등에서 조회 |
| **스크립트** | `scripts/demo-metrics-pusher.mjs` — 기본 1초마다 sin 파형 데이터 POST (`API_BASE`, `INTERVAL_MS` 환경변수) |
| **프론트** | `VITE_REALTIME_DEMO=true` 시 `src/constants/polling.ts`에서 일부 화면 **1초** 폴링 |

> SMWP LDV 화면의 「로드 실패」는 **UECADA API와 무관**하며, SMWP 프로젝트의 태그·HTTP·OPC 설정이 필요합니다. UECADA 쪽 데모는 `demo-metrics-pusher` + 백엔드 + Vue만으로 확인할 수 있습니다.

### 로컬 실행 참고(요약)

```bash
# DB + Mosquitto (로컬 인프라)
docker compose -f docker-compose.local-infra.yml up -d

# 백엔드 (Docker MySQL 8600 포트)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local-docker'

# (선택) 1초 데모 수치
node scripts/demo-metrics-pusher.mjs

# 프론트 — .env 에 VITE_API_BASE_URL 등 설정 후
cd frontend/UECADA_3 && npm run dev
```
