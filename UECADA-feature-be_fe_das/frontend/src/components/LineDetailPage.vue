<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getEquipments, getEquipmentStatus } from '../api/equipment.js'
import { getLines } from '../api/lines.js'
import { getSensorLatestValues } from '../api/sensor.js'
import {
  Activity,
  BarChart3,
  Bell,
  CalendarDays,
  Factory,
  Gauge,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  TrendingUp,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '라인 상세', icon: BarChart3, href: '#/lines', active: true },
  { label: '설비 제어', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
]

const processOrder = [
  { key: 'casting', label: '주조', icon: Factory },
  { key: 'machining', label: '가공', icon: Wrench },
  { key: 'washing', label: '세척', icon: Activity },
  { key: 'assembly', label: '조립', icon: Gauge },
  { key: 'inspection', label: '검사', icon: BarChart3 },
]

const lines = ref([
  { lineId: 'LINE-01', lineName: 'Line A', latestOee: 91, equipmentTotal: 9, equipmentRunning: 9, equipmentAlarm: 0, equipmentStandby: 0, openAlarmCount: 0 },
  { lineId: 'LINE-02', lineName: 'Line B', latestOee: 84, equipmentTotal: 9, equipmentRunning: 8, equipmentAlarm: 1, equipmentStandby: 0, openAlarmCount: 1 },
  { lineId: 'LINE-03', lineName: 'Line C', latestOee: 88, equipmentTotal: 9, equipmentRunning: 9, equipmentAlarm: 0, equipmentStandby: 0, openAlarmCount: 0 },
])

const selectedLineId = ref('LINE-01')
const latestSensorMap = ref(new Map())
const statusMap = ref({})
const equipmentCatalog = ref(buildDemoEquipment())
let lineRefreshTimer = null
let liveRefreshTimer = null

function buildDemoEquipment() {
  const specs = [
    ['CAST-01', '주조기 1호', '주조'],
    ['CNC-01', '가공기 1호', '가공'],
    ['CNC-02', '가공기 2호', '가공'],
    ['CNC-03', '가공기 3호', '가공'],
    ['WASH-01', '세척기 1호', '세척'],
    ['ASSY-01', '조립기 1호', '조립'],
    ['ASSY-02', '조립기 2호', '조립'],
    ['TEST-01', '검사기 1호', '검사'],
    ['TEST-02', '검사기 2호', '검사'],
  ]

  return [1, 2, 3].flatMap((lineNo) => {
    const lineId = `LINE-${String(lineNo).padStart(2, '0')}`
    return specs.map(([code, name, processType]) => ({
      equipmentCode: `${lineId}_${code}`,
      equipmentName: name,
      processType,
      location: lineId,
      model: '-',
    }))
  })
}

function normalizeLineId(value, fallback = 'LINE-01') {
  const match = String(value ?? '').match(/LINE[-_]?(\d+)/i)
  return match ? `LINE-${match[1].padStart(2, '0')}` : fallback
}

function shortEquipmentCode(equipment) {
  return String(equipment?.equipmentCode ?? '')
    .split('_')
    .pop()
    .replace(/[^A-Za-z0-9-]/g, '')
}

function lineScopedCode(equipment) {
  const lineId = normalizeLineId(equipment?.location ?? equipment?.equipmentCode)
  return `${lineId.replace('-', '')}.${shortEquipmentCode(equipment).replaceAll('-', '')}`
}

function sensorBufferKey(equipment, metric) {
  return `${lineScopedCode(equipment)}:${metric}`
}

function processKey(equipment) {
  const type = equipment?.processType ?? ''
  const code = shortEquipmentCode(equipment)
  if (type.includes('주조') || code.startsWith('CAST')) return 'casting'
  if (type.includes('가공') || code.startsWith('CNC')) return 'machining'
  if (type.includes('세척') || code.startsWith('WASH')) return 'washing'
  if (type.includes('조립') || code.startsWith('ASSY')) return 'assembly'
  if (type.includes('검사') || code.startsWith('TEST')) return 'inspection'
  return 'machining'
}

