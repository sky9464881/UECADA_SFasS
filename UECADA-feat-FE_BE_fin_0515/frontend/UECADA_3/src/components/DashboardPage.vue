<script setup lang="ts">
/**
 * SCADA 메인 대시보드 — 디자인 시스템
 * 배경 #F4F7F9, 5:5 그리드, 카드 12px·부드러운 그림자, gap 24px
 */
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { GraphicComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

// ApexCharts 는 1MB+ 라이브러리이므로 대시보드 진입 시점에만 lazy 로드한다.
const apexchart = defineAsyncComponent(() => import('vue3-apexcharts'))
import {
  AlertTriangle,
  Bell,
  CalendarDays,
  CheckCircle2,
  Cog,
  Factory,
  LogOut,
  Package,
  Siren,
} from 'lucide-vue-next'
import VChart from 'vue-echarts'
import 'vue-echarts/style.css'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useDashboard } from '@/composables/useDashboard'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent, GraphicComponent])

/** 라인 카드 좌측 마크 (라인별 구분) */
const lineMarkIcons = [Factory, Cog, Package]

const { navItems } = useAppNav()
const logout = useLogout()
const { data: dashboardData } = useDashboard()

/** YoY 증감: 증가=빨강, 감소=파랑 (비교 구간은 상단 세그먼트로 표시) */
function yoyClass(delta: number) {
  if (delta > 0) return 'dash-yoy--inc'
  if (delta < 0) return 'dash-yoy--dec'
  return 'dash-yoy--flat'
}

function yoyArrow(delta: number) {
  if (delta > 0) return '▲'
  if (delta < 0) return '▼'
  return '—'
}

function yoyAbs(delta: number) {
  if (delta === 0) return '0'
  return `${Math.abs(delta)}`
}

/** 주식 시세형: 양수는 +접두, 음수는 그대로(이미 -) */
function yoySignedDisplay(delta: number) {
  if (delta === 0) return '0'
  if (delta > 0) return `+${delta}`
  return `${delta}`
}

const totalOeeDisplay = computed(() => {
  const v = dashboardData.value?.factoryOee
  return v == null ? '-' : v.toFixed(1)
})

/**
 * 종합 OEE 증감 (%p) — 백엔드가 비교 기간 데이터를 제공하지 않아 임시 0 처리.
 * TODO: backend `/api/dashboard/frontend` 에 yoy/mom/dod 필드 추가 후 연동.
 */
type PeriodKey = 'day' | 'month' | 'year'

const totalOeeCompare: Record<PeriodKey, number> = {
  day: 0,
  month: 0,
  year: 0,
}

/** 전역 비교 기준 — 패널 전체 동일 적용 */
const dashboardPeriod = ref<PeriodKey>('day')

const totalOeeDelta = computed(() => totalOeeCompare[dashboardPeriod.value])

const comparePeriodButtons: { key: PeriodKey; label: string }[] = [
  { key: 'day', label: '전일' },
  { key: 'month', label: '전월' },
  { key: 'year', label: '전년' },
]

const FALLBACK_HOUR_LABELS = [
  '00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00',
  '14:00', '16:00', '18:00', '20:00', '22:00', '24:00',
]

const oeeHourLabels = computed(() => {
  const first = dashboardData.value?.oeeHourlySeries?.[0]
  if (!first || !first.data?.length) return FALLBACK_HOUR_LABELS
  return first.data.map((p) => p.time)
})

const oeeHourlySeries = computed(() => {
  const series = dashboardData.value?.oeeHourlySeries
  if (!series?.length) {
    return [
      { name: '라인 A', data: [] },
      { name: '라인 B', data: [] },
      { name: '라인 C', data: [] },
    ]
  }
  return series.map((s) => ({
    name: s.lineName ?? s.lineId,
    data: (s.data ?? []).map((p) => (p.oee == null ? null : Number(p.oee))),
  }))
})

