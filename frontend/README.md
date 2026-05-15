# UECADA · Frontend

SCADA 통합 대시보드 및 관리 화면을 위한 Vue 기반 프론트엔드 저장소입니다.

실행·빌드는 저장소 루트의 **`UECADA/`** 폴더에서 진행합니다 (`package.json`, `vite.config.js`, `src/` 위치).

## 주요 수정 사항 (`feat/frontend`)

### 대시보드 (`src/components/DashboardPage.vue`)

| 영역 | 내용 |
|------|------|
| **레이아웃** | 좌측(약 2/3): 전체 OEE → 라인별 카드 → 시간별 막대 / 우측(약 1/3): 설비 상태 도넛 → 알람 요약 |
| **전체 OEE** | 전일·전월·전년 비교 배지 단일화, 비교 라벨·숫자 슬롯으로 버튼 전환 시 레이아웃 점프 완화 |
| **라인별 OEE** | 카드형 UI, 스파크라인, **상세 보기 버튼 제거**, 카드 높이·패딩 최적화 |
| **시간별 OEE** | Apex 막대 차트 옵션(범례·그리드·축) 조정으로 플롯 영역 확대, 래퍼 `ResizeObserver`로 패널 높이에 맞춤 |
| **데스크톱 정렬** | `dash-dashboard-fill` + 그리드 `stretch`, 라인·시간별 패널 `flex: 1 1 0`으로 높이 균등 분배, 라인 3열 카드 동일 행 높이(`minmax(0, 1fr)`), 알람 블록 `margin-top: auto`로 하단 정렬 |
| **반응형** | **1180px 이하**에서는 위 확장 레이아웃 규칙 해제, 세로 스택 유지 |

### 공통 스타일 (`src/style.css`)

- 대시보드 셸·헤더 등 글로벌 레이아웃 토큰과 연동되는 스타일 조정

### 기타 페이지 컴포넌트

동일 브랜치에서 아래 파일에 레이아웃·클래스 일관성 관련 수정이 포함될 수 있습니다.

- `AlarmPage.vue`, `CommunityPage.vue`, `EquipmentDetailPage.vue`
- `FactoryLayoutPage.vue`, `LineDetailPage.vue`, `SwmpTestPage.vue`, `UserManagementPage.vue`

## 로컬 실행

```bash
cd UECADA
npm install
npm run dev
```

## 프로덕션 빌드

```bash
cd UECADA
npm run build
```

## 브랜치

- **`feat/frontend`**: 프론트엔드 UI·대시보드 개선 작업

---

저장소: [sky9464881/UECADA](https://github.com/sky9464881/UECADA) · 브랜치 [`feat/frontend`](https://github.com/sky9464881/UECADA/tree/feat/frontend)
