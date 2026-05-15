<script setup>
/**
 * SCADA 메인 대시보드 — 디자인 시스템
 * 배경 #F4F7F9, 5:5 그리드, 카드 12px·부드러운 그림자, gap 24px
 */
import { computed, onMounted, ref, watch } from 'vue'
import { getDashboard } from '../api/dashboard.js'
import {
  AlertTriangle,
  Bell,
  CalendarDays,
  CheckCircle2,
  Cog,
  Factory,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Package,
  Search,
  Siren,
  UserCircle,
  Users,
  Wrench,
} from 'lucide-vue-next'

/** 라인 카드 좌측 마크 (라인별 구분) */
const lineMarkIcons = [Factory, Cog, Package]

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard', active: true },
  { label: '레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 제어', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

/** 전일 대비: 증가=빨강, 감소=파랑 */
function yoyClass(delta) {
  if (delta > 0) return 'dash-yoy--inc'
  if (delta < 0) return 'dash-yoy--dec'
  return 'dash-yoy--flat'
}

function yoyArrow(delta) {
  if (delta > 0) return '▲'
  if (delta < 0) return '▼'
  return '—'
}

function yoyAbs(delta) {
  if (delta === 0) return '0'
  return `${Math.abs(delta)}`
}

const totalOeeDisplay = ref('91.3')

/** 종합 OEE 증감 (%p) — 비교 기준별 샘플 */
const totalOeeCompare = {
  day: 0.8,
  month: 1.1,
  year: -0.4,
}

/** 전역 비교 기준 — 패널 전체 동일 적용 */
const dashboardPeriod = ref('day')

const totalOeeDelta = computed(() => totalOeeCompare[dashboardPeriod.value])

function compareLabel(period) {
  if (period === 'month') return '전월 대비'
  if (period === 'year') return '전년 대비'
  return '전일 대비'
}

const comparePeriodButtons = [
  { key: 'day', label: '전일' },
  { key: 'month', label: '전월' },
  { key: 'year', label: '전년' },
]

const oeeHourLabels = [
  '00:00',
  '02:00',
  '04:00',
  '06:00',
  '08:00',
  '10:00',
  '12:00',
  '14:00',
  '16:00',
  '18:00',
  '20:00',
  '22:00',
  '24:00',
]

const oeeHourlySeries = ref([
  { name: '라인 A', data: [86.2, 87.1, 87.8, 88.4, 89.0, 89.6, 90.2, 90.8, 91.1, 91.4, 91.6, 91.9, 92.1] },
  { name: '라인 B', data: [83.5, 84.2, 84.8, 85.3, 85.9, 86.4, 86.9, 87.3, 87.8, 88.1, 88.4, 88.6, 88.9] },
  { name: '라인 C', data: [84.8, 85.2, 85.9, 86.3, 86.8, 87.2, 87.9, 88.4, 88.9, 89.2, 89.6, 90.0, 90.4] },
])

const oeeHourlyOptions = ref({
  chart: { type: 'bar', toolbar: { show: false }, fontFamily: 'inherit' },
  plotOptions: {
    bar: {
      horizontal: false,
      columnWidth: '52%',
      borderRadius: 4,
      dataLabels: { position: 'top' },
    },
  },
  colors: ['#002c5f', '#62b3ff', '#0a9f68'],
  dataLabels: {
    enabled: false,
  },
  xaxis: {
    categories: oeeHourLabels,
    labels: { style: { fontSize: '12px', fontWeight: 700, colors: '#475569' } },
    axisBorder: { show: true, color: '#cbd5e1' },
    axisTicks: { show: true, color: '#cbd5e1' },
  },
  yaxis: {
    min: 75,
    max: 100,
    tickAmount: 5,
    labels: {
      formatter: (v) => `${v}%`,
      style: { fontSize: '12px', fontWeight: 700, colors: '#475569' },
    },
  },
  legend: {
    position: 'top',
    horizontalAlign: 'center',
    fontSize: '11px',
    fontWeight: 800,
    markers: { width: 8, height: 8, radius: 2 },
    labels: { colors: '#475569' },
    itemMargin: { horizontal: 8, vertical: 2 },
  },
  grid: {
    borderColor: '#e2e8f0',
    strokeDashArray: 0,
    padding: { top: 4, right: 6, bottom: 2, left: 6 },
  },
  tooltip: {
    theme: 'light',
    style: {
      fontSize: '14px',
      fontFamily: 'inherit',
    },
    y: { formatter: (v) => `${Number(v).toFixed(1)}%` },
  },
})

const lineStats = ref([
  { name: '라인 A', pct: '88.5%', deltas: { day: 1.2, month: 0.9, year: 2.1 } },
  { name: '라인 B', pct: '82.1%', deltas: { day: -0.9, month: 0.3, year: -1.4 } },
  { name: '라인 C', pct: '85.4%', deltas: { day: 0.4, month: -0.2, year: 1.0 } },
])

function lineDelta(ln) {
  return ln.deltas[dashboardPeriod.value]
}

const equipmentTotalCount = ref(120)

/** 설비 상태 분포 — 비교 기준별 샘플 분포 */
const statusDonutByPeriod = ref({
  day: [70, 15, 15],
  month: [68, 17, 15],
  year: [71, 14, 15],
})

const statusDonutSeries = ref([...statusDonutByPeriod.value.day])

const statusDonutSliceLabels = ['가동', '정지', '대기']

const statusDonutColors = ['#0a9f68', '#d92d20', '#f0b429']

/** 알람 현황 요약 — 비교 기준별 데모 수치 */
const alarmSummaryByPeriod = ref({
  day: { total: 48, urgent: 7, resolved: 31, pending: 10 },
  month: { total: 312, urgent: 42, resolved: 241, pending: 29 },
  year: { total: 3826, urgent: 518, resolved: 2965, pending: 343 },
})

const alarmSummary = computed(() => alarmSummaryByPeriod.value[dashboardPeriod.value])

/** 도넛 중앙 오버레이 (기본값 / 호버 시 슬라이스 반영) */
const donutOverlayPrimary = ref('총 설비 대수')
const donutOverlayValue = ref(`${equipmentTotalCount}대`)
const donutOverlayIsHover = ref(false)

function resetDonutOverlay() {
  donutOverlayPrimary.value = '총 설비 대수'
  donutOverlayValue.value = `${equipmentTotalCount}대`
  donutOverlayIsHover.value = false
}

watch(dashboardPeriod, (p) => {
  statusDonutSeries.value = [...(statusDonutByPeriod.value[p] ?? statusDonutByPeriod.value.day)]
  resetDonutOverlay()
})

onMounted(async () => {
  try {
    const data = await getDashboard()

    // 공장 OEE
    if (data.factoryOee != null) totalOeeDisplay.value = Number(data.factoryOee).toFixed(1)

    // 라인별 OEE
    if (data.lineStats?.length) {
      lineStats.value = data.lineStats.map(l => ({
        name: l.lineName,
        pct: l.oee != null ? `${Number(l.oee).toFixed(1)}%` : '--',
        deltas: { day: 0, month: 0, year: 0 },
      }))
      // 시간별 OEE 시리즈
      if (data.oeeHourlySeries?.length) {
        oeeHourlySeries.value = data.oeeHourlySeries.map(s => ({
          name: s.lineName,
          data: s.data.map(p => Number(p.oee) || 0),
        }))
      }
    }

    // 설비 상태 도넛
    if (data.statusDonut) {
      const t = data.statusDonut.total || 1
      const run = Math.round(data.statusDonut.running / t * 100)
      const stop = Math.round((data.statusDonut.alarm + data.statusDonut.maintenance) / t * 100)
      const wait = Math.max(0, 100 - run - stop)
      const donut = [run, stop, wait]
      statusDonutByPeriod.value.day = donut
      statusDonutSeries.value = [...donut]
      equipmentTotalCount.value = data.statusDonut.total
      donutOverlayValue.value = `${data.statusDonut.total}대`
    }

    // 알람 요약
    if (data.alarmSummary) {
      const s = data.alarmSummary
      alarmSummaryByPeriod.value.day = {
        total: s.total,
        urgent: s.critical,
        resolved: s.resolved,
        pending: s.open,
      }
    }
  } catch (e) {
    console.warn('[Dashboard] API 연결 실패, 데모 데이터 표시:', e.message)
  }
})

function setDonutOverlayFromSlice(dataPointIndex) {
  const i = dataPointIndex
  const series = statusDonutSeries.value
  const labels = statusDonutSliceLabels
  if (i == null || i < 0 || i >= labels.length) return
  donutOverlayPrimary.value = labels[i]
  donutOverlayValue.value = `${series[i]}%`
  donutOverlayIsHover.value = true
}

/** 라인 카드 스파크라인 — 시간별 시리즈 마지막 구간 */
function lineSparkPolyline(lineIdx) {
  const raw = oeeHourlySeries.value[lineIdx]?.data ?? []
  const w = 120
  const h = 36
  const pad = 2
  const last = raw.slice(-10)
  if (!last.length) return ''
  const min = Math.min(...last)
  const max = Math.max(...last)
  const range = max - min || 1
  return last
    .map((v, i) => {
      const x = pad + (i / Math.max(1, last.length - 1)) * (w - pad * 2)
      const y = pad + (1 - (v - min) / range) * (h - pad * 2)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

const lineSparkStroke = ['#002c5f', '#0ea5e9', '#0a9f68']

const statusDonutOptions = ref({
  chart: {
    type: 'donut',
    toolbar: { show: false },
    fontFamily: 'inherit',
    offsetY: -8,
    events: {
      dataPointMouseEnter(_event, _chartContext, config) {
        setDonutOverlayFromSlice(config.dataPointIndex)
      },
      dataPointMouseLeave() {
        resetDonutOverlay()
      },
    },
  },
  labels: statusDonutSliceLabels,
  colors: statusDonutColors,
  plotOptions: {
    pie: {
      donut: {
        size: '72%',
        labels: {
          show: false,
        },
      },
    },
  },
  dataLabels: { enabled: false },
  legend: {
    show: false,
  },
  stroke: { width: 3, colors: ['#ffffff'] },
  tooltip: {
    theme: 'light',
    style: {
      fontSize: '20px',
      fontFamily: 'inherit',
    },
    fillSeriesColor: false,
    y: { formatter: (v) => `${v}%` },
  },
})

/** 시간별 차트 — 패널 가변 높이에 맞춤 (데스크톱 정렬용) */
const hourlyBarWrapRef = ref(null)
const hourlyChartHeight = ref(240)

function syncHourlyChartHeight() {
  const el = hourlyBarWrapRef.value
  if (!el || typeof ResizeObserver === 'undefined') return
  const h = Math.round(el.getBoundingClientRect().height - 8)
  const next = Number.isFinite(h) ? Math.max(160, h) : hourlyChartHeight.value
  if (Math.abs(next - hourlyChartHeight.value) < 2) return
  hourlyChartHeight.value = next
}

watch(
  hourlyBarWrapRef,
  (el, _prev, onCleanup) => {
    if (!el || typeof ResizeObserver === 'undefined') return
    let raf = 0
    const run = () => {
      if (raf) cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => {
        raf = 0
        syncHourlyChartHeight()
      })
    }
    const ro = new ResizeObserver(run)
    ro.observe(el)
    run()
    onCleanup(() => {
      ro.disconnect()
      if (raf) cancelAnimationFrame(raf)
    })
  },
  { flush: 'post' },
)

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
        <span>Admin</span>
        <strong>김관리</strong>
        <p>OEE·설비 상태·최근 알람을 한 화면에서 확인합니다.</p>
      </div>
    </aside>

    <section class="dashboard-main dash-dashboard-fill">
      <header class="dashboard-header dash-dashboard-page-header">
        <div class="dash-dashboard-page-header-primary">
          <div class="dash-dashboard-page-header-titles">
            <p class="dashboard-kicker">통합 설비 제어</p>
            <h1>대시보드</h1>
          </div>
          <div class="dash-period-seg dash-period-seg--global" role="group" aria-label="대시보드 비교 기준">
            <button
              v-for="b in comparePeriodButtons"
              :key="`global-${b.key}`"
              type="button"
              :class="{ on: dashboardPeriod === b.key }"
              @click="dashboardPeriod = b.key"
            >
              {{ b.label }}
            </button>
          </div>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="ghost-button" href="#/alarms">
            <Bell :size="16" />
            <span>최근 알람</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <div class="dash-scada-surface">
        <div class="dash-ref-page">
          <div class="dash-ref-grid dash-ref-grid--dashboard">
            <!-- 좌측 ~2/3: 전체 OEE → 라인별 카드 → 시간별 막대 -->
            <div class="dash-ref-col dash-ref-col--stack">
              <article class="dashboard-panel dash-ref-hero-oee-panel">
                <div class="dash-ref-panel-head dash-ref-hero-oee-head">
                  <div>
                    <p class="panel-kicker">Overall Equipment Effectiveness</p>
                    <h2>전체 설비 효율 (OEE)</h2>
                  </div>
                </div>
                <div class="dash-oee-hero-band">
                  <div class="dash-oee-hero-band-left">
                    <strong class="dash-oee-hero-pct" aria-label="전체 OEE">{{ totalOeeDisplay }}%</strong>
                  </div>
                  <span class="dash-oee-kpi-pill dash-oee-kpi-pill--trail" :class="yoyClass(totalOeeDelta)">
                    전체
                    <span class="dash-compare-slot">{{ compareLabel(dashboardPeriod) }}</span>
                    <span class="dash-yoy-num-slot">{{ yoyAbs(totalOeeDelta) }}</span>%p
                    <span class="dash-oee-kpi-pill-ar">{{ yoyArrow(totalOeeDelta) }}</span>
                  </span>
                </div>
              </article>

              <article class="dashboard-panel dash-ref-line-section-panel">
                <div class="dash-ref-panel-head dash-ref-line-section-head">
                  <h2 class="dash-ref-line-section-title">라인별 OEE 현황 및 추세</h2>
                </div>
                <div class="dash-ref-line-stack dash-ref-line-stack--detail" aria-label="라인별 OEE">
                  <article
                    v-for="(ln, lineIdx) in lineStats"
                    :key="ln.name"
                    class="dashboard-panel dash-ref-line-detail-card"
                    :aria-label="`${ln.name} OEE`"
                  >
                    <div class="dash-line-detail-top">
                      <div class="dash-line-mark" :class="[`dash-line-mark--${lineIdx}`]">
                        <component
                          :is="lineMarkIcons[lineIdx]"
                          :size="20"
                          :stroke-width="2"
                          aria-hidden="true"
                        />
                      </div>
                      <span class="dash-line-detail-name">{{ ln.name }}</span>
                    </div>
                    <strong class="dash-line-detail-pct">{{ ln.pct }}</strong>
                    <div class="dash-line-yoy-row dash-line-yoy-row--detail" :class="yoyClass(lineDelta(ln))">
                      <span class="dash-yoy-label dash-compare-slot">{{ compareLabel(dashboardPeriod) }}</span>
                      <span class="dash-yoy-value">
                        <span class="dash-yoy-ar">{{ yoyArrow(lineDelta(ln)) }}</span>
                        <span class="dash-yoy-num-slot">{{ yoyAbs(lineDelta(ln)) }}</span>%p
                      </span>
                    </div>
                    <svg
                      class="dash-line-spark"
                      viewBox="0 0 120 36"
                      preserveAspectRatio="none"
                      aria-hidden="true"
                    >
                      <polyline
                        fill="none"
                        :stroke="lineSparkStroke[lineIdx]"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        :points="lineSparkPolyline(lineIdx)"
                      />
                    </svg>
                  </article>
                </div>
              </article>

              <article class="dashboard-panel dash-ref-hourly-panel">
                <div class="dash-ref-panel-head">
                  <div>
                    <p class="panel-kicker">Hourly trend</p>
                    <h2>시간별 OEE 추세 상세</h2>
                  </div>
                </div>
                <div class="dash-oee-summary-chart">
                  <div ref="hourlyBarWrapRef" class="dash-oee-bar-wrap">
                    <apexchart type="bar" :height="hourlyChartHeight" :options="oeeHourlyOptions" :series="oeeHourlySeries" />
                  </div>
                </div>
              </article>
            </div>

            <!-- 우측 ~1/3: 설비 상태 분포 → 알람 요약 -->
            <div class="dash-ref-col dash-ref-col--stack dash-ref-col--side">
              <article class="dashboard-panel dash-ref-status-panel">
                <div class="dash-ref-panel-head dash-ref-status-panel-head dash-status-panel-toolbar">
                  <div>
                    <p class="panel-kicker">Equipment status</p>
                    <h2>설비 상태 분포</h2>
                  </div>
                  <div class="dash-status-toolbar-actions">
                    <label class="dash-status-search">
                      <Search :size="16" class="dash-status-search-ico" aria-hidden="true" />
                      <input type="search" placeholder="검색" autocomplete="off" />
                    </label>
                    <button type="button" class="dash-status-profile-btn" aria-label="사용자 프로필">
                      <UserCircle :size="22" :stroke-width="2" />
                    </button>
                  </div>
                </div>
                <div class="dash-status-body">
                  <div class="dash-status-donut-wrap">
                    <div
                      class="dash-donut-center-overlay"
                      :class="{ 'dash-donut-center-overlay--slice': donutOverlayIsHover }"
                    >
                      <template v-if="!donutOverlayIsHover">
                        <span class="dash-donut-center-line">총 설비 대수:</span>
                        <span class="dash-donut-center-value">{{ equipmentTotalCount }}대</span><!-- ref auto-unwrap -->
                      </template>
                      <template v-else>
                        <span class="dash-donut-center-primary">{{ donutOverlayPrimary }}</span>
                        <span class="dash-donut-center-value">{{ donutOverlayValue }}</span>
                      </template>
                    </div>
                    <apexchart type="donut" height="360" :options="statusDonutOptions" :series="statusDonutSeries" />
                  </div>
                  <ul class="dash-status-custom-legend" aria-label="설비 상태 범례">
                    <li
                      v-for="(label, i) in statusDonutSliceLabels"
                      :key="label"
                      class="dash-status-legend-item"
                    >
                      <span
                        class="dash-status-legend-dot"
                        :style="{ backgroundColor: statusDonutColors[i] }"
                        aria-hidden="true"
                      />
                      <span class="dash-status-legend-label">{{ label }}</span>
                      <span class="dash-status-legend-num">{{ statusDonutSeries[i] }}%</span>
                    </li>
                  </ul>
                </div>
              </article>

              <article class="dashboard-panel dash-ref-alarm-hub" aria-label="알람 현황 상세 요약">
                <div class="dash-ref-panel-head dash-ref-alarm-hub-head">
                  <div>
                    <p class="panel-kicker">Alarm detail</p>
                    <h2 class="dash-ref-alarm-hub-title">
                      알람 현황 상세 요약
                      <span class="dash-alarm-title-suffix">
                        (<span class="dash-compare-slot">{{ compareLabel(dashboardPeriod) }}</span>)
                      </span>
                    </h2>
                  </div>
                </div>

                <div class="dash-alarm-summary-grid" role="region">
                  <div class="dash-alarm-summary-tile dash-alarm-summary-tile--info">
                    <Bell class="dash-alarm-tile-ico" :size="22" :stroke-width="2" aria-hidden="true" />
                    <span class="dash-alarm-summary-label">전체</span>
                    <strong class="dash-alarm-summary-value">{{ alarmSummary.total }}</strong>
                    <span class="dash-alarm-summary-unit">건</span>
                  </div>
                  <div class="dash-alarm-summary-tile dash-alarm-summary-tile--urgent">
                    <Siren class="dash-alarm-tile-ico" :size="22" :stroke-width="2" aria-hidden="true" />
                    <span class="dash-alarm-summary-label">긴급</span>
                    <strong class="dash-alarm-summary-value">{{ alarmSummary.urgent }}</strong>
                    <span class="dash-alarm-summary-unit">건</span>
                  </div>
                  <div class="dash-alarm-summary-tile dash-alarm-summary-tile--pending">
                    <AlertTriangle class="dash-alarm-tile-ico dash-alarm-tile-ico--muted" :size="22" :stroke-width="2" aria-hidden="true" />
                    <span class="dash-alarm-summary-label">미처리</span>
                    <strong class="dash-alarm-summary-value">{{ alarmSummary.pending }}</strong>
                    <span class="dash-alarm-summary-unit">건</span>
                  </div>
                  <div class="dash-alarm-summary-tile dash-alarm-summary-tile--done">
                    <CheckCircle2 class="dash-alarm-tile-ico" :size="22" :stroke-width="2" aria-hidden="true" />
                    <span class="dash-alarm-summary-label">처리 완료</span>
                    <strong class="dash-alarm-summary-value">{{ alarmSummary.resolved }}</strong>
                    <span class="dash-alarm-summary-unit">건</span>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* —— SCADA 대시보드 디자인 토큰 —— */
.dash-scada-surface {
  --dash-bg: #f4f7f9;
  --dash-card-radius: 12px;
  --dash-card-shadow: 0 4px 24px rgba(15, 23, 42, 0.06), 0 12px 40px rgba(15, 23, 42, 0.05);
  --dash-gap: 24px;
  /* 라인 카드 · 알람 KPI 타일 공통 */
  --dash-kpi-gap: 10px;
  --dash-kpi-cell-min-h: 152px;
  --dash-kpi-cell-pad-x: 14px;
  --dash-kpi-cell-pad-y: 14px;
  --dash-kpi-cell-radius: 10px;
  background: var(--dash-bg);
  margin-left: -24px;
  margin-right: -24px;
  margin-bottom: -24px;
  padding: 24px;
  min-height: calc(100vh - 120px);
}

.dash-scada-surface :deep(.dashboard-panel) {
  border: none;
  border-radius: var(--dash-card-radius);
  box-shadow: var(--dash-card-shadow);
  background: #ffffff;
}

section.dashboard-main.dash-dashboard-fill {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

section.dashboard-main.dash-dashboard-fill .dash-scada-surface {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

section.dashboard-main.dash-dashboard-fill .dash-ref-page {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

section.dashboard-main.dash-dashboard-fill .dash-ref-grid--dashboard {
  flex: 1 1 auto;
  min-height: 0;
}

.dash-ref-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

.dash-ref-col--stack {
  display: flex;
  flex-direction: column;
  gap: var(--dash-gap);
  min-width: 0;
  min-height: 0;
  align-self: start;
}

.dash-ref-grid--dashboard {
  display: grid;
  grid-template-columns: minmax(0, 1.72fr) minmax(280px, 1fr);
  grid-template-rows: auto;
  gap: var(--dash-gap);
  align-items: start;
}

@media (min-width: 1181px) {
  .dash-ref-grid--dashboard {
    align-items: stretch;
    grid-template-rows: minmax(0, 1fr);
    min-height: calc(100vh - 210px);
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack {
    align-self: stretch;
    height: 100%;
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-hero-oee-panel {
    flex-shrink: 0;
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-line-section-panel {
    flex: 1 1 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-hourly-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  .dash-ref-col--side .dash-ref-alarm-hub {
    margin-top: auto;
    flex-shrink: 0;
  }

  .dash-ref-line-section-panel .dash-ref-line-section-head {
    flex-shrink: 0;
  }

  .dash-ref-line-section-panel .dash-ref-line-stack--detail {
    flex: 1 1 auto;
    min-height: 0;
    align-self: stretch;
    height: 100%;
    grid-template-rows: minmax(0, 1fr);
  }

  .dash-ref-line-detail-card {
    height: 100%;
    min-height: var(--dash-kpi-cell-min-h);
  }

  .dash-ref-line-detail-card .dash-line-spark {
    margin-top: auto;
  }

  .dash-ref-hourly-panel .dash-oee-summary-chart {
    flex: 1 1 auto;
    min-height: 0;
  }

  .dash-ref-hourly-panel .dash-oee-bar-wrap {
    flex: 1 1 auto;
    min-height: 140px;
  }

  .dash-ref-hourly-panel .dash-oee-bar-wrap :deep(> div) {
    flex: 1 1 auto;
    min-height: 0;
  }
}

/* 비교 기준 라벨 길이·숫자 자릿수 바뀔 때 레이아웃 점프 완화 */
.dash-compare-slot {
  display: inline-block;
  min-width: 5.75rem;
  text-align: center;
}

.dash-line-yoy-row--detail .dash-compare-slot {
  text-align: left;
}

.dash-yoy-num-slot {
  display: inline-block;
  min-width: 2ch;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.dash-ref-line-stack {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--dash-kpi-gap);
  width: 100%;
  min-width: 0;
  min-height: var(--dash-kpi-cell-min-h);
  height: 100%;
  align-self: stretch;
}

.dash-ref-line-stack--detail {
  min-height: 0;
  height: auto;
}

@media (min-width: 1100px) {
  .dash-ref-line-stack {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1099px) {
  .dash-ref-line-stack--detail {
    grid-template-columns: 1fr;
  }
}

.dash-ref-panel-head {
  margin-bottom: 20px;
}

.dash-panel-subhint {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.02em;
}

.dash-period-seg {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.dash-period-seg button {
  appearance: none;
  margin: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #475569;
  padding: 7px 12px;
  border-radius: 8px;
  line-height: 1.2;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.dash-period-seg button:hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.65);
}

.dash-period-seg button.on {
  color: #0f172a;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.dash-period-seg--global {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  min-width: 13.25rem;
}

.dash-period-seg--global button {
  flex: 1 1 0;
  min-width: 4.25rem;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 850;
  text-align: center;
}

.dash-dashboard-page-header {
  align-items: center;
  flex-wrap: wrap;
  row-gap: 14px;
}

.dash-dashboard-page-header-primary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 28px;
  flex: 1 1 auto;
  min-width: 0;
}

.dash-dashboard-page-header-titles {
  flex: 0 1 auto;
  min-width: 0;
}

.dash-ref-panel-head h2 {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 950;
  color: #0f1f38;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.dash-ref-oee-panel-head {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.dash-ref-oee-titles {
  flex: 1 1 auto;
  min-width: 160px;
  text-align: left;
}

.dash-ref-oee-titles .panel-kicker {
  margin-bottom: 0;
}

.dash-ref-oee-titles h2 {
  margin-top: 6px;
}

.dash-ref-oee-kpi-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  min-width: 0;
  margin-left: auto;
  text-align: right;
}

.dash-oee-hero-pct {
  margin: 0;
  font-size: 40px;
  font-weight: 950;
  letter-spacing: -0.04em;
  color: #0f172a;
  line-height: 1;
}

.dash-ref-oee-kpi-col .dash-oee-yoy-row {
  justify-content: flex-end;
}

.dash-ref-hero-oee-panel {
  padding: 28px 28px 32px !important;
}

.dash-ref-hero-oee-head {
  margin-bottom: 16px;
}

.dash-oee-hero-band {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px 18px;
  width: 100%;
}

.dash-oee-hero-band-left {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 12px 16px;
  flex-shrink: 0;
  min-width: 0;
}

.dash-oee-kpi-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 850;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
  min-width: 17rem;
}

.dash-oee-kpi-pill.dash-yoy--inc {
  background: rgba(16, 185, 129, 0.14);
  color: #047857;
  border-color: rgba(16, 185, 129, 0.32);
}

.dash-oee-kpi-pill.dash-yoy--dec {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.28);
}

.dash-oee-kpi-pill.dash-yoy--flat {
  background: #f1f5f9;
  color: #64748b;
  border-color: #e2e8f0;
}

.dash-oee-kpi-pill-ar {
  font-weight: 950;
  font-size: 12px;
}

.dash-oee-kpi-pill--trail {
  flex-shrink: 0;
}

@media (max-width: 560px) {
  .dash-oee-hero-band {
    flex-wrap: wrap;
  }

  .dash-oee-kpi-pill {
    min-width: 0;
    flex: 1 1 auto;
  }
}

.dash-ref-line-section-panel {
  padding: 24px 24px 22px !important;
}

.dash-ref-line-section-head {
  margin-bottom: 12px;
}

.dash-ref-line-section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 950;
  color: #0f1f38;
  letter-spacing: -0.02em;
}

.dash-ref-line-detail-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 14px 14px 12px !important;
  min-height: 0;
  height: auto;
  border-radius: var(--dash-kpi-cell-radius);
}

.dash-ref-line-detail-card .dash-line-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.dash-line-detail-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dash-line-detail-name {
  font-weight: 950;
  font-size: 14px;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.dash-line-detail-pct {
  margin: 0;
  font-size: 24px;
  font-weight: 950;
  letter-spacing: -0.03em;
  color: #0f1f38;
  line-height: 1.1;
}

.dash-line-spark {
  width: 100%;
  height: 30px;
  display: block;
  flex-shrink: 0;
}

.dash-ref-hourly-panel {
  padding: 28px 28px 26px !important;
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  min-height: 0;
}

.dash-ref-hourly-panel > .dash-ref-panel-head {
  flex-shrink: 0;
  margin-bottom: 10px;
}

.dash-ref-hourly-panel > .dash-ref-panel-head .panel-kicker {
  margin-bottom: 4px;
}

.dash-ref-hourly-panel > .dash-ref-panel-head h2 {
  margin-top: 4px;
}

.dash-status-panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px 20px;
}

.dash-status-toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.dash-status-search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  min-width: 140px;
}

.dash-status-search:focus-within {
  border-color: #94a3b8;
  background: #ffffff;
}

.dash-status-search-ico {
  flex-shrink: 0;
  color: #64748b;
}

.dash-status-search input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  color: #0f172a;
  outline: none;
}

.dash-status-search input::placeholder {
  color: #94a3b8;
}

.dash-status-profile-btn {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  padding: 0;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.dash-status-profile-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #0f172a;
}

.dash-donut-center-line {
  font-size: 15px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.02em;
}

.dash-alarm-tile-ico {
  margin-bottom: 4px;
  color: #475569;
}

.dash-alarm-summary-tile--urgent .dash-alarm-tile-ico {
  color: #991b1b;
}

.dash-alarm-summary-tile--pending .dash-alarm-tile-ico--muted {
  color: #b45309;
}

.dash-alarm-summary-tile--done .dash-alarm-tile-ico {
  color: #047857;
}

.dash-alarm-summary-tile--info .dash-alarm-tile-ico {
  color: #002c5f;
}

@media (max-width: 520px) {
  .dash-ref-oee-panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .dash-ref-oee-kpi-col {
    align-items: flex-start;
    margin-left: 0;
    text-align: left;
  }

  .dash-ref-oee-kpi-col .dash-oee-yoy-row {
    justify-content: flex-start;
  }
}

.dash-oee-yoy-row {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
}

.dash-yoy-label {
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.02em;
}

.dash-yoy-value {
  font-size: 14px;
  font-weight: 950;
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.dash-yoy-ar {
  font-size: 13px;
}

.dash-oee-yoy-row.dash-yoy--inc .dash-yoy-value,
.dash-line-yoy-row.dash-yoy--inc .dash-yoy-value {
  color: #d92d20;
}

.dash-oee-yoy-row.dash-yoy--dec .dash-yoy-value,
.dash-line-yoy-row.dash-yoy--dec .dash-yoy-value {
  color: #0057a4;
}

.dash-oee-yoy-row.dash-yoy--flat .dash-yoy-value,
.dash-line-yoy-row.dash-yoy--flat .dash-yoy-value {
  color: #64748b;
}

.dash-ref-oee-panel {
  padding: 28px 28px 32px !important;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.dash-line-mark {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 12px;
  border: 1px solid transparent;
}

.dash-line-mark--0 {
  background: rgba(0, 44, 95, 0.1);
  border-color: rgba(0, 44, 95, 0.12);
  color: #002c5f;
}

.dash-line-mark--1 {
  background: rgba(0, 170, 210, 0.12);
  border-color: rgba(0, 170, 210, 0.18);
  color: #006d87;
}

.dash-line-mark--2 {
  background: rgba(10, 159, 104, 0.12);
  border-color: rgba(10, 159, 104, 0.2);
  color: #05764d;
}

.dash-line-body {
  flex: 0 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  min-width: 0;
  width: 100%;
}

.dash-line-kpi {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-width: 0;
  width: 100%;
}

.dash-line-name-tag {
  display: inline-flex;
  align-items: center;
  align-self: center;
  padding: 4px 11px;
  border-radius: 999px;
  font-weight: 950;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: #334155;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
}

.dash-line-yoy-row {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 10px;
  width: 100%;
}

.dash-line-yoy-row--detail {
  display: grid;
  grid-template-columns: minmax(5.75rem, max-content) minmax(0, 1fr);
  align-items: baseline;
  gap: 6px 12px;
  justify-content: unset;
}

.dash-line-yoy-row--detail .dash-yoy-value {
  justify-self: start;
}

.dash-oee-summary-chart {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  min-height: 0;
  min-width: 0;
}

.dash-oee-bar-wrap {
  flex: 0 0 auto;
  min-height: 0;
  min-width: 0;
  padding: 2px 0 2px;
  display: flex;
  flex-direction: column;
}

.dash-oee-bar-wrap :deep(> div) {
  flex: 0 0 auto;
  min-height: 240px;
}

.dash-line-pct {
  font-size: 30px;
  font-weight: 950;
  color: #0f1f38;
  letter-spacing: -0.03em;
  line-height: 1.12;
  text-align: center;
}

.dash-ref-status-panel {
  padding: 28px 28px 32px !important;
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  min-height: 0;
}

.dash-ref-status-panel-head {
  flex-shrink: 0;
}

.dash-status-body {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  justify-content: flex-start;
  align-items: stretch;
  gap: 8px;
  padding: 12px 8px 8px;
  min-height: 0;
}

.dash-status-donut-wrap {
  position: relative;
  width: 100%;
  max-width: none;
  margin: 0 auto;
  flex: 0 0 auto;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.dash-status-custom-legend {
  list-style: none;
  margin: 12px 0 0;
  padding: 14px 18px 16px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 14px 28px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e8edf2;
}

.dash-status-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 0;
}

.dash-status-legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 4px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
}

.dash-status-legend-label {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: #475569;
}

.dash-status-legend-num {
  font-size: 17px;
  font-weight: 950;
  letter-spacing: -0.02em;
  color: #0f172a;
  margin-left: 4px;
}

.dash-donut-center-overlay {
  position: absolute;
  left: 50%;
  top: 43%;
  transform: translate(-50%, -50%);
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: min(240px, 56%);
  max-width: min(240px, 56%);
  pointer-events: none;
  text-align: center;
  transition: color 0.15s ease;
}

.dash-donut-center-primary {
  width: 100%;
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: #475569;
  line-height: 1.25;
  letter-spacing: 0.02em;
  text-align: center;
}

.dash-donut-center-value {
  width: 100%;
  margin: 0;
  font-size: 22px;
  font-weight: 950;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.25;
  text-align: center;
}

.dash-donut-center-overlay--slice .dash-donut-center-primary {
  font-weight: 950;
  color: #0f172a;
}

.dash-donut-center-overlay--slice .dash-donut-center-value {
  color: #002c5f;
}

.dash-ref-alarm-hub {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex: 0 0 auto;
  min-height: var(--dash-kpi-cell-min-h);
  min-width: 0;
  padding: 28px 28px 32px !important;
  width: 100%;
}

.dash-ref-alarm-hub-head {
  flex-shrink: 0;
  width: 100%;
  text-align: left;
}

.dash-ref-alarm-hub-head > div {
  max-width: none;
  margin-inline: 0;
}

.dash-ref-alarm-hub-title {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 950;
  color: #0f1f38;
  letter-spacing: -0.02em;
  line-height: 1.35;
}

.dash-alarm-title-suffix {
  white-space: nowrap;
  font-weight: inherit;
}

.dash-alarm-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  gap: var(--dash-kpi-gap);
  flex: 1 1 auto;
  min-height: var(--dash-kpi-cell-min-h);
  min-width: 0;
  width: 100%;
  align-items: stretch;
  justify-items: stretch;
}

@media (min-width: 520px) {
  .dash-alarm-summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    grid-template-rows: 1fr;
  }
}

.dash-alarm-summary-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-sizing: border-box;
  min-height: var(--dash-kpi-cell-min-h);
  height: 100%;
  padding: var(--dash-kpi-cell-pad-y) var(--dash-kpi-cell-pad-x)
    calc(var(--dash-kpi-cell-pad-y) + 2px);
  border-radius: var(--dash-kpi-cell-radius);
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.dash-alarm-summary-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  text-align: center;
}

.dash-alarm-summary-value {
  font-size: 26px;
  font-weight: 950;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1;
  text-align: center;
}

.dash-alarm-summary-unit {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  text-align: center;
}

.dash-alarm-summary-tile--urgent {
  border-color: #fecaca;
  background: linear-gradient(180deg, #fff 0%, #fff5f5 100%);
}

.dash-alarm-summary-tile--urgent .dash-alarm-summary-value {
  color: #991b1b;
}

.dash-alarm-summary-tile--done {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #fff 0%, #f0fdf4 100%);
}

.dash-alarm-summary-tile--done .dash-alarm-summary-value {
  color: #047857;
}

.dash-alarm-summary-tile--pending {
  border-color: #fde68a;
  background: linear-gradient(180deg, #fff 0%, #fffbeb 100%);
}

.dash-alarm-summary-tile--pending .dash-alarm-summary-value {
  color: #92400e;
}

@media (max-width: 1180px) {
  section.dashboard-main.dash-dashboard-fill .dash-scada-surface {
    flex: none;
    min-height: auto;
    display: block;
  }

  section.dashboard-main.dash-dashboard-fill .dash-ref-page {
    flex: none;
    min-height: auto;
  }

  section.dashboard-main.dash-dashboard-fill .dash-ref-grid--dashboard {
    flex: none;
    min-height: auto;
  }

  .dash-ref-grid--dashboard {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    align-items: start;
    min-height: 0;
  }

  .dash-ref-col--stack {
    height: auto;
    align-self: start;
  }

  .dash-ref-col--side .dash-ref-alarm-hub {
    margin-top: 0;
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-line-section-panel,
  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-hourly-panel {
    flex: 0 0 auto;
    min-height: 0;
  }

  .dash-ref-grid--dashboard > .dash-ref-col--stack:first-child > .dash-ref-line-section-panel {
    display: block;
  }

  .dash-ref-line-section-panel .dash-ref-line-stack--detail {
    flex: none;
    height: auto;
    grid-template-rows: none;
  }

  .dash-ref-line-detail-card {
    height: auto;
  }

  .dash-ref-line-detail-card .dash-line-spark {
    margin-top: 0;
  }

  .dash-scada-surface {
    min-height: auto;
  }
}
</style>