const oeeHourlyOptions = computed((): Record<string, unknown> => ({
  chart: { type: 'line', toolbar: { show: false }, fontFamily: 'inherit', zoom: { enabled: false } },
  colors: ['#002c5f', '#62b3ff', '#0a9f68'],
  stroke: {
    curve: 'straight',
    width: 2.5,
  },
  markers: {
    size: 4,
    strokeWidth: 2,
    strokeColors: '#ffffff',
    hover: { size: 6 },
  },
  dataLabels: {
    enabled: false,
  },
  xaxis: {
    categories: oeeHourLabels.value,
    labels: { style: { fontSize: '12px', fontWeight: 700, colors: '#475569' } },
    axisBorder: { show: true, color: '#cbd5e1' },
    axisTicks: { show: true, color: '#cbd5e1' },
  },
  yaxis: {
    min: 50,
    max: 100,
    tickAmount: 5,
    labels: {
      formatter: (v: number) => `${v}%`,
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
    shared: true,
    intersect: false,
    style: {
      fontSize: '14px',
      fontFamily: 'inherit',
    },
    y: { formatter: (v: number | null) => (v == null ? '-' : `${Number(v).toFixed(1)}%`) },
  },
}))

interface LineStatRow {
  name: string
  pct: string
  deltas: Record<PeriodKey, number>
}

const lineStats = computed<LineStatRow[]>(() => {
  const stats = dashboardData.value?.lineStats ?? []
  return stats.map((s) => ({
    name: s.lineName ?? s.lineId,
    pct: s.oee == null ? '-' : `${Number(s.oee).toFixed(1)}%`,
    // TODO: backend 가 라인별 비교 데이터 제공 시 매핑
    deltas: { day: 0, month: 0, year: 0 },
  }))
})

function lineDelta(ln: LineStatRow) {
  return ln.deltas[dashboardPeriod.value]
}

const equipmentTotalCount = computed(() => dashboardData.value?.statusDonut?.total ?? 0)

/**
 * 설비 상태 분포 — 백엔드 4상태(running/standby/alarm/maintenance)를
 * 화면 3슬라이스(가동/정지/대기) 로 매핑하고 백분율로 환산.
 * - 가동 = running
 * - 정지 = alarm
 * - 대기 = standby + maintenance
 */
const statusDonutSeries = computed(() => {
  const d = dashboardData.value?.statusDonut
  if (!d || !d.total) return [0, 0, 0]
  const running = (d.running / d.total) * 100
  const stopped = (d.alarm / d.total) * 100
  const waiting = ((d.standby + d.maintenance) / d.total) * 100
  return [round1(running), round1(stopped), round1(waiting)]
})

function round1(v: number) {
  return Math.round(v * 10) / 10
}

const statusDonutSliceLabels = ['가동', '정지', '대기']

const statusDonutColors = ['#0a9f68', '#d92d20', '#f0b429']

/**
 * 알람 현황 요약 — 백엔드 `alarmSummary` 단일 시점 기준.
 * 비교 기간(day/month/year)별 데이터는 백엔드 미제공 → 동일 값 노출.
 */
const alarmSummary = computed(() => {
  const a = dashboardData.value?.alarmSummary
  return {
    total: a?.total ?? 0,
    urgent: a?.critical ?? 0,
    resolved: a?.resolved ?? 0,
    pending: a?.open ?? 0,
  }
})

/** 도넛 중앙 대수만 표시 — 슬라이스 호버 시 숨겨 ECharts emphasis 중앙 라벨과 겹치지 않게 */
const showDonutCenterSummary = ref(true)

watch(dashboardPeriod, () => {
  showDonutCenterSummary.value = true
})

function onStatusPieMouseOver(params: { seriesType?: string }) {
  if (params.seriesType === 'pie') {
    showDonutCenterSummary.value = false
  }
}

function onStatusPieGlobalOut() {
  showDonutCenterSummary.value = true
}

/** ECharts 예시와 동일 구조: tooltip item, legend 상단 중앙, 도넛·둥근 모서리·강조 시 중앙 라벨 */
const statusPieChartOption = computed(() => {
  const seriesData = statusDonutSliceLabels.map((name, i) => ({
    value: statusDonutSeries.value[i],
    name,
    itemStyle: { color: statusDonutColors[i] },
  }))

  const showCenter = showDonutCenterSummary.value

  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}%',
    },
    legend: {
      top: '5%',
      left: 'center',
      itemGap: 18,
      textStyle: {
        fontSize: 15,
        fontWeight: 800,
        color: '#475569',
      },
    },
    graphic: showCenter
      ? [
          {
            type: 'text',
            left: 'center',
            top: '47%',
            z: 10,
            style: {
              text: `${equipmentTotalCount.value}대`,
              textAlign: 'center',
              fill: '#0f172a',
              fontSize: 25,
              fontWeight: 950,
            },
          },
        ]
      : [],
    series: [
      {
        name: '설비 상태',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '56%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 40,
            fontWeight: 'bold',
            formatter: '{b}\n{c}%',
          },
        },
        labelLine: {
          show: false,
        },
        data: seriesData,
      },
    ],
  }
})