function equipmentStatusCode(equipment) {
  return statusMap.value[equipment.equipmentCode] ?? 'RUNNING'
}

function equipmentState(equipment) {
  const status = equipmentStatusCode(equipment)
  if (status === 'ALARM') return '이상'
  if (status === 'STANDBY' || status === 'MAINTENANCE') return '대기'
  return '정상'
}

function latestMetric(equipment, metric) {
  const row = latestSensorMap.value.get(sensorBufferKey(equipment, metric))
  const value = row?.latest?.value
  return Number.isFinite(Number(value)) ? Number(value) : null
}

function cycleTime(equipment) {
  return latestMetric(equipment, 'cycle_time')
}

function lineEquipments(lineId) {
  return equipmentCatalog.value.filter(equipment =>
    normalizeLineId(equipment.location ?? equipment.equipmentCode) === lineId,
  )
}

function processStages(lineId) {
  const equipments = lineEquipments(lineId)
  return processOrder.map(process => {
    const nodes = equipments
      .filter(equipment => processKey(equipment) === process.key)
      .sort((a, b) => shortEquipmentCode(a).localeCompare(shortEquipmentCode(b)))
      .map(equipment => {
        const cycle = cycleTime(equipment)
        const temperature = latestMetric(equipment, 'sensor_temperature')
        return {
          id: equipment.equipmentCode,
          code: shortEquipmentCode(equipment),
          name: equipment.equipmentName,
          state: equipmentState(equipment),
          cycle,
          cycleLabel: cycle == null ? 'CT 대기' : `CT ${cycle.toFixed(1)}s`,
          tempLabel: temperature == null ? '온도 대기' : `${temperature.toFixed(1)}℃`,
        }
      })
    return { ...process, nodes }
  })
}

function clampPercent(value) {
  return Math.max(0, Math.min(100, Math.round(value)))
}

function stationScore(cycle, target) {
  if (!cycle || !target) return 72
  return clampPercent(100 - Math.abs(cycle - target) / target * 100)
}

const lineSummaries = computed(() => lines.value.map((line, index) => {
  const lineId = normalizeLineId(line.lineId, `LINE-${String(index + 1).padStart(2, '0')}`)
  const equipments = lineEquipments(lineId)
  const total = equipments.length || line.equipmentTotal || 1
  const running = equipments.length
    ? equipments.filter(equipment => equipmentStatusCode(equipment) === 'RUNNING').length
    : line.equipmentRunning ?? 0
  const stopped = equipments.length
    ? equipments.filter(equipment => equipmentStatusCode(equipment) === 'ALARM').length
    : line.equipmentAlarm ?? 0
  const waiting = Math.max(0, total - running - stopped)
  const runPct = clampPercent((running / total) * 100)
  const stopPct = clampPercent((stopped / total) * 100)
  const waitPct = Math.max(0, 100 - runPct - stopPct)
  const stages = processStages(lineId)
  const processCycles = stages.map(stage => {
    const values = stage.nodes.map(node => node.cycle).filter(value => value != null && value > 0)
    const avg = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
    return { label: stage.label, value: avg }
  })
  const validCycles = processCycles.map(item => item.value).filter(value => value != null && value > 0)
  const targetCycle = validCycles.length
    ? validCycles.reduce((sum, value) => sum + value, 0) / validCycles.length
    : 60
  const bottleneckCycle = validCycles.length ? Math.max(...validCycles) : 60
  const stations = processCycles.map(item => ({
    label: item.label,
    value: stationScore(item.value, targetCycle),
    cycle: item.value,
  }))
  const balance = stations.length
    ? clampPercent(stations.reduce((sum, item) => sum + item.value, 0) / stations.length)
    : 0
  const uph = bottleneckCycle ? Math.round(3600 / bottleneckCycle) : 0
  const upmh = uph * Math.max(1, running || total)
  const productivity = clampPercent((line.latestOee ?? 88) * (balance / 100))

  return {
    id: lineId,
    name: line.lineName ?? lineId,
    oee: line.latestOee != null ? Math.round(Number(line.latestOee)) : 0,
    equipment: total,
    active: running,
    alarm: stopped,
    status: { run: runPct, stop: stopPct, wait: waitPct, stopEnd: runPct + stopPct },
    stages,
    balance,
    stations,
    uph,
    upmh,
    productivity,
    upmhPercent: clampPercent((upmh / 540) * 100),
    uphPercent: clampPercent((uph / 80) * 100),
  }
}))

