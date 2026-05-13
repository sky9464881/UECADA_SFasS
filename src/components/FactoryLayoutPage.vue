<script setup>
import { computed, nextTick, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  Bell,
  CalendarDays,
  CheckCircle2,
  Factory,
  Gauge,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  TrendingUp,
  Users,
  Wrench,
  X,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout', active: true },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const leftRooms = ['자재 창고', '부품 창고', '사무실']
const rightRooms = ['검사실', '포장 구역', '출하장']

/** 라인 3개. 라인당: 주조 1EA · 가공 3EA · 세척 1EA · 조립 2EA · 검사 2EA (공정 순서: 주조→가공→세척→조립→검사) */
const lines = [
  {
    id: 'line-1',
    name: '라인 1',
    area: 'Line-1',
    status: '정상',
    oee: 91,
    uph: 60,
    equipment: 9,
    active: 9,
    alarm: 0,
    equipStatus: { run: 94, stop: 3, wait: 3, stopEnd: 97 },
    balance: 88,
    stations: [90, 92, 88, 91, 86, 89],
    upmh: 420,
    productivity: 94,
    upmhPercent: 88,
    uphPercent: 85,
    routeNote: '주조 1 → 가공 3 → 세척 1 → 조립 2 → 검사 2',
  },
  {
    id: 'line-2',
    name: '라인 2',
    area: 'Line-2',
    status: '경고',
    oee: 84,
    uph: 60,
    equipment: 9,
    active: 9,
    alarm: 2,
    equipStatus: { run: 82, stop: 8, wait: 10, stopEnd: 90 },
    balance: 81,
    stations: [78, 82, 86, 80, 76, 83],
    upmh: 395,
    productivity: 86,
    upmhPercent: 78,
    uphPercent: 80,
    routeNote: '주조 1 → 가공 3 → 세척 1 → 조립 2 → 검사 2',
  },
  {
    id: 'line-3',
    name: '라인 3',
    area: 'Line-3',
    status: '정상',
    oee: 88,
    uph: 60,
    equipment: 9,
    active: 9,
    alarm: 0,
    equipStatus: { run: 90, stop: 5, wait: 5, stopEnd: 95 },
    balance: 86,
    stations: [88, 90, 85, 87, 84, 86],
    upmh: 402,
    productivity: 90,
    upmhPercent: 81,
    uphPercent: 82,
    routeNote: '주조 1 → 가공 3 → 세척 1 → 조립 2 → 검사 2',
  },
]

/** 라인별 동일 구성 9대. 설비 ID는 라인 구분을 위해 L{n}- 접두사 사용 */
const equipmentNodes = [1, 2, 3].flatMap((n) => {
  const lineId = `line-${n}`
  const p = `L${n}`
  const castP = 17.8 + n * 0.1
  const rpm = 7050 - n * 80
  const line2Warn = n === 2

  return [
    {
      id: `${p}-CAST-01`,
      name: '주조기 1호',
      type: '주조기',
      lineId,
      status: '정상',
      main: `압력 ${castP.toFixed(1)}bar · 금형 ${210 + n}℃ · CT 60.0s`,
    },
    {
      id: `${p}-MACH-01`,
      name: '가공기 1호',
      type: '가공기',
      lineId,
      status: '정상',
      main: `스핀들 ${rpm + 120}rpm · 공구 ${40 + n * 6}h · CT 20.0s`,
    },
    {
      id: `${p}-MACH-02`,
      name: '가공기 2호',
      type: '가공기',
      lineId,
      status: line2Warn ? '경고' : '정상',
      main: line2Warn
        ? '진동 2.3mm/s · 공구 72h · CT 21.2s'
        : `스핀들 ${rpm}rpm · 공구 ${52 + n * 4}h · CT 20.2s`,
    },
    {
      id: `${p}-MACH-03`,
      name: '가공기 3호',
      type: '가공기',
      lineId,
      status: '정상',
      main: `스핀들 ${rpm - 100}rpm · 공구 ${48 + n * 3}h · CT 20.4s`,
    },
    {
      id: `${p}-WASH-01`,
      name: '세척기 1호',
      type: '세척기',
      lineId,
      status: '정상',
      main: `세척 농도 ${4.0 + n * 0.05}% · 세척 ${60 + n}℃ · CT 60.0s`,
    },
    {
      id: `${p}-ASM-01`,
      name: '조립기 1호',
      type: '조립기',
      lineId,
      status: '정상',
      main: `체결토크 ${38 + n}Nm · 체결각도 ${86 + n}° · CT 30.0s`,
    },
    {
      id: `${p}-ASM-02`,
      name: '조립기 2호',
      type: '조립기',
      lineId,
      status: line2Warn ? '경고' : '정상',
      main: line2Warn
        ? '체결각도 94° · 체결토크 편차 · CT 30.8s'
        : `체결토크 ${40 + n}Nm · 체결각도 ${88 + n}° · CT 30.1s`,
    },
    {
      id: `${p}-INSP-01`,
      name: '검사기 1호',
      type: '검사기',
      lineId,
      status: '정상',
      main: `치수 목표 24.0${n}mm · 현재 24.0${n}mm · CT 30.0s`,
    },
    {
      id: `${p}-INSP-02`,
      name: '검사기 2호',
      type: '검사기',
      lineId,
      status: '정상',
      main: `치수 목표 18.5mm · 현재 18.5${n + 1}mm · CT 30.0s`,
    },
  ]
})

const allEquipmentNodes = equipmentNodes

const selectedEquipmentId = ref('L1-MACH-01')
const selectedLineId = ref(null)
const isLinePopupOpen = ref(false)

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

const openLinePopup = (lineId) => {
  selectedLineId.value = lineId
  isLinePopupOpen.value = true
}

const closeLinePopup = () => {
  isLinePopupOpen.value = false
}

const statusIcon = (status) => {
  if (status === '정상') return CheckCircle2
  return AlertTriangle
}

/** 지도 확대 단계 (0.85 ~ 1.45). 라인 내 설비는 항상 2줄(5+4) — 가로로 길어지면 스크롤 */
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
        <p>라인 3개 · 라인당 주조 1 · 가공 3 · 세척 1 · 조립 2 · 검사 2 (총 27대)</p>
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
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Plant Equipment Position</p>
            <h2>공장 레이아웃 기반 설비 위치 표</h2>
            <p class="factory-page-spec-line">
              라인 3개 · 각 라인 동일: 주조기 1EA, 가공기 3EA, 세척기 1EA, 조립기 2EA, 검사기 2EA (라인당 9대, 전체 27대) · 순서:
              주조 → 가공 → 세척 → 조립 → 검사
            </p>
          </div>
          <div class="factory-status-legend" aria-label="설비 상태 범례">
            <span class="normal">정상</span>
            <span class="warning">경고</span>
            <span class="abnormal">이상</span>
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
            <div class="factory-blueprint" :style="blueprintStyle">
              <aside class="factory-side-rooms left" aria-label="좌측 구역">
                <span v-for="room in leftRooms" :key="room">{{ room }}</span>
              </aside>

              <div class="factory-line-lanes" aria-label="라인별 설비 배치">
                <article
                  v-for="line in lines"
                  :key="line.id"
                  :class="['factory-line-row', line.status, { selected: line.id === selectedLineId }]"
                  role="button"
                  tabindex="0"
                  @click="openLinePopup(line.id)"
                  @keydown.enter="openLinePopup(line.id)"
                  @keydown.space.prevent="openLinePopup(line.id)"
                >
                  <div class="factory-line-label">
                    <span>{{ line.area }}</span>
                    <strong>{{ line.name }}</strong>
                    <p v-if="line.routeNote" class="factory-line-route-note">{{ line.routeNote }}</p>
                  </div>

                  <div class="factory-conveyor factory-conveyor-dense">
                    <span class="conveyor-rail"></span>
                    <button
                      v-for="node in lineEquipments(line.id)"
                      :key="`${line.id}-${node.id}`"
                      :class="['factory-equipment-node', node.status, { selected: node.id === selectedEquipmentId }]"
                      type="button"
                      @click.stop="selectedEquipmentId = node.id"
                    >
                      <component :is="statusIcon(node.status)" :size="18" />
                      <strong>{{ node.name }}</strong>
                      <small>{{ node.id }}</small>
                    </button>
                  </div>

                  <div class="factory-line-meta">
                    <span :class="['line-state', line.status]">{{ line.status }}</span>
                    <b>OEE {{ line.oee }}%</b>
                  </div>
                </article>
              </div>

              <aside class="factory-side-rooms right" aria-label="우측 구역">
                <span v-for="room in rightRooms" :key="room">{{ room }}</span>
              </aside>
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

        <div
          v-if="selectedLine && isLinePopupOpen"
          class="line-modal-backdrop"
          @click.self="closeLinePopup"
        >
          <article
            class="line-detail-modal"
            role="dialog"
            aria-modal="true"
            :aria-label="`${selectedLine.name} 상세보기`"
          >
            <div class="line-detail-popup-head">
              <div>
                <p class="panel-kicker">현재 라인</p>
                <h2>{{ selectedLine.name }}</h2>
                <p class="line-current-subtitle">
                  {{ selectedLine.area }} · 설비 {{ selectedLine.equipment }}대 · 현재 알람 {{ selectedLine.alarm }}건
                </p>
              </div>
              <div class="line-modal-actions">
                <span :class="['line-state', selectedLine.status]">{{ selectedLine.status }}</span>
                <button class="line-modal-close" type="button" aria-label="팝업 닫기" @click="closeLinePopup">
                  <X :size="18" />
                </button>
              </div>
            </div>

            <section class="line-popup-layout-panel" aria-label="현재 라인의 레이아웃">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Current Line Layout</p>
                  <h3>현재 라인의 레이아웃</h3>
                </div>
                <Factory :size="22" />
              </div>

              <div class="line-popup-mini-layout">
                <div class="line-popup-mini-label">
                  <span>{{ selectedLine.area }}</span>
                  <strong>{{ selectedLine.name }}</strong>
                  <b>OEE {{ selectedLine.oee }}%</b>
                </div>

                <div class="line-popup-mini-conveyor line-popup-mini-conveyor-dense">
                  <span class="conveyor-rail"></span>
                  <button
                    v-for="node in lineEquipments(selectedLine.id)"
                    :key="`popup-${selectedLine.id}-${node.id}`"
                    :class="['line-popup-mini-node', node.status, { selected: node.id === selectedEquipmentId }]"
                    type="button"
                    @click="selectedEquipmentId = node.id"
                  >
                    <component :is="statusIcon(node.status)" :size="18" />
                    <strong>{{ node.name }}</strong>
                    <small>{{ node.id }} · {{ node.status }}</small>
                  </button>
                </div>
              </div>
            </section>

            <div class="line-popup-quadrant-grid" aria-label="라인 현재상태 상세내역">
              <section class="dashboard-panel line-oee-analysis-panel line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line OEE</p>
                    <h3>종합 설비 효율 라인</h3>
                    <p class="line-popup-chart-note">
                      라인별 설비 기준 종합 설비 효율을 나타내는 원 그래프
                    </p>
                  </div>
                  <Gauge :size="22" />
                </div>
                <div class="line-oee-donut-grid line-popup-single-metric">
                  <article class="line-oee-donut-card">
                    <div
                      class="line-analysis-donut"
                      :style="{ '--value': `${selectedLine.oee}%` }"
                    >
                      <strong>{{ selectedLine.oee }}%</strong>
                    </div>
                    <div>
                      <h3>{{ selectedLine.name }}</h3>
                      <p>{{ selectedLine.equipment }}대 설비 기준 종합 설비 효율</p>
                    </div>
                  </article>
                </div>
              </section>

              <section class="dashboard-panel line-status-analysis-panel line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line Equipment Status</p>
                    <h3>라인에 해당하는 설비 상태 분포도</h3>
                    <p class="line-popup-chart-note">
                      라인 별 설비 상태: 가동, 정지, 대기
                    </p>
                  </div>
                  <Activity :size="22" />
                </div>
                <div class="line-status-donut-grid line-popup-single-metric">
                  <article class="line-status-card">
                    <div
                      class="line-status-donut"
                      :style="{
                        '--run-end': `${selectedLine.equipStatus.run}%`,
                        '--stop-end': `${selectedLine.equipStatus.stopEnd}%`,
                      }"
                    >
                      <strong>{{ selectedLine.equipment }}대</strong>
                    </div>
                    <div class="line-status-info">
                      <h3>{{ selectedLine.name }}</h3>
                      <div class="line-status-legend">
                        <span class="run">가동 {{ selectedLine.equipStatus.run }}%</span>
                        <span class="stop">정지 {{ selectedLine.equipStatus.stop }}%</span>
                        <span class="wait">대기 {{ selectedLine.equipStatus.wait }}%</span>
                      </div>
                    </div>
                  </article>
                </div>
              </section>

              <article class="dashboard-panel line-balance-panel line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line Balancing</p>
                    <h3>라인밸런싱</h3>
                    <p class="line-popup-chart-note">라인 설비 간 밸런싱 분석</p>
                  </div>
                  <TrendingUp :size="22" />
                </div>
                <div class="line-balance-chart-grid line-popup-balance-single">
                  <article class="line-balance-chart">
                    <div class="line-chart-head">
                      <strong>{{ selectedLine.name }}</strong>
                      <span>{{ selectedLine.balance }}%</span>
                    </div>
                    <div class="line-station-bars">
                      <i
                        v-for="(value, index) in selectedLine.stations"
                        :key="`${selectedLine.id}-st-${index}`"
                        :style="{ height: `${value}%` }"
                      >
                        <b>{{ index + 1 }}</b>
                      </i>
                    </div>
                  </article>
                </div>
              </article>

              <article class="dashboard-panel productivity-panel line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">UPMH / UPH</p>
                    <h3>라인별 생산성 분석</h3>
                    <p class="line-popup-chart-note">UPMH, UPH 기준 생산성</p>
                  </div>
                  <Factory :size="22" />
                </div>
                <div class="productivity-chart line-popup-productivity-single">
                  <article>
                    <div class="productivity-label">
                      <strong>{{ selectedLine.name }}</strong>
                      <span>{{ selectedLine.productivity }}%</span>
                    </div>
                    <div class="productivity-bars">
                      <div>
                        <i :style="{ width: `${selectedLine.upmhPercent}%` }"></i>
                        <span>UPMH {{ selectedLine.upmh.toLocaleString() }}</span>
                      </div>
                      <div>
                        <i :style="{ width: `${selectedLine.uphPercent}%` }"></i>
                        <span>UPH {{ selectedLine.uph.toLocaleString() }}</span>
                      </div>
                    </div>
                  </article>
                </div>
              </article>
            </div>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>