/** 시간별 차트 — 패널 가변 높이에 맞춤 (데스크톱 정렬용) */
const hourlyBarWrapRef = ref<HTMLElement | null>(null)
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
      <RouterLink class="dashboard-brand" :to="{ name: 'dashboard' }">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스카다</small>
        </span>
      </RouterLink>

      <nav class="dashboard-nav">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.to">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <span>관리자</span>
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
          <RouterLink class="ghost-button" :to="{ name: 'alarms' }">
            <Bell :size="16" />
            <span>최근 알람</span>
          </RouterLink>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
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
                  <span
                    class="dash-oee-kpi-pill dash-oee-kpi-pill--trail dash-oee-kpi-pill--hero"
                    :class="yoyClass(totalOeeDelta)"
                  >
                    <span class="dash-oee-kpi-pill-change">
                      <span class="dash-oee-kpi-pill-ar" aria-hidden="true">{{ yoyArrow(totalOeeDelta) }}</span>
                      <span class="dash-yoy-num-slot">{{ yoySignedDisplay(totalOeeDelta) }}</span>
                      <span class="dash-oee-kpi-pill-unit">%p</span>
                    </span>
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
                    <div class="dash-line-detail-head">
                      <div class="dash-line-detail-left">
                        <div class="dash-line-mark" :class="[`dash-line-mark--${lineIdx}`]">
                          <component
                            :is="lineMarkIcons[lineIdx]"
                            :size="20"
                            :stroke-width="2"
                            aria-hidden="true"
                          />
                        </div>
                        <div class="dash-line-detail-title-group">
                          <span class="dash-line-detail-name">{{ ln.name }}</span>
                          <strong class="dash-line-detail-pct">{{ ln.pct }}</strong>
                        </div>
                      </div>
                      <div class="dash-line-detail-delta" :class="yoyClass(lineDelta(ln))">
                        <span class="dash-yoy-value dash-line-detail-delta-value">
                          <span class="dash-yoy-ar" aria-hidden="true">{{ yoyArrow(lineDelta(ln)) }}</span>
                          <span class="dash-yoy-num-slot">{{ yoyAbs(lineDelta(ln)) }}</span>%p
                        </span>
                      </div>
                    </div>
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
                    <apexchart type="line" :height="hourlyChartHeight" :options="oeeHourlyOptions" :series="oeeHourlySeries" />
                  </div>
                </div>
              </article>
            </div>

            <!-- 우측 ~1/3: 설비 상태 분포 → 알람 요약 -->
            <div class="dash-ref-col dash-ref-col--stack dash-ref-col--side">
              <article class="dashboard-panel dash-ref-status-panel">
                <div class="dash-ref-panel-head dash-ref-status-panel-head">
                  <div>
                    <p class="panel-kicker">Equipment status</p>
                    <h2>설비 상태 분포</h2>
                  </div>
                </div>
                <div class="dash-status-body">
                  <div class="dash-status-donut-wrap">
                    <v-chart
                      class="dash-status-echart"
                      :option="statusPieChartOption"
                      autoresize
                      @mouseover="onStatusPieMouseOver"
                      @globalout="onStatusPieGlobalOut"
                    />
                  </div>
                </div>
              </article>

              <article class="dashboard-panel dash-ref-alarm-hub" aria-label="알람 현황 상세 요약">
                <div class="dash-ref-panel-head dash-ref-alarm-hub-head">
                  <div>
                    <p class="panel-kicker">Alarm detail</p>
                    <h2 class="dash-ref-alarm-hub-title">알람 현황 상세 요약</h2>
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
    flex: 0 0 auto;
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
    flex: 0 0 auto;
    min-height: 0;
    align-self: stretch;
    height: auto;
    grid-template-rows: none;
  }

  .dash-ref-line-detail-card {
    height: auto;
    min-height: 0;
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

  .dash-ref-line-stack.dash-ref-line-stack--detail {
    min-height: 0;
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
  align-items: flex-end;
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

/* 전체 OEE 카드: 증감만 표시 (비교 구간은 상단 버튼) */
.dash-oee-kpi-pill--hero {
  min-width: 0;
  max-width: min(100%, 22rem);
  padding: 7px 12px 8px;
  gap: 0;
  align-items: center;
}

.dash-oee-kpi-pill--hero .dash-oee-kpi-pill-change {
  gap: 3px;
  font-size: 14px;
  font-weight: 950;
  letter-spacing: -0.02em;
  flex-shrink: 0;
}

.dash-oee-kpi-pill--hero .dash-oee-kpi-pill-unit {
  font-size: 0.82em;
  font-weight: 800;
  margin-left: 0;
  letter-spacing: 0;
}

/* OEE 총괄 증감: 국내 시세(상승 빨강·하락 파랑) — 라인 카드와 동일 톤 */
.dash-oee-kpi-pill.dash-yoy--inc {
  background: rgba(217, 45, 32, 0.1);
  color: #b91c1c;
  border-color: rgba(217, 45, 32, 0.28);
}

.dash-oee-kpi-pill.dash-yoy--dec {
  background: rgba(0, 87, 164, 0.1);
  color: #0057a4;
  border-color: rgba(0, 87, 164, 0.28);
}

.dash-oee-kpi-pill.dash-yoy--flat {
  background: #f1f5f9;
  color: #64748b;
  border-color: #e2e8f0;
}

.dash-oee-kpi-pill-scope {
  font-weight: 800;
  color: inherit;
  opacity: 0.92;
}

.dash-oee-kpi-pill-change {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  font-weight: 950;
}

.dash-oee-kpi-pill-unit {
  font-weight: 800;
  font-size: 0.92em;
  opacity: 0.88;
}

.dash-oee-kpi-pill-ar {
  font-weight: 950;
  font-size: 0.95em;
  line-height: 1;
}

.dash-oee-kpi-pill.dash-yoy--inc .dash-oee-kpi-pill-ar,
.dash-oee-kpi-pill.dash-yoy--inc .dash-yoy-num-slot {
  color: #d92d20;
}

.dash-oee-kpi-pill.dash-yoy--dec .dash-oee-kpi-pill-ar,
.dash-oee-kpi-pill.dash-yoy--dec .dash-yoy-num-slot {
  color: #0057a4;
}

.dash-oee-kpi-pill.dash-yoy--flat .dash-oee-kpi-pill-ar,
.dash-oee-kpi-pill.dash-yoy--flat .dash-yoy-num-slot {
  color: #64748b;
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

  .dash-oee-kpi-pill--hero {
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 6px;
    max-width: none;
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
  font-size: calc(20px + 5pt);
  font-weight: 950;
  color: #0f1f38;
  letter-spacing: -0.02em;
}

.dash-ref-line-detail-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  padding: 12px 14px !important;
  min-height: 0;
  height: auto;
  border-radius: var(--dash-kpi-cell-radius);
}

.dash-ref-line-detail-card .dash-line-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.dash-line-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px 12px;
  min-width: 0;
  flex-wrap: wrap;
}

.dash-line-detail-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1 1 auto;
}

