# UECADA — PHM 대시보드 (Vue 3)

설비통합관제(SCADA/PHM) UI 프로젝트입니다.

---

## 변경 기록 (2026-05-15)

**UI·스타일** 위주 수정입니다.

| 구분 | 파일 | 내용 |
|------|------|------|
| 설비별 화면 | `src/components/EquipmentDetailPage.vue` | **설비 카테고리 선택** 그리드에 공장 레이아웃과 동일한 공정 아이콘 적용: 주조 `Flame`, 가공 `Cog`, 세척 `Droplets`, 조립 `Wrench`, 검사 `Search` (기존 공통 `Factory` 아이콘 대체) |
| 설비별 화면 | `src/style.css` | `.equipment-category-icon-wrap` — 카테고리 셀 아이콘 정렬·테두리·색 상속 정리 |
| 사용자·권한 | `src/style.css` | **요약 카드(셀) 버튼** — 메뉴 열림(`.user-perm-summary-card--open`), 클릭 중(`:active`), 열린 상태에서 재클릭(`--open:active`)일 때 `box-shadow` 강도 완화 |

빌드 확인: `npm run build` (`vue-tsc` + Vite) 통과 기준.

### 코드 점검·최적화 요약 (한눈에)

전체 코드를 훑은 뒤 **지금 상태에서 특히 신경 쓰면 좋은 점**만 짧게 정리했습니다.

- **큰 화면 파일**: 대시보드·설비 상세처럼 한 `.vue` 파일이 매우 길면 고치기 어렵습니다. 카드·차트·사이드바처럼 **조각으로 나누는 것**을 권장합니다.
- **차트 두 종류**: 한 화면에 ECharts와 ApexCharts가 같이 쓰이면 **앱 용량·로딩**이 커질 수 있습니다. 가능하면 **하나로 통일**하거나, 꼭 필요한 페이지에서만 불러오는 방식이 좋습니다.
- **안 쓰는 패키지**: `package.json`에 있는 `l` 패키지는 소스에서 쓰이지 않아 **삭제해도 될 가능성**이 큽니다. (삭제 후 `npm run build`로 확인.)
- **보안 습관**: 로그인 뒤 이동할 주소(`redirect`)와 SWMP **iframe 주소**는 사용자 입력이면 **허용 목록(예: https만, 내부 도메인만)** 을 두는 편이 안전합니다.

---

## 변경·분석 기록 (2026-05-14)

당일 코드베이스를 7단계(구조 → 타입 → API → 로딩·에러·빈 상태 → Vue 안티패턴 → 실시간 → 보안) 기준으로 검토한 뒤, 아래 **우선순위 순**으로 실제 수정을 반영했습니다.

### 분석 요약 (검토 시점 기준)

| 구분 | 내용 |
|------|------|
| 구조 | `src/`는 페이지형 `.vue`와 `style.css` 중심이었고, `composables/`, `types/`, `api/` 디렉터리는 없었음. |
| 타입 | 대부분 JavaScript + `<script setup>` — 프로젝트 규칙 대비 TypeScript 미도입 상태였음. |
| API | Axios·인터셉터·백엔드 연동 없음. 대시보드·알람은 정적 목업 데이터. |
| UI 상태 | 알람 등 데이터 fetch UI에 로딩·에러·빈 상태 분기 없음. |
| 라우팅 | `window.location.hash` + `App.vue`의 `v-if` 분기 — Vue Router 미사용. |
| 보안 | SWMP URL이 소스에 하드코딩됨. 토큰 저장 로직 없음. 관리자 화면 가드 없음. |

### 이번에 적용한 수정 (우선순위 반영)

1. **환경 변수 · Axios**
   - `.env.example` 추가: `VITE_API_BASE_URL`, `VITE_USE_MOCK_ALARMS`, `VITE_SWMP_DEFAULT_URL`
   - 공통 실행용 `.env`는 저장소에 포함한다. 개인 PC 전용 값은 `.env.local`에 두고 커밋하지 않는다.
   - `src/api/client.ts`: `import.meta.env.VITE_API_BASE_URL` 기준 `axios.create`
   - `src/api/interceptors.ts`: 요청에 `Authorization: Bearer …` 부착, **401 시 세션 정리 후 `/login`으로 이동** (처리는 인터셉터에만 집중)
   - `src/api/alarmApi.ts`: API URL이 비어 있거나 `VITE_USE_MOCK_ALARMS=true`이면 지연 목업 응답, 그렇지 않으면 `GET /alarms` 호출