const selectedLine = computed(() =>
  lineSummaries.value.find(line => line.id === selectedLineId.value) ?? lineSummaries.value[0],
)

async function refreshLines() {
  try {
    const [lineData, equipData] = await Promise.all([
      getLines('FACTORY-01'),
      getEquipments('FACTORY-01'),
    ])

    if (lineData?.length) {
      lines.value = lineData.map((line, index) => ({
        ...line,
        lineId: normalizeLineId(line.lineId, `LINE-${String(index + 1).padStart(2, '0')}`),
      }))
      if (!lines.value.some(line => normalizeLineId(line.lineId) === selectedLineId.value)) {
        selectedLineId.value = normalizeLineId(lines.value[0].lineId)
      }
    }

    if (equipData?.length) {
      equipmentCatalog.value = equipData
      const ids = equipData.map(equipment => equipment.equipmentCode)
      try {
        const statuses = await getEquipmentStatus(ids)
        statusMap.value = Object.fromEntries(statuses.map(status => [status.equipId, status.statusCode]))
      } catch (_) {
        statusMap.value = {}
      }
    }

    await refreshLatestValues()
  } catch (e) {
    console.warn('[LineDetail] API 연결 실패, 데모 데이터 표시:', e.message)
  }
}

async function refreshLatestValues() {
  const keys = [...new Set(
    equipmentCatalog.value.flatMap(equipment => [
      sensorBufferKey(equipment, 'cycle_time'),
      sensorBufferKey(equipment, 'sensor_temperature'),
      sensorBufferKey(equipment, 'sensor_current'),
      sensorBufferKey(equipment, 'sensor_voltage'),
      sensorBufferKey(equipment, 'sensor_vibration'),
    ]),
  )]
  if (!keys.length) return
  try {
    const rows = await getSensorLatestValues(keys)
    latestSensorMap.value = new Map(rows.map(row => [row.bufferKey, row]))
  } catch (_) {
    latestSensorMap.value = new Map()
  }
}

onMounted(() => {
  refreshLines()
  liveRefreshTimer = window.setInterval(refreshLatestValues, 1000)
  lineRefreshTimer = window.setInterval(refreshLines, 5000)
})