.dash-line-detail-title-group {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
}

.dash-line-detail-name {
  font-weight: 950;
  font-size: 14px;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.dash-line-detail-pct {
  margin: 0;
  font-size: 22px;
  font-weight: 950;
  letter-spacing: -0.03em;
  color: #0f1f38;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.dash-line-detail-delta {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-end;
  gap: 0;
  flex-shrink: 0;
  min-width: 0;
  padding: 2px 0 2px 8px;
}

.dash-line-detail-delta-value {
  font-size: 13px;
  font-weight: 950;
  line-height: 1.2;
  white-space: nowrap;
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

.dash-status-echart {
  width: 100%;
  height: 380px;
  min-height: 340px;
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
.dash-line-yoy-row.dash-yoy--inc .dash-yoy-value,
.dash-line-detail-delta.dash-yoy--inc .dash-yoy-value {
  color: #d92d20;
}

.dash-oee-yoy-row.dash-yoy--dec .dash-yoy-value,
.dash-line-yoy-row.dash-yoy--dec .dash-yoy-value,
.dash-line-detail-delta.dash-yoy--dec .dash-yoy-value {
  color: #0057a4;
}

.dash-oee-yoy-row.dash-yoy--flat .dash-yoy-value,
.dash-line-yoy-row.dash-yoy--flat .dash-yoy-value,
.dash-line-detail-delta.dash-yoy--flat .dash-yoy-value {
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
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
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

  .dash-scada-surface {
    min-height: auto;
  }
}
</style>
