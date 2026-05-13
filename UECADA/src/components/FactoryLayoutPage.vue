<script setup>
import { computed, nextTick, ref } from 'vue'
import {
  AlertCircle,
  AlertTriangle,
  Bell,
  CalendarDays,
  CheckCircle2,
  Circle as IconCircle,
  Factory,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Pentagon as IconPentagon,
  Printer,
  Square as IconSquare,
  Star as IconStar,
  Triangle as IconTriangle,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '레이아웃', icon: MapPinned, href: '#/layout', active: true },
  { label: '설비 제어', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

function shapeLucide(kind) {
  const map = {
    circle: IconCircle,
    triangle: IconTriangle,
    square: IconSquare,
    pentagon: IconPentagon,
    star: IconStar,
  }
  return map[kind] ?? IconCircle
}

/** 라인 3개. 라인당: 주조 1 · 가공 3 · 세척 2 · 검사 2 (공정 순서: 주조→가공→세척→검사) */
const lines = [
  {
    id: 'line-1',
    name: '라인 A',
    area: 'Line-A',
    status: '정상',
    oee: 91,
    uph: 60,
    equipment: 8,
    active: 8,
    alarm: 0,
    equipStatus: { run: 94, stop: 3, wait: 3, stopEnd: 97 },
    balance: 88,
    stations: [90, 92, 88, 91, 86, 89],
    upmh: 420,
    productivity: 94,
    upmhPercent: 88,
    uphPercent: 85,
    routeNote: '주조 → 가공(3) → 세척(2) → 검사(2)',
  },
  {
    id: 'line-2',
    name: '라인 B',
    area: 'Line-B',
    status: '경고',
    oee: 84,
    uph: 60,
    equipment: 8,
    active: 8,
    alarm: 2,
    equipStatus: { run: 82, stop: 8, wait: 10, stopEnd: 90 },
    balance: 81,
    stations: [78, 82, 86, 80, 76, 83],
    upmh: 395,
    productivity: 86,
    upmhPercent: 78,
    uphPercent: 80,
    routeNote: '주조 → 가공(3) → 세척(2) → 검사(2)',
  },
  {
    id: 'line-3',
    name: '라인 C',
    area: 'Line-C',
    status: '정상',
    oee: 88,
    uph: 60,
    equipment: 8,
    active: 8,
    alarm: 0,
    equipStatus: { run: 90, stop: 5, wait: 5, stopEnd: 95 },
    balance: 86,
    stations: [88, 90, 85, 87, 84, 86],
    upmh: 402,
    productivity: 90,
    upmhPercent: 81,
    uphPercent: 82,
    routeNote: '주조 → 가공(3) → 세척(2) → 검사(2)',
  },
]

/** 라인별 동일 구성 8대 · 순서: 주조→가공×3→세척×2→검사×2 */
const equipmentNodes = [1, 2, 3].flatMap((n) => {
  const lineId = `line-${n}`
  const p = `L${n}`
  const castP = 17.8 + n * 0.1
  const rpm = 7050 - n * 80
  const line2Issues = n === 2

  return [
    {
      id: `${p}-CAST-01`,
      name: '주조기 1호',
      type: '주조기',
      shapeKind: 'circle',
      lineId,
      status: '정상',
      main: `압력 ${castP.toFixed(1)}bar · 금형 ${210 + n}℃ · CT 60.0s`,
    },
    {
      id: `${p}-MACH-01`,
      name: '가공기 1호',
      type: '가공기',
      shapeKind: 'triangle',
      lineId,
      status: '정상',
      main: `스핀들 ${rpm + 120}rpm · 공구 ${40 + n * 6}h · CT 20.0s`,
    },
    {
      id: `${p}-MACH-02`,
      name: '가공기 2호',
      type: '가공기',
      shapeKind: 'triangle',
      lineId,
      status: line2Issues ? '주의' : '정상',
      main: line2Issues
        ? '진동 2.3mm/s · 공구 72h · CT 21.2s'
        : `스핀들 ${rpm}rpm · 공구 ${52 + n * 4}h · CT 20.2s`,
    },
    {
      id: `${p}-MACH-03`,
      name: '가공기 3호',
      type: '가공기',
      shapeKind: 'triangle',
      lineId,
      status: '정상',
      main: `스핀들 ${rpm - 100}rpm · 공구 ${48 + n * 3}h · CT 20.4s`,
    },
    {
      id: `${p}-WASH-01`,
      name: '세척기 1호',
      type: '세척기',
      shapeKind: 'square',
      lineId,
      status: '정상',
      main: `세척 농도 ${4.0 + n * 0.05}% · 세척 ${60 + n}℃ · CT 60.0s`,
    },
    {
      id: `${p}-WASH-02`,
      name: '세척기 2호',
      type: '세척기',
      shapeKind: 'square',
      lineId,
      status: line2Issues ? '경고' : '정상',
      main: line2Issues
        ? '농도 편차 · 필터 차압 상한 · CT 62.4s'
        : `세척 농도 ${4.1 + n * 0.04}% · 세척 ${61 + n}℃ · CT 60.2s`,
    },
    {
      id: `${p}-INSP-01`,
      name: '검사기 1호',
      type: '검사기',
      shapeKind: 'star',
      lineId,
      status: '정상',
      main: `치수 목표 24.0${n}mm · 현재 24.0${n}mm · CT 30.0s`,
    },
    {
      id: `${p}-INSP-02`,
      name: '검사기 2호',
      type: '검사기',
      shapeKind: 'star',
      lineId,
      status: '정상',
      main: `치수 목표 18.5mm · 현재 18.5${n + 1}mm · CT 30.0s`,
    },
  ]
})

const allEquipmentNodes = equipmentNodes

const selectedEquipmentId = ref('L1-CAST-01')
const selectedLineId = ref('line-1')
const isReportOpen = ref(false)

const selectedEquipment = computed(() =>
  allEquipmentNodes.find((node) => node.id === selectedEquipmentId.value),
)

const selectedLine = computed(() => lines.find((line) => line.id === selectedLineId.value))

const selectedEquipmentLine = computed(() => {
  const eq = selectedEquipment.value
  if (!eq) return null
  return lines.find((line) => line.id === eq.lineId) ?? null
})

const selectedEquipmentMetrics = computed(() => selectedEquipment.value?.main.split(' · ') ?? [])

const lineEquipments = (lineId) => equipmentNodes.filter((node) => node.lineId === lineId)

/** 공정 단계별 노드 (세척기는 한 단계에 통합) */
const flowStages = computed(() => {
  const lineId = selectedLineId.value
  const eqs = lineEquipments(lineId)
  const cast = eqs.filter((e) => e.type === '주조기')
  const mach = eqs.filter((e) => e.type === '가공기').sort((a, b) => a.id.localeCompare(b.id))
  const wash = eqs.filter((e) => e.type === '세척기').sort((a, b) => a.id.localeCompare(b.id))
  const insp = eqs.filter((e) => e.type === '검사기').sort((a, b) => a.id.localeCompare(b.id))
  return [
    { key: 'cast', label: '주조', nodes: cast },
    { key: 'mach', label: '가공', nodes: mach },
    { key: 'wash', label: '세척', nodes: wash },
    { key: 'insp', label: '검사', nodes: insp },
  ]
})

function selectLine(lineId) {
  selectedLineId.value = lineId
  const first = equipmentNodes.find((e) => e.lineId === lineId)
  if (first) selectedEquipmentId.value = first.id
  nextTick(() => {
    const el = mapScrollEl.value
    if (el) {
      el.scrollTop = 0
      el.scrollLeft = 0
    }
  })
}

function openReport() {
  isReportOpen.value = true
}

function closeReport() {
  isReportOpen.value = false
}

function printReport() {
  nextTick(() => {
    window.print()
  })
}

const statusIcon = (status) => {
  if (status === '정상') return CheckCircle2
  if (status === '주의') return AlertCircle
  return AlertTriangle
}

const reportRows = computed(() => lineEquipments(selectedLineId.value))

/** 보고서 표기용 도형 마커 (주조=원, 가공=정삼각, 세척=사각, 조립=오각형 대체 기호, 검사=별) */
function reportEquipMarker(type) {
  const m = {
    주조기: '○',
    가공기: '△',
    세척기: '□',
    조립기: '⬟',
    검사기: '☆',
  }
  return m[type] ?? '—'
}

const reportRevisionHistory = [
  {
    rev: 'Rev.01',
    date: '2026-05-11',
    note: '최초 작성 · UECADA 공장 레이아웃 화면 연동 출력',
    author: '생산관리',
  },
]

const reportSpecialNotes = computed(() => {
  const ln = selectedLine.value
  if (!ln) return '—'
  const parts = []
  if (ln.status === '경고') {
    parts.push(
      '해당 라인은 현재 「경고」 상태입니다. 세부 원인은 설비별 요약 및 알람 이력 화면과 대조하여 확인하시기 바랍니다.',
    )
  }
  if (ln.alarm > 0) {
    parts.push(`발생 알람 건수 ${ln.alarm}건 (실운영 시 알람·이력 메뉴와 연계 확인)`)
  }
  if (!parts.length) {
    parts.push('별도 특이사항 없음 (본 출력물은 데모·모니터링 데이터 기준입니다).')
  }
  return parts.join('\n')
})

/** 지도 확대 단계 (0.85 ~ 1.45) */
const ZOOM_STEPS = [0.85, 1, 1.15, 1.3, 1.45]
const zoomStepIndex = ref(1)
const layoutZoom = computed(() => ZOOM_STEPS[zoomStepIndex.value])

const mapScrollEl = ref(null)

const zoomPercentLabel = computed(() => `${Math.round(layoutZoom.value * 100)}%`)

const zoomIn = () => {
  zoomStepIndex.value = Math.min(ZOOM_STEPS.length - 1, zoomStepIndex.value + 1)
}

const zoomOut = () => {
  zoomStepIndex.value = Math.max(0, zoomStepIndex.value - 1)
}

const zoomFit = () => {
  zoomStepIndex.value = 1
  nextTick(() => {
    const el = mapScrollEl.value
    if (el) {
      el.scrollTop = 0
      el.scrollLeft = 0
    }
  })
}

const blueprintStyle = computed(() => ({
  zoom: layoutZoom.value,
}))
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar" aria-label="주요 메뉴">
      <a class="dashboard-brand" href="#/dashboard">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스카다</small>
        </span>
      </a>

      <nav class="dashboard-nav">
        <a
          v-for="item in navItems"
          :key="item.label"
          :class="{ active: item.active }"
          :href="item.href"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </a>
      </nav>

      <div class="sidebar-status">
        <span>관리자</span>
        <strong>김관리</strong>
        <p>라인 A/B/C 3개 · 라인당 주조 1 · 가공 3 · 세척 2 · 검사 2 (총 24대) · 주조→가공→세척→검사</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Factory Layout</p>
          <h1>공장 레이아웃</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="dashboard-panel factory-page-panel">
        <div class="factory-line-toolbar no-print-factory">
          <div class="factory-line-btn-group" role="tablist" aria-label="라인 선택">
            <button
              v-for="line in lines"
              :key="line.id"
              type="button"
              role="tab"
              :aria-selected="selectedLineId === line.id"
              :class="['factory-line-pick', line.status, { active: selectedLineId === line.id }]"
              @click="selectLine(line.id)"
            >
              <span class="factory-line-pick-area">{{ line.area }}</span>
              <strong>{{ line.name }}</strong>
              <span class="factory-line-pick-oee">OEE {{ line.oee }}%</span>
            </button>
          </div>
          <div class="factory-toolbar-actions">
            <div class="factory-status-legend" aria-label="설비 상태 범례">
              <span class="normal">정상</span>
              <span class="caution">주의</span>
              <span class="warn-strong">경고</span>
            </div>
            <button type="button" class="factory-report-btn" @click="openReport">
              <Printer :size="17" />
              <span>레포트·인쇄</span>
            </button>
          </div>
        </div>

        <div class="factory-layout-workspace">
          <div class="factory-map-stage" aria-label="공장 레이아웃 지도">
            <div class="factory-map-toolbar" aria-label="지도 도구">
              <button
                type="button"
                aria-label="확대"
                :disabled="zoomStepIndex >= ZOOM_STEPS.length - 1"
                @click="zoomIn"
              >
                +
              </button>
              <button type="button" aria-label="축소" :disabled="zoomStepIndex <= 0" @click="zoomOut">-</button>
              <button type="button" aria-label="맞춤(100% 및 스크롤 초기화)" @click="zoomFit">□</button>
              <span class="factory-zoom-label" aria-live="polite">{{ zoomPercentLabel }}</span>
            </div>

            <div ref="mapScrollEl" class="factory-map-scroll">
              <div class="factory-blueprint factory-blueprint-flow" :style="blueprintStyle">
                <div v-if="selectedLine" class="factory-flow-board" aria-label="선택 라인 공정 구성">
                  <div class="factory-flow-board-head">
                    <div class="factory-flow-board-title">
                      <span class="factory-flow-chip">{{ selectedLine.area }}</span>
                      <strong>{{ selectedLine.name }}</strong>
                    </div>
                    <div class="factory-flow-board-meta">
                      <span :class="['line-state', selectedLine.status]">{{ selectedLine.status }}</span>
                      <span class="factory-flow-route">{{ selectedLine.routeNote }}</span>
                    </div>
                  </div>

                  <div class="factory-flow-stages">
                    <template v-for="(stage, si) in flowStages" :key="stage.key">
                      <div v-if="si > 0" class="factory-flow-arrow" aria-hidden="true">→</div>
                      <section class="factory-flow-stage" :aria-label="stage.label">
                        <h4 class="factory-flow-stage-title">{{ stage.label }}</h4>
                        <div class="factory-flow-nodes">
                          <button
                            v-for="node in stage.nodes"
                            :key="node.id"
                            type="button"
                            :class="[
                              'factory-shape-node',
                              `factory-shape-node--${node.shapeKind}`,
                              node.status,
                              { selected: node.id === selectedEquipmentId },
                            ]"
                            @click="selectedEquipmentId = node.id"
                          >
                            <span class="factory-shape-node-icon" :class="`factory-shape-kind-${node.shapeKind}`">
                              <component :is="shapeLucide(node.shapeKind)" :size="30" :stroke-width="2.2" />
                            </span>
                            <component :is="statusIcon(node.status)" class="factory-shape-node-status" :size="16" />
                            <strong>{{ node.name }}</strong>
                            <small>{{ node.id }}</small>
                          </button>
                        </div>
                      </section>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <aside v-if="selectedEquipment" class="factory-detail-pane" aria-label="설비 상세 정보">
            <div class="factory-detail-head">
              <Factory :size="22" />
              <div>
                <p class="panel-kicker">설비 상세 정보</p>
                <h3>{{ selectedEquipment.name }}</h3>
              </div>
              <span :class="['line-state', selectedEquipment.status]">{{ selectedEquipment.status }}</span>
            </div>

            <dl class="factory-detail-list">
              <div>
                <dt>위치</dt>
                <dd>{{ selectedEquipmentLine?.name }} · {{ selectedEquipmentLine?.area }}</dd>
              </div>
              <div>
                <dt>설비 유형</dt>
                <dd>{{ selectedEquipment.type }}</dd>
              </div>
              <div>
                <dt>설비 ID</dt>
                <dd>{{ selectedEquipment.id }}</dd>
              </div>
            </dl>

            <div class="factory-detail-metrics">
              <article v-for="metric in selectedEquipmentMetrics" :key="metric">
                <span>{{ metric }}</span>
              </article>
            </div>

            <div class="factory-detail-alarm">
              <strong>최근 상태</strong>
              <p>{{ selectedEquipment.main }}</p>
            </div>
          </aside>
        </div>

        <Teleport to="body">
          <div
            v-if="isReportOpen && selectedLine"
            class="factory-report-portal"
          >
            <div class="factory-report-backdrop" @click.self="closeReport">
            <article
              class="factory-report-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="factory-report-title"
            >
              <header class="factory-report-actions factory-report-no-print">
                <button type="button" class="factory-report-print" @click="printReport">
                  <Printer :size="17" />
                  인쇄
                </button>
                <button type="button" class="factory-report-close" aria-label="닫기" @click="closeReport">
                  닫기
                </button>
              </header>

              <div class="factory-report-dialog-scroll">
                <div id="factory-print-root" class="factory-report-sheet factory-report-k-doc">
                  <header class="factory-report-k-doc-head">
                    <div class="factory-report-k-title-block">
                      <p class="factory-report-k-doc-label">생산관리·현장설비 (SCADA 연계)</p>
                      <h1 id="factory-report-title" class="factory-report-k-doc-title">
                        라인 공정 구성 보고서
                      </h1>
                      <p class="factory-report-k-doc-meta-line">
                        문서번호 &nbsp;UEC-FM-LAYOUT-{{ selectedLine.id }} &nbsp;·&nbsp; 발행일자 &nbsp;2026년 5월 11일
                      </p>
                      <p class="factory-report-k-doc-meta-line">
                        대상 &nbsp;{{ selectedLine.area }} &nbsp;{{ selectedLine.name }} &nbsp;·&nbsp; 라인상태
                        &nbsp;<strong>{{ selectedLine.status }}</strong>
                      </p>
                    </div>

                    <table class="factory-report-k-approval" aria-label="결재란">
                      <tbody>
                        <tr>
                          <th scope="row" rowspan="4" class="factory-report-k-approval-side">결재</th>
                          <th scope="col">작성</th>
                          <th scope="col">검토</th>
                          <th scope="col">승인</th>
                        </tr>
                        <tr class="factory-report-k-approval-sign">
                          <td></td>
                          <td></td>
                          <td></td>
                        </tr>
                        <tr class="factory-report-k-approval-sign">
                          <td></td>
                          <td></td>
                          <td></td>
                        </tr>
                        <tr class="factory-report-k-approval-date">
                          <td>/&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
                          <td>/&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
                          <td>/&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
                        </tr>
                      </tbody>
                    </table>
                  </header>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">1. 문서 개요</h2>
                    <div class="factory-report-k-body">
                      <h3 class="factory-report-k-h3">가. 목적</h3>
                      <p>
                        본 보고서는 선택된 생산 라인에 대한 공정 흐름·설비 배치·가동 상태를 한 장으로 정리하여,
                        생산·설비·품질 관련 부서 및 경영 지원 부서가 동일한 정보를 공유하고 보고·결재 절차에
                        활용할 수 있도록 하기 위하여 작성합니다.
                      </p>
                      <h3 class="factory-report-k-h3">나. 적용 범위</h3>
                      <p>
                        적용 범위는 <strong>{{ selectedLine.area }}</strong> 소속
                        <strong>{{ selectedLine.name }}</strong> 에 한정하며, 동일 데이터 기준으로 화면 출력 및
                        PDF·인쇄 보관이 가능합니다.
                      </p>
                      <h3 class="factory-report-k-h3">다. 작성 기준</h3>
                      <p>
                        작성 기준 시각: <strong>2026-05-11 12:40</strong> (시스템 조회 시각·데모 데이터). 실운영 시에는
                        출력 시점의 최신 스냅샷으로 자동 반영되도록 연동합니다.
                      </p>
                    </div>
                  </section>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">2. 라인 및 공정 개요</h2>
                    <div class="factory-report-k-body">
                      <p>
                        본 라인의 공정 순서는 아래와 같습니다.
                      </p>
                      <p class="factory-report-k-emphasis">
                        {{ selectedLine.routeNote }}
                      </p>
                      <h3 class="factory-report-k-h3">가. 도형 마커 (설비 유형 표기)</h3>
                      <p class="factory-report-k-marker-line">
                        <span><strong>○</strong> 원형 — 주조기</span>
                        <span><strong>△</strong> 정삼각형 — 가공기</span>
                        <span><strong>□</strong> 사각형 — 세척기</span>
                        <span><strong>⬟</strong> 오각형 대체 기호 — 조립기 (본 레이아웃 공정에는 미포함, 참고)</span>
                        <span><strong>☆</strong> 별형 — 검사기</span>
                      </p>
                    </div>
                  </section>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">3. 공정·설비 구성</h2>
                    <div class="factory-report-k-body">
                      <ol class="factory-report-k-outline-list">
                        <li v-for="(stage, idx) in flowStages" :key="`doc-${stage.key}`">
                          <span class="factory-report-k-li-title"
                            >{{ idx + 1 }}) {{ stage.label }} 공정</span
                          >
                          <span class="factory-report-k-li-desc">
                            {{
                              stage.nodes
                                .map((n) => `${reportEquipMarker(n.type)} ${n.name}`)
                                .join(' · ')
                            }}
                          </span>
                        </li>
                      </ol>
                    </div>
                  </section>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">4. 설비별 현황</h2>
                    <div class="factory-report-k-body">
                      <table class="factory-report-k-table">
                        <thead>
                          <tr>
                            <th scope="col">순번</th>
                            <th scope="col">마커</th>
                            <th scope="col">설비명</th>
                            <th scope="col">유형</th>
                            <th scope="col">상태</th>
                            <th scope="col">운전 요약</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, ri) in reportRows" :key="row.id">
                            <td>{{ ri + 1 }}</td>
                            <td class="factory-report-k-cell-center">{{ reportEquipMarker(row.type) }}</td>
                            <td>{{ row.name }}</td>
                            <td>{{ row.type }}</td>
                            <td>
                              <span :class="['factory-report-k-status', row.status]">{{ row.status }}</span>
                            </td>
                            <td>{{ row.main }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </section>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">5. 라인 지표 및 특이사항</h2>
                    <div class="factory-report-k-body">
                      <h3 class="factory-report-k-h3">가. 주요 지표</h3>
                      <table class="factory-report-k-table factory-report-k-table-metrics">
                        <thead>
                          <tr>
                            <th scope="col">항목</th>
                            <th scope="col">수치</th>
                            <th scope="col">비고</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr>
                            <td>OEE (종합설비효율)</td>
                            <td>{{ selectedLine.oee }}%</td>
                            <td>라인 설비 통합 기준</td>
                          </tr>
                          <tr>
                            <td>설비 대수</td>
                            <td>{{ selectedLine.equipment }}대</td>
                            <td>주조·가공·세척·검사 포함</td>
                          </tr>
                          <tr>
                            <td>알람 건수</td>
                            <td>{{ selectedLine.alarm }}건</td>
                            <td>현 시점 스냅샷</td>
                          </tr>
                          <tr>
                            <td>밸런스</td>
                            <td>{{ selectedLine.balance }}%</td>
                            <td>공정 간 부하 균형 지표</td>
                          </tr>
                          <tr>
                            <td>생산성</td>
                            <td>{{ selectedLine.productivity }}%</td>
                            <td>표준 대비</td>
                          </tr>
                          <tr>
                            <td>UPMH</td>
                            <td>{{ selectedLine.upmh.toLocaleString() }}</td>
                            <td>시간당 처리량 지표</td>
                          </tr>
                          <tr>
                            <td>UPH</td>
                            <td>{{ selectedLine.uph.toLocaleString() }}</td>
                            <td>시간당 생산량</td>
                          </tr>
                        </tbody>
                      </table>

                      <h3 class="factory-report-k-h3">나. 특이사항</h3>
                      <p class="factory-report-k-pre">{{ reportSpecialNotes }}</p>
                    </div>
                  </section>

                  <section class="factory-report-k-section">
                    <h2 class="factory-report-k-h2">6. 개정 이력</h2>
                    <div class="factory-report-k-body">
                      <table class="factory-report-k-table">
                        <thead>
                          <tr>
                            <th scope="col">버전</th>
                            <th scope="col">일자</th>
                            <th scope="col">개정 내용</th>
                            <th scope="col">작성자</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="rev in reportRevisionHistory" :key="rev.rev">
                            <td>{{ rev.rev }}</td>
                            <td>{{ rev.date }}</td>
                            <td>{{ rev.note }}</td>
                            <td>{{ rev.author }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </section>

                  <footer class="factory-report-k-footer">
                    <p>
                      본 문서는 <strong>UECADA</strong> 현장 모니터링 화면에서 생성된 출력물이며, 결재·보관 시
                      사내 문서 규정을 준용합니다. (데모 데이터 포함 가능)
                    </p>
                  </footer>
                </div>
              </div>
            </article>
            </div>
          </div>
        </Teleport>
      </section>
    </section>
  </main>
</template>