onUnmounted(() => {
  if (lineRefreshTimer) window.clearInterval(lineRefreshTimer)
  if (liveRefreshTimer) window.clearInterval(liveRefreshTimer)
})
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
        <p>라인별 OEE, 설비 상태 분포, 밸런싱/생산성 분석 전용 화면</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Line Analytics</p>
          <h1>라인별 상세보기</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-18
          </span>
          <a class="ghost-button" href="#/layout">
            <MapPinned :size="16" />
            <span>레이아웃</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="dashboard-panel line-selector-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line Flow</p>
            <h2>라인밸런싱 대상 라인 선택</h2>
          </div>
          <Factory :size="22" />
        </div>

        <div class="line-selector-tabs" role="tablist" aria-label="라인 선택">
          <button
            v-for="line in lineSummaries"
            :key="line.id"
            :class="{ active: selectedLine?.id === line.id }"
            type="button"
            @click="selectedLineId = line.id"
          >
            <strong>{{ line.name }}</strong>
            <span>OEE {{ line.oee }}% · {{ line.equipment }}대</span>
          </button>
        </div>
      </section>

      <section v-if="selectedLine" class="dashboard-panel line-flow-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line Layout</p>
            <h2>{{ selectedLine.name }} 라인별 현황</h2>
          </div>
          <Activity :size="22" />
        </div>

        <div class="line-flow-stage-row">
          <template v-for="(stage, index) in selectedLine.stages" :key="stage.key">
            <div v-if="index > 0" class="line-flow-arrow" aria-hidden="true">→</div>
            <section class="line-flow-stage">
              <h3>
                <component :is="stage.icon" :size="18" />
                <span>{{ stage.label }}</span>
              </h3>
              <div class="line-flow-equipment-list">
                <article
                  v-for="node in stage.nodes"
                  :key="node.id"
                  :class="['line-flow-equipment', node.state]"
                >
                  <component :is="stage.icon" :size="24" />
                  <strong>{{ node.name }}</strong>
                  <small>{{ node.code }}</small>
                  <span>{{ node.cycleLabel }}</span>
                </article>
              </div>
            </section>
          </template>
        </div>
      </section>

      <section class="dashboard-panel line-oee-analysis-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line OEE</p>
            <h2>라인별 종합 설비 효율</h2>
          </div>
          <Gauge :size="22" />
        </div>

        <div class="line-oee-donut-grid">
          <article v-for="line in lineSummaries" :key="line.id" class="line-oee-donut-card">
            <div class="line-analysis-donut" :style="{ '--value': `${line.oee}%` }">
              <strong>{{ line.oee }}%</strong>
            </div>
            <div>
              <h3>{{ line.name }}</h3>
              <p>{{ line.equipment }}대 설비 기준 종합 설비 효율</p>
            </div>
          </article>
        </div>
      </section>

      <section class="dashboard-panel line-status-analysis-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line Equipment Status</p>
            <h2>라인에 해당하는 설비 상태 분포도</h2>
          </div>
          <Activity :size="22" />
        </div>

        <div class="line-status-donut-grid">
          <article v-for="line in lineSummaries" :key="line.id" class="line-status-card">
            <div
              class="line-status-donut"
              :style="{
                '--run-end': `${line.status.run}%`,
                '--stop-end': `${line.status.stopEnd}%`,
              }"
            >
              <strong>{{ line.equipment }}대</strong>
            </div>
            <div class="line-status-info">
              <h3>{{ line.name }}</h3>
              <div class="line-status-legend">
                <span class="run">가동 {{ line.status.run }}%</span>
                <span class="stop">정지 {{ line.status.stop }}%</span>
                <span class="wait">대기 {{ line.status.wait }}%</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="line-analysis-grid">
        <article class="dashboard-panel line-balance-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Line Balancing</p>
              <h2>주조, 가공, 세척, 조립, 검사 밸런싱</h2>
            </div>
            <TrendingUp :size="22" />
          </div>

          <div class="line-balance-chart-grid">
            <article v-for="line in lineSummaries" :key="line.id" class="line-balance-chart">
              <div class="line-chart-head">
                <strong>{{ line.name }}</strong>
                <span>{{ line.balance }}%</span>
              </div>
              <div class="line-station-bars">
                <i
                  v-for="station in line.stations"
                  :key="`${line.id}-${station.label}`"
                  :title="station.cycle ? `${station.label} ${station.cycle.toFixed(1)}s` : station.label"
                  :style="{ height: `${station.value}%` }"
                >
                  <b>{{ station.label }}</b>
                </i>
              </div>
            </article>
          </div>
        </article>

        <article class="dashboard-panel productivity-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">UPMH / UPH</p>
              <h2>라인 생산성 분석</h2>
            </div>
            <Factory :size="22" />
          </div>

          <div class="productivity-chart">
            <article v-for="line in lineSummaries" :key="line.id">
              <div class="productivity-label">
                <strong>{{ line.name }}</strong>
                <span>{{ line.productivity }}%</span>
              </div>
              <div class="productivity-bars">
                <div>
                  <i :style="{ width: `${line.upmhPercent}%` }"></i>
                  <span>UPMH {{ line.upmh.toLocaleString() }}</span>
                </div>
                <div>
                  <i :style="{ width: `${line.uphPercent}%` }"></i>
                  <span>UPH {{ line.uph.toLocaleString() }}</span>
                </div>
              </div>
            </article>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
