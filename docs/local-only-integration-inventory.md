# Local-only integration inventory

이 문서는 `origin/develop`에는 없고 현재 로컬 작업본에만 남아 있는 큰 덩어리를 정리한다.
목적은 develop 동기화 이후에도 유지해야 할 SMWP/데모 확장과, 나중에 삭제 또는 PR 분리가 필요한 파일을 구분하는 것이다.

## 유지 의도: SMWP/WebSCADA 연동

| 경로 | 역할 | 처리 방향 |
|---|---|---|
| `frontend/UECADA_3/src/components/WebScadaOverlay.vue` | SMWP를 `<dialog>` + `<iframe>`으로 띄우는 공통 오버레이 | 유지 |
| `frontend/UECADA_3/src/composables/useWebScadaLinks.ts` | `VITE_SWMP_DEFAULT_URL` 기반 URL 검증, 라인/공정 pageId 매핑 | 유지 |
| `backend/src/main/java/com/example/phm/smwp/SmwpHeartbeatController.java` | SMWP 스크립트 연결 확인용 `/api/smwp/heartbeat` | 유지 |
| `docs/frontend-smwp-spec.md` | 프론트/백엔드/SMWP 연동 명세 | 유지 |
| `docs/smwp-data-binding.md` | SMWP 데이터 바인딩 및 스니펫 적용 가이드 | 유지 |
| `docs/smwp-snippets/` | SMWP onOpen/onRun/onClose 스크립트 예시 | 유지 |

## 유지 의도: 데모 KPI/런타임 메트릭

| 경로 | 역할 | 처리 방향 |
|---|---|---|
| `backend/src/main/java/com/example/phm/demo/` | 라인 KPI, 설비 런타임 데모 메트릭 서비스/엔티티/API | 유지 또는 별도 PR |
| `database/init/04_demo_metrics.sql` | 데모 KPI 테이블 초기화 SQL | 유지 또는 DB 적용 방식 재검토 |
| `scripts/demo-metrics-pusher.mjs` | `/api/demo/metrics`로 라인/설비 KPI를 주입하는 로컬 데모 스크립트 | 유지 |

## 주의: 루트 위치가 애매한 프론트 패키지 파일

| 경로 | 관찰 | 처리 방향 |
|---|---|---|
| `frontend/package.json` | develop에는 없음. 실제 Vite 앱은 `frontend/UECADA_3/package.json`을 사용 | 삭제 후보 |
| `frontend/package-lock.json` | 위 파일과 짝인 lockfile로 보임 | 삭제 후보 |

## 현재 develop과 의도적으로 다른 파일

| 경로 | 차이 이유 |
|---|---|
| `frontend/UECADA_3/vite.config.ts` | develop 원본으로 교체 완료. 추후 차이가 생기면 dev proxy/청크 설정 확인 |
| `frontend/UECADA_3/.env.example` | SMWP 라인/공정 URL 예시를 로컬 문서화 용도로 추가 |
| `backend/src/main/java/com/example/phm/equipment/*` | `/api/equipments`에 데모 런타임 KPI 필드를 합쳐 반환하도록 로컬 확장 |

## 정리 원칙

- SMWP 발표/시연에 필요한 파일은 유지한다.
- develop과 완전 동일한 배포 브랜치를 만들 때는 SMWP/데모 파일을 별도 feature 브랜치로 분리한다.
- `frontend/package*.json`은 실제 사용 여부를 확인한 뒤 삭제하는 것이 안전하다.
