<script setup>
import { computed, ref } from 'vue'
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
  ShieldCheck,
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
  { label: '사용자 관리', icon: Users, href: '#/users' },
  { label: '권한 설정', icon: ShieldCheck, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const leftRooms = ['자재 창고', '부품 창고', '사무실']
const rightRooms = ['검사실', '포장 구역', '출하장']

const lines = [
  {
    id: 'line-a',
    name: 'Line A 주조',
    area: 'A-Block',
    status: '정상',
    oee: 91,
    uph: 520,
    equipment: 24,
    active: 23,
    alarm: 2,
    left: '5%',
    top: '9%',
    width: '35%',
    height: '34%',
    equipStatus: { run: 92, stop: 4, wait: 4, stopEnd: 96 },
    balance: 88,
    stations: [86, 92, 88, 91, 83, 89],
    upmh: 1240,
    productivity: 96,
    upmhPercent: 89,
    uphPercent: 87,
  },
  {
    id: 'line-b',
    name: 'Line B 가공',
    area: 'B-Block',
    status: '경고',
    oee: 84,
    uph: 475,
    equipment: 38,
    active: 35,
    alarm: 4,
    left: '49%',
    top: '10%',
    width: '46%',
    height: '38%',
    equipStatus: { run: 82, stop: 6, wait: 12, stopEnd: 88 },
    balance: 81,
    stations: [78, 82, 89, 80, 76, 83],
    upmh: 1080,
    productivity: 86,
    upmhPercent: 77,
    uphPercent: 79,
  },
  {
    id: 'line-c',
    name: 'Line C 조립',
    area: 'C-Block',
    status: '정상',
    oee: 88,
    uph: 498,
    equipment: 42,
    active: 39,
    alarm: 3,
    left: '7%',
    top: '55%',
    width: '56%',
    height: '36%',
    equipStatus: { run: 86, stop: 5, wait: 9, stopEnd: 91 },
    balance: 86,
    stations: [87, 84, 90, 88, 82, 85],
    upmh: 1160,
    productivity: 92,
    upmhPercent: 83,
    uphPercent: 83,
  },
  {
    id: 'line-d',
    name: 'Line D 검사',
    area: 'D-Block',
    status: '이상',
    oee: 79,
    uph: 431,
    equipment: 24,
    active: 19,
    alarm: 5,
    left: '69%',
    top: '57%',
    width: '26%',
    height: '32%',
    equipStatus: { run: 76, stop: 12, wait: 12, stopEnd: 88 },
    balance: 76,
    stations: [71, 75, 79, 73, 82, 77],
    upmh: 940,
    productivity: 78,
    upmhPercent: 67,
    uphPercent: 72,
  },
]

const equipmentNodes = [
  {
    id: 'CAST-01',
    name: '주조기 1호',
    type: '주조기',
    lineId: 'line-a',
    status: '정상',
    left: '13%',
    top: '22%',
    main: '압력 17.9bar · 용탕 672℃ · CT 41.8s',
  },
  {
    id: 'CAST-02',
    name: '주조기 2호',
    type: '주조기',
    lineId: 'line-a',
    status: '경고',
    left: '28%',
    top: '23%',
    main: '용탕온도 681℃ · 금형 216℃ · CT 42.1s',
  },
  {
    id: 'CAST-03',
    name: '주조기 3호',
    type: '주조기',
    lineId: 'line-a',
    status: '정상',
    left: '36%',
    top: '23%',
    main: '압력 18.1bar · 용탕 668℃ · CT 41.4s',
  },
  {
    id: 'CAST-04',
    name: '주조기 4호',
    type: '주조기',
    lineId: 'line-a',
    status: '정상',
    left: '43%',
    top: '23%',
    main: '압력 17.6bar · 금형 211℃ · CT 41.9s',
  },
  {
    id: 'MACH-03',
    name: '가공기 3호',
    type: '가공기',
    lineId: 'line-b',
    status: '정상',
    left: '55%',
    top: '24%',
    main: '스핀들 7,100rpm · 공구 64h · CT 35.9s',
  },
  {
    id: 'MACH-11',
    name: '가공기 11호',
    type: '가공기',
    lineId: 'line-b',
    status: '경고',
    left: '80%',
    top: '35%',
    main: '진동 2.4mm/s · 공구 79h · CT 38.2s',
  },
  {
    id: 'MACH-14',
    name: '가공기 14호',
    type: '가공기',
    lineId: 'line-b',
    status: '정상',
    left: '88%',
    top: '35%',
    main: '스핀들 6,880rpm · 공구 42h · CT 36.4s',
  },
  {
    id: 'WASH-03',
    name: '세척기 3호',
    type: '세척기',
    lineId: 'line-c',
    status: '정상',
    left: '25%',
    top: '68%',
    main: '세척수 62℃ · 농도 4.2% · 건조 84℃',
  },
  {
    id: 'ASM-05',
    name: '조립기 5호',
    type: '조립기',
    lineId: 'line-c',
    status: '이상',
    left: '56%',
    top: '72%',
    main: '압입하중 편차 · 체결토크 42Nm · 정지',
  },
  {
    id: 'ASM-09',
    name: '조립기 9호',
    type: '조립기',
    lineId: 'line-c',
    status: '정상',
    left: '41%',
    top: '72%',
    main: '체결토크 39Nm · 체결각도 87° · CT 34.6s',
  },
  {
    id: 'ASM-12',
    name: '조립기 12호',
    type: '조립기',
    lineId: 'line-c',
    status: '경고',
    left: '62%',
    top: '72%',
    main: '압입하중 18.8kN · 체결각도 91° · CT 36.1s',
  },
  {
    id: 'INSP-02',
    name: '검사기 2호',
    type: '검사기',
    lineId: 'line-d',
    status: '경고',
    left: '79%',
    top: '68%',
    main: '목표 24.00mm · 현재 24.18mm · 치수 편차',
  },
  {
    id: 'INSP-05',
    name: '검사기 5호',
    type: '검사기',
    lineId: 'line-d',
    status: '정상',
    left: '88%',
    top: '59%',
    main: '목표 18.50mm · 현재 18.52mm · CT 30.8s',
  },
  {
    id: 'INSP-08',
    name: '검사기 8호',
    type: '검사기',
    lineId: 'line-d',
    status: '정상',
    left: '72%',
    top: '77%',
    main: '목표 32.00mm · 현재 31.98mm · CT 29.9s',
  },
]

const selectedEquipmentId = ref('CAST-02')
const selectedLineId = ref(null)
const isLinePopupOpen = ref(false)

const selectedEquipment = computed(() =>
  equipmentNodes.find((node) => node.id === selectedEquipmentId.value),
)

const selectedLine = computed(() => lines.find((line) => line.id === selectedLineId.value))

const selectedEquipmentLine = computed(() =>
  lines.find((line) => line.id === selectedEquipment.value?.lineId),
)

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
        <p>공장 레이아웃 기반 설비 위치, 상태, 라인 상세 팝업 확인</p>
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
              <button type="button" aria-label="확대">+</button>
              <button type="button" aria-label="축소">-</button>
              <button type="button" aria-label="맞춤">□</button>
            </div>

            <div class="factory-blueprint">
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
                  </div>

                  <div class="factory-conveyor">
                    <span class="conveyor-rail"></span>
                    <button
                      v-for="node in lineEquipments(line.id)"
                      :key="node.id"
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

                <div class="line-popup-mini-conveyor">
                  <span class="conveyor-rail"></span>
                  <button
                    v-for="node in lineEquipments(selectedLine.id)"
                    :key="`popup-${node.id}`"
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