2. **Vue Router 4 · Pinia · 라우터 가드**
   - `createWebHashHistory()`로 기존 `#/…` URL과 호환 유지
   - `src/router/index.ts`: 로그인 제외 경로에 `requiresAuth`, `/users`에 `roles: ['admin']`
   - `src/stores/auth.ts`: **accessToken은 `sessionStorage`만 사용**(localStorage 사용 안 함), 역할 `admin` | `operator`
   - `main.ts`에서 `setAuthRouter(router)` 후 인터셉터 등록해 401 시 라우터 이동 가능하도록 연결

3. **TypeScript 진입점·모듈**
   - `index.html` → `/src/main.ts`
   - `tsconfig.json`, `src/vite-env.d.ts`(`.vue` 모듈 선언 포함)
   - `vite.config.ts` + `@` → `src` 별칭
   - 빌드: `vue-tsc --noEmit && vite build`

4. **TanStack Vue Query + 알람 화면 3종 상태**
   - `src/composables/useAlarms.ts`: `refetchInterval: 60_000`
   - `AlarmPage.vue`를 `lang="ts"`로 전환하고 **로딩 / 에러+재시도 / 빈 목록** UI 구현

5. **대형 컴포넌트 분리**
   - `DashboardPage.vue` 등 초대형 SFC 분할은 **이번 범위에서 미완료**(후속 작업으로 README에만 명시). 대신 사이드바 네비 중복을 줄이기 위해 `useAppNav` 도입.

6. **SMWP 하드코딩 제거**
   - `WebScadaOverlay.vue`가 `import.meta.env.VITE_SWMP_DEFAULT_URL`을 기준으로 외부 WebSCADA 화면을 연다.

### 공통 리팩터

- `src/composables/useAppNav.ts`: 기본·라인 상세(`'line'`) 네비 정의 통합
- `src/composables/useLogout.ts`: 로그아웃 시 세션 제거 + 로그인 라우트 이동
- 주요 페이지: `#/…` 앵커 → `RouterLink`, 헤더의 «로그인 화면» → **로그아웃** 버튼
- `LoginPage.vue`: 역할 선택(데모) + `redirect` 쿼리 지원

### 로컬 실행

```bash
npm install
npm run dev
```

- **운영자**로 로그인 후 `/users`(사용자·권한) 접근 시, 라우터 가드에 의해 대시보드로 되돌아갑니다.
- **관리자**로 로그인하면 `/users` 접근이 허용됩니다.

### 남은 권장 과제

- `DashboardPage.vue` 등 **200줄 초과 SFC**를 패널·차트·사이드바 단위로 분할
- 대시보드 지표도 Vue Query + 실 API(또는 SSE)로 이관
- 실제 백엔드 로그인 API와 토큰 갱신·만료 정책 연동
- `vue-tsc` 경고 0을 목표로 나머지 `.vue`에 `lang="ts"` 및 `defineProps`/`defineEmits` 제네릭 점진 적용

---

## 스크립트

| 명령 | 설명 |
|------|------|
| `npm run dev` | Vite 개발 서버 |
| `npm run build` | 타입 검사 후 프로덕션 빌드 |
| `npm run typecheck` | `vue-tsc --noEmit`만 실행 |
| `npm run preview` | 빌드 결과 프리뷰 |

---

## 기술 스택 (현재 `package.json` 기준)

Vue 3, Vite, Pinia, Vue Router 4, Axios, TanStack Vue Query, ApexCharts, Lucide Vue Next

프로젝트 규칙(`.cursor/rules/phm-frontend-standards.mdc`)과의 정합은 위 «남은 권장 과제»를 통해 계속 맞춰 나가면 됩니다.
