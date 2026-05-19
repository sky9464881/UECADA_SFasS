# UECADA

전체 DAS/equip-sim/X_DAS/backend/ai-api/frontend 실행과 정합성 검증 절차는 [docs/system-runbook.md](docs/system-runbook.md)를 기준으로 확인한다.

스마트팩토리 OEE · 알람 · 설비 상태 모니터링 시스템.
백엔드: Spring Boot 3 + JPA + MySQL · 프론트: Vue 3 + TypeScript + Vite · 인프라: docker-compose.

---

## 2026-05-19 FE/BE 통합 작업 요약 (`feat/FE_BE_fin_0519`)

로컬 Docker 인프라, Spring Boot(`local-docker`), Vue/Vite를 기준으로 사용자 관리·알람·설비 상세·커뮤니티·SMWP 연결 흐름을 점검하고 데모 시연에 필요한 표시 보강을 반영했습니다.

### 주요 변경

| 영역 | 내용 |
|---|---|
| 사용자 관리 | 사용자 목록/역할 타입을 정리하고, 로그인 실패 횟수와 계정 잠금 상태를 백엔드 응답·프론트 화면에 노출. `PATCH /api/users/{id}/lock`로 관리자 잠금/해제 흐름 추가 |
| 로그인 | 실패 횟수를 누적하고 임계치 도달 시 계정을 잠그도록 `AuthService` 보강. 로그인 응답에 잠금 여부 등 사용자 상태 필드 포함 |
| DB 초기화 | `users.failed_login_count`, `users.locked` 컬럼을 `init.sql`에 추가하고, 기존 볼륨 대응용 idempotent 보강을 `03_indexes.sql`에 추가 |
| 설비 API | `/api/equipments` 응답에 데모 런타임 KPI(`utilizationRate`, `defectCount`, `operatorName`, `cycleTimeSec`, 전류/온도/습도/진동)를 합쳐 반환하는 조회 서비스 추가 |
| 설비 상세 화면 | 공통 지표는 설비 API 런타임 필드를 fallback으로 사용. X_DAS 버퍼가 비어도 공정별 Category Specific Data는 설비코드·메트릭 기반의 안정적인 예시값으로 표시 |
| 알람 | `GET /api/alarms`에 `limit` 옵션을 추가하고, 프론트 요약에서 `IN_PROGRESS`도 미처리 알람에 포함 |
| 커뮤니티 | 커뮤니티 알람 패널은 `OPEN` 알람 상위 20건만 조회. 알람 조치 후 `['community','alarm-list']` 캐시도 무효화해 패널 갱신 지연을 줄임 |
| 공장 보고서 | 버퍼가 비어 있을 때 에너지/탄소 관련 보고서 수치가 0으로 고정되지 않도록 데모 메트릭 fallback 적용 |
| SMWP/WebSCADA | `.env.example`에 라인/공정 pageId 예시를 정리하고, `verify-smwp-integration.mjs`로 오버레이 연결 계약을 확인할 수 있게 함 |
| 프론트 구조 | 루트 `App.vue`를 라우터 뷰만 렌더하도록 정리하고, 기존 하드코딩/스타일 중복 일부를 정리 |

### 확인한 특이사항

- 공정별 상세 지표는 DB 시드가 아니라 `SensorBufferRegistry`의 메모리 버퍼와 X_DAS OPC 구독에 의존한다. 로컬 기본값은 X_DAS OPC 비활성이라 실측 버퍼가 비어 있을 수 있다.
- 현재 Category Specific Data의 예시값은 실시간 대체가 아니라 데모/오프라인 표시용이다. 실측 연결 시 상태 문구가 X_DAS 수신 상태로 바뀐다.
- 전압은 백엔드 런타임 필드가 없어 버퍼 미수신 시 220V 기본값으로 표시한다.
- 기존 Docker 볼륨을 유지한 환경에서는 신규 사용자 잠금 컬럼이 자동 적용되지 않을 수 있어 수동 `ALTER` 또는 볼륨 재초기화가 필요할 수 있다.
- API 전역 인증/인가, `FACTORY-01` 하드코딩, Debug/Demo 계열 엔드포인트 운영 노출 위험은 후속 보안·운영 정리 항목으로 남아 있다.

### 검증

- `./gradlew test` — 통과
- `npm run typecheck` — 통과
- `npm run build` — 통과
- API smoke: 사용자 잠금, 제한 알람 조회, 설비 런타임 응답, 커뮤니티 보고서 fallback 확인
- `frontend/UECADA_3/scripts/verify-smwp-integration.mjs` — SMWP 오버레이 연결 계약 확인용 스크립트 추가

### 후속 작업 후보

- `GET /api/alarms`의 `limit` 처리를 DB `Pageable`/커서 기반으로 내려 전량 로드 비용 제거
- `FACTORY-01`을 사용자/환경 컨텍스트 기반 `factoryId`로 치환
- Spring Security 기반 인증/인가 도입 후 사용자/알람/센서 push/debug/demo API 보호
- X_DAS OPC NodeId와 `LINE01.CAST01:metric` 버퍼 키 매핑을 운영 환경 설정으로 외부화

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
