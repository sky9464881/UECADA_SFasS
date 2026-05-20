<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useQuery } from '@tanstack/vue-query'
import { RouterLink, useRoute } from 'vue-router'
import {
  Activity,
  AlertTriangle,
  CalendarDays,
  Cog,
  Droplets,
  Factory,
  Flame,
  Gauge,
  LogOut,
  MapPinned,
  Play,
  Printer,
  Search,
  Square,
  Wrench,
  X,
} from 'lucide-vue-next'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useEquipmentCatalog } from '@/composables/useEquipmentCatalog'
import type { EquipmentCategory, EquipmentSpecificMetric } from '@/composables/useEquipmentCatalog'
import type { Component } from 'vue'
import EquipmentCategoryGrid from '@/components/equipment/EquipmentCategoryGrid.vue'
import CategorySummaryPanel from '@/components/equipment/CategorySummaryPanel.vue'
import CategoryEquipmentList from '@/components/equipment/CategoryEquipmentList.vue'
import { fetchRealtimeVibration } from '@/api/vibrationApi'
import { edPageIdForCategory, isWebScadaConfigured } from '@/composables/useWebScadaLinks'

const WebScadaOverlay = defineAsyncComponent(() => import('@/components/WebScadaOverlay.vue'))

const { navItems } = useAppNav()
const logout = useLogout()
const { categories: backendCategories } = useEquipmentCatalog()
const route = useRoute()

const CATEGORY_ICON_MAP: Record<string, Component> = {
  casting: Flame,
  machining: Cog,
  washing: Droplets,
  assembly: Wrench,
  inspection: Search,
}

interface CategoryWithIcon extends EquipmentCategory {
  icon: Component
}

// 백엔드 데이터 + 카테고리별 아이콘 매핑.
const categories = computed<CategoryWithIcon[]>(() =>
  backendCategories.value.map((c) => ({
    ...c,
    icon: CATEGORY_ICON_MAP[c.id] ?? Factory,
  })),
)

const selectedCategoryId = ref('casting')
const selectedEquipmentId = ref('CAST-02')
const isEquipmentPopupOpen = ref(false)
const webScadaReady = isWebScadaConfigured()
const webScadaOverlayOpen = ref(false)
const webScadaOverlayTitle = computed(() => {
  const equipment = selectedEquipment.value
  if (!equipment || equipment.id === '-') return '설비 상세 웹스카다'
  return `${equipment.id} · ${equipment.name}`
})
const webScadaOverlayPageId = computed(() => edPageIdForCategory(selectedCategoryId.value))
const rawVibrationChartEl = ref<HTMLDivElement | null>(null)
const fftChartEl = ref<HTMLDivElement | null>(null)
const trendChartEl = ref<HTMLDivElement | null>(null)
const vibrationTrendHistory = ref<Array<{
  windowIndex: number | string
  timestamp: number
  rms: number
  peakToPeak: number
  anomalyScore: number
}>>([])
const chartInstances: Partial<Record<'raw' | 'fft' | 'trend', echarts.ECharts>> = {}
let chartRenderPending = false

const EMPTY_CATEGORY = Object.freeze({
  id: '',
  name: '-',
  icon: Factory,
  status: '-',
  count: 0,
  running: 0,
  stopped: 0,
  waiting: 0,
  avgRate: 0,
  defectCount: 0,
  description: '데이터 로딩 중',
  equipment: [],
})

const selectedCategory = computed(() =>
  categories.value.find((category) => category.id === selectedCategoryId.value)
    ?? categories.value[0]
    ?? EMPTY_CATEGORY,
)

const EMPTY_EQUIPMENT = Object.freeze({
  id: '-',
  name: '-',
  line: '-',
  state: '대기',
  rate: 0,
  defects: 0,
  operator: '-',
  cycle: '-',
  common: [],
  specific: [],
})

const selectedEquipment = computed(() => {
  const cat = selectedCategory.value
  if (!cat) return EMPTY_EQUIPMENT
  return (
    cat.equipment.find((equipment) => equipment.id === selectedEquipmentId.value)
    ?? cat.equipment[0]
    ?? EMPTY_EQUIPMENT
  )
})

const realtimeVibrationQuery = useQuery({
  queryKey: computed(() => ['equipment-vibration-realtime', selectedEquipment.value.id]),
  queryFn: () => fetchRealtimeVibration(selectedEquipment.value.id),
  enabled: computed(() => isEquipmentPopupOpen.value && !!selectedEquipment.value.id && selectedEquipment.value.id !== '-'),
  refetchInterval: 1000,
  staleTime: 500,
})

const realtimeVibration = computed(() => realtimeVibrationQuery.data.value ?? null)
const realtimeAnalysis = computed(() => realtimeVibration.value?.analysis ?? null)
const vibrationValues = computed(() => realtimeVibration.value?.values ?? [])
const vibrationSampleCount = computed(() =>
  realtimeVibration.value?.window?.valuesLength ?? vibrationValues.value.length,
)

// 백엔드 데이터 로드 후, 비어있던 selection 을 첫 카테고리/첫 설비로 자동 보정.
watch(
  categories,
  (list) => {
    if (!list.length) return
    const queryEquipmentId = typeof route.query.equipmentId === 'string' ? route.query.equipmentId : ''
    if (queryEquipmentId) {
      const matchedCategory = list.find((category) =>
        category.equipment.some((equipment) => equipment.id === queryEquipmentId),
      )
      if (matchedCategory) {
        selectedCategoryId.value = matchedCategory.id
        selectedEquipmentId.value = queryEquipmentId
        if (route.query.popup === '1' || route.query.popup === 'true') {
          webScadaOverlayOpen.value = webScadaReady
        }
        return
      }
    }
    const cat = list.find((c) => c.id === selectedCategoryId.value) ?? list[0]
    if (cat.id !== selectedCategoryId.value) {
      selectedCategoryId.value = cat.id
    }
    if (!cat.equipment.find((e) => e.id === selectedEquipmentId.value)) {
      selectedEquipmentId.value = cat.equipment[0]?.id ?? ''
    }
  },
  { immediate: true },
)

watch(
  () => route.query.equipmentId,
  () => {
    const list = categories.value
    const queryEquipmentId = typeof route.query.equipmentId === 'string' ? route.query.equipmentId : ''
    if (!list.length || !queryEquipmentId) return
    const matchedCategory = list.find((category) =>
      category.equipment.some((equipment) => equipment.id === queryEquipmentId),
    )
    if (!matchedCategory) return
    selectedCategoryId.value = matchedCategory.id
    selectedEquipmentId.value = queryEquipmentId
    if (route.query.popup === '1' || route.query.popup === 'true') {
      webScadaOverlayOpen.value = webScadaReady
    }
  },
)

const selectCategory = (category: CategoryWithIcon) => {
  selectedCategoryId.value = category.id
  selectedEquipmentId.value = category.equipment[0]?.id ?? ''
  isEquipmentPopupOpen.value = false
  webScadaOverlayOpen.value = false
}

const openEquipmentPopup = () => {
  if (!webScadaReady) return
  isEquipmentPopupOpen.value = false
  webScadaOverlayOpen.value = true
}

const closeEquipmentPopup = () => {
  isEquipmentPopupOpen.value = false
}

const metricNumber = (value: unknown) => {
  const match = String(value).replace(/,/g, '').match(/\d+(\.\d+)?/)
  return match ? Number(match[0]) : 0
}

const getCommonMetric = (label: string): string =>
  selectedEquipment.value.common.find((metric) => metric.label === label)?.value ?? '-'

const getCommonMetricLike = (...keywords: string[]): string =>
  selectedEquipment.value.common.find((metric) =>
    keywords.some((keyword) => metric.label.includes(keyword)),
  )?.value ?? '-'

const clampPercent = (value: number) => Math.max(0, Math.min(100, Math.round(value)))

function fixedMetric(value: number | null | undefined, digits = 3): string {
  return value == null || Number.isNaN(Number(value)) ? '-' : Number(value).toFixed(digits)
}

function predictionLabel(value: string | null | undefined): string {
  if (!value) return '-'
  if (value.toLowerCase() === 'normal') return '정상'
  return value
}

const popupSummaryItems = computed(() => [
  { label: '운전상태', value: selectedEquipment.value.state },
  { label: '가동률', value: `${selectedEquipment.value.rate}%` },
  { label: '싸이클 타임', value: selectedEquipment.value.cycle },
  { label: '온도', value: getCommonMetricLike('온도') },
  { label: '전류', value: getCommonMetricLike('전류') },
  { label: '전압', value: getCommonMetricLike('전압') },
])

const vibrationAnalysisCards = computed(() => {
  const analysis = realtimeAnalysis.value
  const features = analysis?.features
  return [
    { label: 'RMS', value: fixedMetric(features?.rms, 5) },
    { label: 'Peak-to-Peak', value: fixedMetric(features?.peakToPeak, 5) },
    { label: 'Crest Factor', value: fixedMetric(features?.crestFactor, 3) },
    { label: 'Kurtosis', value: fixedMetric(features?.kurtosis, 3) },
    { label: 'AI 예측 후보', value: predictionLabel(analysis?.prediction) },
    { label: '신뢰도', value: analysis?.confidence == null ? '-' : `${fixedMetric(analysis.confidence * 100, 1)}%` },
    { label: '분석 샘플', value: `${vibrationSampleCount.value}` },
    { label: 'FFT Bin', value: `${analysis?.fft?.binCount ?? analysis?.fft?.frequencies?.length ?? '-'}` },
  ]
})

const vibrationTimeRange = computed(() => {
  const window = realtimeVibration.value?.window
  const samplingRate = window?.samplingRate || realtimeAnalysis.value?.samplingRate || 16000
  const endMs = Date.parse(window?.timestamp ?? realtimeAnalysis.value?.timestamp ?? realtimeVibration.value?.receivedAt ?? '')
  if (!Number.isFinite(endMs)) return '-'
  const startMs = endMs - (vibrationValues.value.length / samplingRate) * 1000
  return `${formatCompactDateTime(startMs)} ~ ${formatCompactDateTime(endMs)}`
})

function formatCompactDateTime(value: number): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  const ss = String(date.getSeconds()).padStart(2, '0')
  return `${yyyy}. ${mm}. ${dd} ${hh}:${mi}:${ss}`
}

const qualityPercent = computed(() => {
  const ok = metricNumber(getCommonMetric('OK'))
  const ng = metricNumber(getCommonMetric('NG'))
  const total = ok + ng

  return total ? clampPercent((ok / total) * 100) : 0
})

const operationTrend = computed(() => {
  const base = selectedEquipment.value.rate
  return [
    { label: '08시', value: clampPercent(base - 7) },
    { label: '09시', value: clampPercent(base - 3) },
    { label: '10시', value: clampPercent(base + 1) },
    { label: '11시', value: clampPercent(base - 1) },
    { label: '12시', value: clampPercent(base) },
  ]
})

const sensorChart = computed(() => [
  { label: '가동률', value: `${selectedEquipment.value.rate}%`, percent: selectedEquipment.value.rate },
  { label: '전류', value: getCommonMetric('전류'), percent: clampPercent((metricNumber(getCommonMetric('전류')) / 50) * 100) },
  { label: '온도', value: getCommonMetric('온도'), percent: clampPercent((metricNumber(getCommonMetric('온도')) / 80) * 100) },
  { label: '습도', value: getCommonMetric('습도'), percent: clampPercent(metricNumber(getCommonMetric('습도'))) },
  { label: '진동', value: getCommonMetric('진동'), percent: clampPercent((metricNumber(getCommonMetric('진동')) / 3) * 100) },
])

/** 인쇄용: 전 설비 목록 (카테고리·요약 KPI) */
const equipmentReportRows = computed(() =>
  categories.value.flatMap((category) =>
    category.equipment.map((equipment) => ({
      categoryName: category.name,
      id: equipment.id,
      name: equipment.name,
      line: equipment.line,
      state: equipment.state,
      rate: equipment.rate,
      defects: equipment.defects,
      cycle: equipment.cycle,
      operator: equipment.operator,
    })),
  ),
)

const reportGeneratedAt = ref('')

/** 인쇄 레포트 상단 요약 KPI */
const printReportSummary = computed(() => {
  const list = categories.value
  const totalUnits = list.reduce((sum, c) => sum + c.count, 0)
  const totalNg = list.reduce((sum, c) => sum + c.defectCount, 0)
  const avgCategoryRate = list.length
    ? Math.round(list.reduce((sum, c) => sum + c.avgRate, 0) / list.length)
    : 0
  return {
    categoryCount: list.length,
    equipmentRowCount: equipmentReportRows.value.length,
    totalUnits,
    totalNg,
    avgCategoryRate,
  }
})

function printCategoryStatusClass(status: string) {
  if (status === '경고') return 'equipment-print-status--warn'
  if (status === '이상') return 'equipment-print-status--bad'
  return 'equipment-print-status--ok'
}

function printEquipmentReport() {
  reportGeneratedAt.value = new Date().toLocaleString('ko-KR', {
    dateStyle: 'long',
    timeStyle: 'short',
  })
  isEquipmentPopupOpen.value = false
  requestAnimationFrame(() => {
    window.print()
  })
}

const specificMetricPercent = (metric: EquipmentSpecificMetric) => {
  const value = metricNumber(metric.value)
  const label = metric.label

  if (label.includes('압력')) return clampPercent((value / 20) * 100)
  if (label.includes('용탕')) return clampPercent((value / 700) * 100)
  if (label.includes('금형')) return clampPercent((value / 250) * 100)
  if (label.includes('스핀들')) return clampPercent((value / 8000) * 100)
  if (label.includes('절삭')) return clampPercent((value / 220) * 100)
  if (label.includes('공구')) return clampPercent((value / 80) * 100)
  if (label.includes('진동')) return clampPercent((value / 3) * 100)
  if (label.includes('세척수')) return clampPercent((value / 80) * 100)
  if (label.includes('세척입력')) return clampPercent((value / 5) * 100)
  if (label.includes('세척농도')) return clampPercent((value / 5) * 100)
  if (label.includes('건조')) return clampPercent((value / 100) * 100)
  if (label.includes('토크')) return clampPercent((value / 60) * 100)
  if (label.includes('각도')) return clampPercent((value / 180) * 100)
  if (label.includes('하중')) return clampPercent((value / 8) * 100)
  if (label.includes('치수')) return clampPercent((value / 30) * 100)

  return clampPercent(value)
}

function ensureChart(name: 'raw' | 'fft' | 'trend', el: HTMLDivElement | null) {
  if (!el) return null
  if (!chartInstances[name] || chartInstances[name]?.isDisposed?.()) {
    chartInstances[name] = echarts.init(el)
  }
  return chartInstances[name] ?? null
}

function disposeEquipmentCharts() {
  Object.values(chartInstances).forEach((chart) => chart?.dispose())
  delete chartInstances.raw
  delete chartInstances.fft
  delete chartInstances.trend
}

function downsampleIndexed(values: number[], maxPoints = 3600) {
  if (!values.length) return []
  if (values.length <= maxPoints) {
    return values.map((value, index) => ({ index, value: Number(Number(value || 0).toFixed(5)) }))
  }

  const bucketSize = Math.max(1, Math.ceil(values.length / Math.max(1, Math.floor(maxPoints / 2))))
  const points: Array<{ index: number; value: number }> = []

  for (let start = 0; start < values.length; start += bucketSize) {
    const end = Math.min(values.length, start + bucketSize)
    let minIndex = start
    let maxIndex = start
    let minValue = Number(values[start] || 0)
    let maxValue = minValue

    for (let index = start + 1; index < end; index += 1) {
      const value = Number(values[index] || 0)
      if (value < minValue) {
        minValue = value
        minIndex = index
      }
      if (value > maxValue) {
        maxValue = value
        maxIndex = index
      }
    }

    if (minIndex <= maxIndex) {
      points.push({ index: minIndex, value: Number(minValue.toFixed(5)) })
      if (minIndex !== maxIndex) points.push({ index: maxIndex, value: Number(maxValue.toFixed(5)) })
    } else {
      points.push({ index: maxIndex, value: Number(maxValue.toFixed(5)) })
      points.push({ index: minIndex, value: Number(minValue.toFixed(5)) })
    }
  }

  return points
}

function rawVibrationSeries() {
  const values = vibrationValues.value
  const window = realtimeVibration.value?.window
  const samplingRate = window?.samplingRate || realtimeAnalysis.value?.samplingRate || 16000
  const end = Date.parse(window?.timestamp ?? realtimeAnalysis.value?.timestamp ?? realtimeVibration.value?.receivedAt ?? '')
  const endMs = Number.isFinite(end) ? end : Date.now()
  const start = endMs - (values.length / samplingRate) * 1000
  return downsampleIndexed(values).map((point) => [
    start + (point.index / samplingRate) * 1000,
    point.value,
  ])
}

function fftSeries() {
  const fft = realtimeAnalysis.value?.fft
  const frequencies = fft?.frequencies ?? []
  const magnitudes = fft?.magnitudes ?? []
  const length = Math.min(frequencies.length, magnitudes.length)
  if (!length) return []
  const step = Math.max(1, Math.ceil(length / 1200))
  const points: number[][] = []
  for (let i = 0; i < length; i += step) {
    points.push([Number(frequencies[i].toFixed(2)), Number((magnitudes[i] ?? 0).toFixed(6))])
  }
  return points
}

function recordTrendPoint() {
  const analysis = realtimeAnalysis.value
  const features = analysis?.features
  if (!features) return
  const windowIndex = realtimeVibration.value?.window?.windowIndex ?? analysis?.windowIndex ?? Date.now()
  const timestamp = Date.parse(realtimeVibration.value?.receivedAt ?? analysis?.timestamp ?? '') || Date.now()
  const last = vibrationTrendHistory.value.at(-1)
  if (last?.windowIndex === windowIndex) return

  vibrationTrendHistory.value = [
    ...vibrationTrendHistory.value,
    {
      windowIndex,
      timestamp,
      rms: features.rms ?? 0,
      peakToPeak: features.peakToPeak ?? 0,
      anomalyScore: analysis?.anomalyScore ?? 0,
    },
  ].slice(-90)
}

function queueChartRender() {
  if (!isEquipmentPopupOpen.value || chartRenderPending) return
  chartRenderPending = true
  window.requestAnimationFrame(() => {
    chartRenderPending = false
    nextTick(renderEquipmentCharts)
  })
}

function renderEquipmentCharts() {
  renderRawVibrationChart()
  renderFftChart()
  renderTrendChart()
}

function renderRawVibrationChart() {
  const chart = ensureChart('raw', rawVibrationChartEl.value)
  if (!chart) return
  const data = rawVibrationSeries()
  const startX = data[0]?.[0]
  const endX = data.at(-1)?.[0]
  const span = startX && endX ? endX - startX : 0

  chart.setOption({
    animation: false,
    tooltip: { trigger: 'axis' },
    grid: { left: 54, right: 18, top: 34, bottom: 56 },
    toolbox: {
      right: 8,
      top: 2,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: data.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text: '실시간 진동 데이터 수신 대기', fill: '#64748b', fontWeight: 800 },
    }],
    xAxis: {
      type: 'time',
      axisLabel: { formatter: (value: number) => echarts.time.format(value, '{HH}:{mm}:{ss}', false) },
    },
    yAxis: {
      type: 'value',
      name: '진동 진폭 (a.u.)',
      min: 'dataMin',
      max: 'dataMax',
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0 },
      { type: 'slider', xAxisIndex: 0, height: 24, bottom: 16 },
    ],
    series: [{
      name: '측정값',
      type: 'line',
      symbol: 'none',
      sampling: 'lttb',
      lineStyle: { width: 1, color: '#0f5e8c' },
      areaStyle: { color: 'rgba(23, 121, 178, 0.08)' },
      data,
      markArea: span ? {
        silent: true,
        itemStyle: { color: 'rgba(34, 197, 94, 0.09)' },
        label: { color: '#334155', fontWeight: 800 },
        data: [
          [{ name: '회복 확인 구간', xAxis: startX + span * 0.30 }, { xAxis: startX + span * 0.44 }],
          [{ name: '회복 확인 구간', xAxis: startX + span * 0.62 }, { xAxis: startX + span * 0.78 }],
        ],
      } : undefined,
    }],
  }, true)
}

function renderFftChart() {
  const chart = ensureChart('fft', fftChartEl.value)
  if (!chart) return
  const data = fftSeries()
  chart.setOption({
    animation: false,
    tooltip: { trigger: 'axis' },
    grid: { left: 54, right: 18, top: 34, bottom: 48 },
    toolbox: {
      right: 8,
      top: 2,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: data.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text: 'FFT 데이터 수신 대기', fill: '#64748b', fontWeight: 800 },
    }],
    xAxis: {
      type: 'value',
      name: '주파수 (Hz)',
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    yAxis: {
      type: 'value',
      name: 'FFT 크기',
      min: 0,
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0 },
      { type: 'slider', xAxisIndex: 0, height: 20, bottom: 12 },
    ],
    series: [{
      name: 'FFT',
      type: 'line',
      symbol: 'none',
      lineStyle: { width: 1.2, color: '#645bff' },
      areaStyle: { color: 'rgba(100, 91, 255, 0.08)' },
      data,
    }],
  }, true)
}

function renderTrendChart() {
  const chart = ensureChart('trend', trendChartEl.value)
  if (!chart) return
  const rows = vibrationTrendHistory.value
  const anomalyPoints: [number, number][] = rows.map((row) => [row.timestamp, row.anomalyScore])
  const maxAnomaly = anomalyPoints.reduce(
    (max, point) => point[1] > max[1] ? point : max,
    [null, -Infinity] as [number | null, number],
  )

  chart.setOption({
    animation: false,
    legend: { top: 2, right: 44, itemWidth: 14, textStyle: { color: '#334155', fontWeight: 700 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 18, top: 42, bottom: 46 },
    toolbox: {
      right: 8,
      top: 2,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: rows.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text: '구간 특징값 수집 중', fill: '#64748b', fontWeight: 800 },
    }],
    xAxis: {
      type: 'time',
      axisLabel: { formatter: (value: number) => echarts.time.format(value, '{HH}:{mm}:{ss}', false) },
    },
    yAxis: {
      type: 'value',
      name: '특징값',
      min: 0,
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
    series: [
      {
        name: 'RMS',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#4f6df5' },
        itemStyle: { color: '#4f6df5' },
        data: rows.map((row) => [row.timestamp, Number(row.rms.toFixed(4))]),
      },
      {
        name: 'Peak-to-Peak',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#f59e0b' },
        itemStyle: { color: '#f59e0b' },
        data: rows.map((row) => [row.timestamp, Number(row.peakToPeak.toFixed(4))]),
      },
      {
        name: '이상 점수',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#ef4444' },
        itemStyle: { color: '#ef4444' },
        data: anomalyPoints,
        markPoint: maxAnomaly[0] ? {
          symbol: 'pin',
          symbolSize: 56,
          label: { formatter: '최고 위험', color: '#ffffff', fontSize: 10, fontWeight: 900 },
          data: [{ coord: maxAnomaly, value: fixedMetric(maxAnomaly[1], 3) }],
        } : undefined,
      },
    ],
  }, true)
}

watch(
  () => selectedEquipment.value.id,
  () => {
    vibrationTrendHistory.value = []
    disposeEquipmentCharts()
    if (isEquipmentPopupOpen.value) queueChartRender()
  },
)

watch(
  () => realtimeVibration.value?.window?.windowIndex,
  () => {
    recordTrendPoint()
    queueChartRender()
  },
)

watch(isEquipmentPopupOpen, (open) => {
  if (open) queueChartRender()
  else disposeEquipmentCharts()
})

onUnmounted(disposeEquipmentCharts)
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar no-print" aria-label="주요 메뉴">
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
        <p>설비 카테고리별 주요 데이터와 특정 설비 상세 정보 확인</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <div class="no-print">
        <header class="dashboard-header">
          <div class="dashboard-header-titles">
            <p class="dashboard-kicker">Equipment Monitoring</p>
            <h1>설비별 화면</h1>
          </div>
          <div class="header-actions">
            <span class="current-time">
              <CalendarDays :size="16" />
              2026-05-11 12:40
            </span>
            <button type="button" class="ghost-button equipment-print-trigger" @click="printEquipmentReport">
              <Printer :size="16" />
              <span>종합 레포트 인쇄</span>
            </button>
            <RouterLink class="ghost-button" :to="{ name: 'layout' }">
              <MapPinned :size="16" />
              <span>라인별 현황</span>
            </RouterLink>
            <button type="button" class="icon-link" @click="logout">
              <LogOut :size="16" />
              <span>로그아웃</span>
            </button>
          </div>
        </header>

        <section class="dashboard-panel equipment-category-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Equipment Category</p>
            <h2>설비 카테고리 선택</h2>
          </div>
          <Factory :size="22" />
        </div>

        <EquipmentCategoryGrid
          :categories="categories"
          :selected-category-id="selectedCategoryId"
          @select="selectCategory"
        />
      </section>

      <section class="equipment-monitor-grid">
        <CategorySummaryPanel :category="selectedCategory" />
        <CategoryEquipmentList
          :category="selectedCategory"
          :selected-equipment-id="selectedEquipmentId"
          @select="(id) => (selectedEquipmentId = id)"
        />
      </section>

      <section class="dashboard-panel selected-equipment-panel">
        <div class="selected-equipment-head">
          <div>
            <p class="panel-kicker">Selected Equipment Detail</p>
            <h2>{{ selectedEquipment.id }} · {{ selectedEquipment.name }}</h2>
          </div>
          <div class="equipment-detail-actions">
            <button
              class="equipment-detail-open-button"
              type="button"
              :disabled="!webScadaReady"
              title="SMWP 설비 화면 (#ED) 팝업"
              @click="openEquipmentPopup"
            >
              <Factory :size="16" />
              <span>설비 상세보기</span>
            </button>
            <span :class="['state-badge', selectedEquipment.state]">{{ selectedEquipment.state }}</span>
          </div>
        </div>

        <div class="selected-equipment-summary">
          <article>
            <span>라인</span>
            <strong>{{ selectedEquipment.line }}</strong>
          </article>
          <article>
            <span>가동률</span>
            <strong>{{ selectedEquipment.rate }}%</strong>
          </article>
          <article>
            <span>불량수량</span>
            <strong>{{ selectedEquipment.defects }}</strong>
          </article>
          <article>
            <span>싸이클 타임</span>
            <strong>{{ selectedEquipment.cycle }}</strong>
          </article>
        </div>

        <div class="equipment-detail-grid">
          <article class="equipment-detail-card">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Common Data</p>
                <h2>공통 운전 정보</h2>
              </div>
              <Activity :size="22" />
            </div>

            <div class="common-metric-grid compact">
              <article v-for="metric in selectedEquipment.common" :key="metric.label">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.value }}</strong>
              </article>
            </div>
          </article>

          <article class="equipment-detail-card">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Category Specific Data</p>
                <h2>{{ selectedCategory.name }} 상세 데이터</h2>
              </div>
              <AlertTriangle :size="22" />
            </div>

            <div class="type-metric-grid category-specific-grid">
              <article v-for="metric in selectedEquipment.specific" :key="metric.label">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.value }}</strong>
                <p>{{ metric.status }}</p>
              </article>
            </div>
          </article>
        </div>
        </section>

        <Teleport to="body">
          <div
            v-if="selectedEquipment && isEquipmentPopupOpen"
            class="equipment-modal-backdrop"
            @click.self="closeEquipmentPopup"
          >
        <article
          class="equipment-detail-modal"
          role="dialog"
          aria-modal="true"
          :aria-label="`${selectedEquipment.name} 상세보기`"
        >
          <div class="equipment-popup-head">
            <div>
              <p class="panel-kicker">Equipment Detail</p>
              <h2>{{ selectedEquipment.id }} · {{ selectedEquipment.name }}</h2>
              <p>{{ selectedEquipment.line }} · {{ selectedCategory.name }} · 작업자 {{ selectedEquipment.operator }}</p>
            </div>
            <div class="equipment-popup-head-actions">
              <span :class="['state-badge', selectedEquipment.state]">{{ selectedEquipment.state }}</span>
              <button class="equipment-modal-close" type="button" aria-label="팝업 닫기" @click="closeEquipmentPopup">
                <X :size="18" />
              </button>
            </div>
          </div>

          <div class="equipment-popup-summary">
            <article v-for="metric in popupSummaryItems" :key="`popup-summary-${metric.label}`">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </article>
          </div>

          <section class="equipment-popup-command-panel">
            <div>
              <p class="panel-kicker">Control Command</p>
              <h3>제어명령</h3>
            </div>
            <div class="equipment-popup-command-row">
              <button :class="{ active: selectedEquipment.state === '정지' }" type="button">
                <Square :size="16" />
                <span>정지</span>
              </button>
              <button :class="{ active: selectedEquipment.state !== '정지' }" type="button">
                <Play :size="16" />
                <span>운전</span>
              </button>
            </div>
          </section>

          <div class="equipment-popup-data-grid">
            <section class="equipment-popup-section">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Common Data</p>
                  <h3>공통 상세내역</h3>
                </div>
                <Activity :size="22" />
              </div>
              <div class="equipment-popup-metric-grid">
                <article v-for="metric in selectedEquipment.common" :key="`popup-common-${metric.label}`">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                </article>
              </div>
            </section>

            <section class="equipment-popup-section">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Type Data</p>
                  <h3>{{ selectedCategory.name }} 상세내역</h3>
                </div>
                <AlertTriangle :size="22" />
              </div>
              <div class="equipment-popup-specific-grid">
                <article v-for="metric in selectedEquipment.specific" :key="`popup-specific-${metric.label}`">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                  <p>{{ metric.status }}</p>
                </article>
              </div>
            </section>
          </div>

          <section class="equipment-popup-section equipment-popup-graph-section">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Equipment Graph</p>
                <h3>설비 그래프</h3>
              </div>
              <Gauge :size="22" />
            </div>

            <div class="equipment-popup-graph-grid">
              <article class="equipment-popup-chart-card equipment-vibration-raw-card">
                <div class="equipment-chart-head">
                  <div>
                    <strong>진동 진폭 (a.u.)</strong>
                    <span>회복 확인 구간</span>
                  </div>
                  <small>{{ vibrationSampleCount }} samples</small>
                </div>
                <div ref="rawVibrationChartEl" class="equipment-echart equipment-echart--raw"></div>
              </article>

              <aside class="equipment-popup-chart-card equipment-vibration-metric-card">
                <article v-for="metric in vibrationAnalysisCards" :key="`vibration-${metric.label}`">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                </article>
              </aside>

              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <div>
                    <strong>선택 구간 FFT</strong>
                    <span>FFT 크기</span>
                  </div>
                  <small>{{ realtimeAnalysis?.fft?.binCount ?? realtimeAnalysis?.fft?.frequencies?.length ?? 0 }} bin</small>
                </div>
                <div ref="fftChartEl" class="equipment-echart equipment-echart--small"></div>
              </article>

              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <div>
                    <strong>{{ selectedEquipment.id }} 구간 특징값 흐름</strong>
                    <span>최근 {{ vibrationTrendHistory.length }} window</span>
                  </div>
                  <small>{{ vibrationTimeRange }}</small>
                </div>
                <div ref="trendChartEl" class="equipment-echart equipment-echart--small"></div>
              </article>
            </div>
          </section>
        </article>
          </div>
        </Teleport>
      </div>

      <Teleport to="body">
        <div class="equipment-report-portal">
      <div class="equipment-print-only equipment-print-report" role="document" aria-label="설비 종합 모니터링 레포트">
        <div class="equipment-print-sheet">
          <header class="equipment-print-cover">
            <div class="equipment-print-cover-top">
              <div class="equipment-print-brand">
                <span class="equipment-print-brand-mark">UECADA</span>
                <span class="equipment-print-brand-line" aria-hidden="true" />
                <span class="equipment-print-brand-sub">설비 모니터링 · PHM</span>
              </div>
              <dl class="equipment-print-doc-meta">
                <div>
                  <dt>발행</dt>
                  <dd>{{ reportGeneratedAt }}</dd>
                </div>
                <div>
                  <dt>문서</dt>
                  <dd>EQUIP-SUM-01</dd>
                </div>
              </dl>
            </div>
            <h1 class="equipment-print-doc-title">설비 종합 모니터링 레포트</h1>
            <p class="equipment-print-doc-lead">
              카테고리 단위 요약, 전 설비 가동·품질 스냅샷, 화면에서 선택한 설비의 상세 항목을 한 장에 정리합니다.
            </p>
          </header>

          <section class="equipment-print-kpi-strip" aria-label="요약 지표">
            <article class="equipment-print-kpi">
              <span class="equipment-print-kpi-label">관리 카테고리</span>
              <strong class="equipment-print-kpi-value">{{ printReportSummary.categoryCount }}</strong>
              <span class="equipment-print-kpi-unit">개 구역</span>
            </article>
            <article class="equipment-print-kpi">
              <span class="equipment-print-kpi-label">등록 설비</span>
              <strong class="equipment-print-kpi-value">{{ printReportSummary.totalUnits }}</strong>
              <span class="equipment-print-kpi-unit">대 (표시 {{ printReportSummary.equipmentRowCount }}행)</span>
            </article>
            <article class="equipment-print-kpi">
              <span class="equipment-print-kpi-label">카테고리 평균 가동률</span>
              <strong class="equipment-print-kpi-value">{{ printReportSummary.avgCategoryRate }}<span class="pct">%</span></strong>
              <span class="equipment-print-kpi-unit">산술 평균</span>
            </article>
            <article class="equipment-print-kpi">
              <span class="equipment-print-kpi-label">금일 누적 NG</span>
              <strong class="equipment-print-kpi-value">{{ printReportSummary.totalNg }}</strong>
              <span class="equipment-print-kpi-unit">건 · 전 카테고리 합</span>
            </article>
          </section>

          <section class="equipment-print-block">
            <header class="equipment-print-block-head">
              <span class="equipment-print-step" aria-hidden="true">01</span>
              <div class="equipment-print-block-titles">
                <h2>카테고리별 요약</h2>
                <p>대수·상태·운전·정지·대기·평균 가동률·불량 누적을 구역별로 정리합니다.</p>
              </div>
            </header>
            <div class="equipment-print-table-shell">
              <table class="equipment-print-table">
                <thead>
                  <tr>
                    <th scope="col">카테고리</th>
                    <th scope="col" class="num">대수</th>
                    <th scope="col">상태</th>
                    <th scope="col" class="num">운전</th>
                    <th scope="col" class="num">정지</th>
                    <th scope="col" class="num">대기</th>
                    <th scope="col" class="num">평균 가동률</th>
                    <th scope="col" class="num">불량(NG)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="cat in categories" :key="`print-cat-${cat.id}`">
                    <td class="equipment-print-td-strong">{{ cat.name }}</td>
                    <td class="num">{{ cat.count }}</td>
                    <td :class="['equipment-print-status', printCategoryStatusClass(cat.status)]">{{ cat.status }}</td>
                    <td class="num">{{ cat.running }}</td>
                    <td class="num">{{ cat.stopped }}</td>
                    <td class="num">{{ cat.waiting }}</td>
                    <td class="num">{{ cat.avgRate }}%</td>
                    <td class="num">{{ cat.defectCount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="equipment-print-block">
            <header class="equipment-print-block-head">
              <span class="equipment-print-step" aria-hidden="true">02</span>
              <div class="equipment-print-block-titles">
                <h2>설비별 현황</h2>
                <p>표에 포함된 모든 설비의 식별·라인·운전·가동률·불량·싸이클·작업자 코드입니다.</p>
              </div>
            </header>
            <div class="equipment-print-table-shell">
              <table class="equipment-print-table equipment-print-table--dense">
                <colgroup>
                  <col class="col-cat" />
                  <col class="col-id" />
                  <col class="col-name" />
                  <col class="col-line" />
                  <col class="col-state" />
                  <col class="col-num" />
                  <col class="col-num" />
                  <col class="col-cycle" />
                  <col class="col-op" />
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col">카테고리</th>
                    <th scope="col">설비 ID</th>
                    <th scope="col">설비명</th>
                    <th scope="col">라인</th>
                    <th scope="col">운전</th>
                    <th scope="col" class="num">가동률</th>
                    <th scope="col" class="num">불량</th>
                    <th scope="col" class="nowrap">싸이클</th>
                    <th scope="col" class="nowrap">작업자</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in equipmentReportRows" :key="`print-eq-${row.id}`">
                    <td>{{ row.categoryName }}</td>
                    <td class="mono">{{ row.id }}</td>
                    <td class="equipment-print-td-strong">{{ row.name }}</td>
                    <td>{{ row.line }}</td>
                    <td>{{ row.state }}</td>
                    <td class="num">{{ row.rate }}%</td>
                    <td class="num">{{ row.defects }}</td>
                    <td class="nowrap mono">{{ row.cycle }}</td>
                    <td class="nowrap mono">{{ row.operator }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="equipment-print-block equipment-print-block--detail">
            <header class="equipment-print-block-head">
              <span class="equipment-print-step" aria-hidden="true">03</span>
              <div class="equipment-print-block-titles">
                <h2>선택 설비 상세</h2>
                <p>인쇄 시점 화면에서 선택된 설비 기준입니다. 카테고리·라인·상태·핵심 KPI를 먼저 확인한 뒤 항목별 값을 참고하세요.</p>
              </div>
            </header>

            <div class="equipment-print-focus-card">
              <div class="equipment-print-focus-main">
                <span class="equipment-print-focus-id mono">{{ selectedEquipment.id }}</span>
                <span class="equipment-print-focus-name">{{ selectedEquipment.name }}</span>
              </div>
              <div class="equipment-print-focus-meta">
                <span>{{ selectedEquipment.line }}</span>
                <span class="equipment-print-focus-dot" aria-hidden="true">·</span>
                <span>{{ selectedCategory.name }}</span>
                <span class="equipment-print-focus-dot" aria-hidden="true">·</span>
                <span class="equipment-print-focus-state">{{ selectedEquipment.state }}</span>
              </div>
              <ul class="equipment-print-focus-kpis" aria-label="선택 설비 핵심 지표">
                <li><span>가동률</span><strong>{{ selectedEquipment.rate }}%</strong></li>
                <li><span>불량</span><strong>{{ selectedEquipment.defects }}</strong></li>
                <li><span>싸이클</span><strong class="mono">{{ selectedEquipment.cycle }}</strong></li>
                <li><span>작업자</span><strong class="mono">{{ selectedEquipment.operator }}</strong></li>
              </ul>
            </div>

            <div class="equipment-print-detail-grid">
              <div class="equipment-print-detail-panel">
                <h3 class="equipment-print-panel-title">공통 운전 정보</h3>
                <div class="equipment-print-table-shell">
                  <table class="equipment-print-table equipment-print-table--kv">
                    <tbody>
                      <tr v-for="metric in selectedEquipment.common" :key="`print-cm-${metric.label}`">
                        <th scope="row">{{ metric.label }}</th>
                        <td class="mono">{{ metric.value }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <div class="equipment-print-detail-panel">
                <h3 class="equipment-print-panel-title">{{ selectedCategory.name }} 상세</h3>
                <div class="equipment-print-table-shell">
                  <table class="equipment-print-table">
                    <thead>
                      <tr>
                        <th scope="col">항목</th>
                        <th scope="col" class="nowrap">값</th>
                        <th scope="col">판정</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="metric in selectedEquipment.specific" :key="`print-sp-${metric.label}`">
                        <td>{{ metric.label }}</td>
                        <td class="nowrap mono equipment-print-td-value">{{ metric.value }}</td>
                        <td>{{ metric.status }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>

          <footer class="equipment-print-footer">
            <p>
              본 문서는 조회 화면의 샘플 데이터를 기준으로 생성된 스냅샷이며, 실제 MES·SCADA 연동 시 API 응답 시각과 항목이 달라질 수 있습니다.
            </p>
          </footer>
        </div>
      </div>
        </div>
      </Teleport>
    </section>
    <WebScadaOverlay
      :open="webScadaOverlayOpen"
      :page-id="webScadaOverlayPageId"
      :title="webScadaOverlayTitle"
      subtitle="Equipment Detail · SMWP"
      @close="webScadaOverlayOpen = false"
    />
  </main>
</template>

<style scoped>
.equipment-print-only {
  display: none;
}

.equipment-print-trigger {
  white-space: nowrap;
}

@media print {
  @page {
    margin: 11mm 12mm 14mm;
    size: A4 portrait;
  }

  .equipment-print-only {
    display: block !important;
  }

  .no-print {
    display: none !important;
  }

  .equipment-print-report {
    font-family: 'Malgun Gothic', 'Apple SD Gothic Neo', 'Segoe UI', sans-serif;
    color: #0f172a;
    font-size: 9.25pt;
    line-height: 1.48;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .equipment-print-sheet {
    max-width: 100%;
  }

  /* 표지형 헤더 */
  .equipment-print-cover {
    margin-bottom: 14pt;
    padding-bottom: 12pt;
    border-bottom: 1.25pt solid #0f1f38;
  }

  .equipment-print-cover-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12pt;
    margin-bottom: 10pt;
  }

  .equipment-print-brand {
    display: flex;
    align-items: center;
    gap: 8pt;
    flex-wrap: wrap;
  }

  .equipment-print-brand-mark {
    font-size: 11pt;
    font-weight: 900;
    letter-spacing: 0.12em;
    color: #002c5f;
  }

  .equipment-print-brand-line {
    width: 1pt;
    height: 11pt;
    background: #cbd5e1;
  }

  .equipment-print-brand-sub {
    font-size: 8.25pt;
    font-weight: 700;
    color: #64748b;
    letter-spacing: 0.04em;
  }

  .equipment-print-doc-meta {
    display: flex;
    gap: 14pt;
    margin: 0;
    font-size: 8.25pt;
    color: #475569;
  }

  .equipment-print-doc-meta div {
    display: grid;
    gap: 1pt;
    text-align: right;
  }

  .equipment-print-doc-meta dt {
    margin: 0;
    font-size: 7.25pt;
    font-weight: 800;
    color: #94a3b8;
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  .equipment-print-doc-meta dd {
    margin: 0;
    font-weight: 700;
    color: #334155;
    font-variant-numeric: tabular-nums;
  }

  .equipment-print-doc-title {
    margin: 0 0 5pt;
    font-size: 17pt;
    font-weight: 900;
    letter-spacing: -0.03em;
    color: #0f1f38;
    line-height: 1.2;
  }

  .equipment-print-doc-lead {
    margin: 0;
    max-width: 52em;
    font-size: 8.75pt;
    color: #475569;
    line-height: 1.55;
  }

  /* 상단 KPI 띠 */
  .equipment-print-kpi-strip {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8pt;
    margin-bottom: 16pt;
  }

  .equipment-print-kpi {
    border: 0.5pt solid #e2e8f0;
    border-radius: 4pt;
    padding: 7pt 8pt 8pt;
    background: linear-gradient(180deg, #fafbfc 0%, #f4f7f9 100%);
    break-inside: avoid;
  }

  .equipment-print-kpi-label {
    display: block;
    font-size: 7.25pt;
    font-weight: 800;
    color: #64748b;
    letter-spacing: 0.02em;
    margin-bottom: 3pt;
  }

  .equipment-print-kpi-value {
    display: block;
    font-size: 14pt;
    font-weight: 900;
    letter-spacing: -0.03em;
    color: #0f1f38;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
  }

  .equipment-print-kpi-value .pct {
    font-size: 0.72em;
    font-weight: 800;
    margin-left: 1px;
  }

  .equipment-print-kpi-unit {
    display: block;
    margin-top: 3pt;
    font-size: 7pt;
    font-weight: 600;
    color: #94a3b8;
  }

  /* 섹션 공통 */
  .equipment-print-block {
    margin-bottom: 15pt;
    break-inside: avoid;
  }

  .equipment-print-block--detail {
    break-inside: auto;
  }

  .equipment-print-block-head {
    display: flex;
    gap: 10pt;
    align-items: flex-start;
    margin-bottom: 8pt;
    padding-bottom: 6pt;
    border-bottom: 0.5pt solid #e2e8f0;
  }

  .equipment-print-step {
    flex-shrink: 0;
    width: 22pt;
    height: 22pt;
    display: grid;
    place-items: center;
    font-size: 8pt;
    font-weight: 900;
    color: #fff;
    background: #002c5f;
    border-radius: 4pt;
    letter-spacing: 0.02em;
  }

  .equipment-print-block-titles h2 {
    margin: 0 0 3pt;
    font-size: 11pt;
    font-weight: 900;
    color: #0f1f38;
    letter-spacing: -0.02em;
  }

  .equipment-print-block-titles p {
    margin: 0;
    font-size: 8pt;
    font-weight: 600;
    color: #64748b;
    line-height: 1.45;
    max-width: 48em;
  }

  .equipment-print-table-shell {
    border: 0.5pt solid #cbd5e1;
    border-radius: 3pt;
    overflow: hidden;
  }

  .equipment-print-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 8.5pt;
    font-variant-numeric: tabular-nums;
  }

  .equipment-print-table thead {
    display: table-header-group;
  }

  .equipment-print-table th,
  .equipment-print-table td {
    border-bottom: 0.5pt solid #e2e8f0;
    padding: 4.5pt 6pt;
    text-align: left;
    vertical-align: middle;
  }

  .equipment-print-table tr:last-child td,
  .equipment-print-table tr:last-child th {
    border-bottom: none;
  }

  .equipment-print-table thead th {
    background: #f1f5f9;
    font-weight: 800;
    font-size: 7.75pt;
    color: #334155;
    letter-spacing: 0.02em;
  }

  .equipment-print-table tbody tr:nth-child(even) td,
  .equipment-print-table tbody tr:nth-child(even) th {
    background: #fafbfc;
  }

  .equipment-print-table .num {
    text-align: right;
    white-space: nowrap;
  }

  .equipment-print-table .nowrap {
    white-space: nowrap;
  }

  .equipment-print-table .mono {
    font-family: ui-monospace, 'Cascadia Mono', 'Consolas', monospace;
    font-size: 0.96em;
  }

  .equipment-print-td-strong {
    font-weight: 800;
    color: #0f1f38;
  }

  .equipment-print-td-value {
    text-align: right;
    font-weight: 700;
  }

  .equipment-print-table--dense {
    font-size: 7.85pt;
  }

  .equipment-print-table--dense th,
  .equipment-print-table--dense td {
    padding: 3.5pt 4pt;
  }

  .equipment-print-table--dense .col-cat {
    width: 11%;
  }

  .equipment-print-table--dense .col-id {
    width: 9%;
  }

  .equipment-print-table--dense .col-name {
    width: 14%;
  }

  .equipment-print-table--dense .col-line {
    width: 18%;
  }

  .equipment-print-table--dense .col-state {
    width: 8%;
  }

  .equipment-print-table--dense .col-num {
    width: 7%;
  }

  .equipment-print-table--dense .col-cycle {
    width: 10%;
  }

  .equipment-print-table--dense .col-op {
    width: 9%;
  }

  .equipment-print-table--kv tbody th {
    width: 38%;
    font-weight: 700;
    color: #475569;
    background: #f8fafc;
    font-size: 8pt;
  }

  .equipment-print-table--kv tbody td {
    font-size: 8.25pt;
  }

  .equipment-print-status {
    font-weight: 800;
  }

  .equipment-print-status--ok {
    color: #0f766e;
  }

  .equipment-print-status--warn {
    color: #b45309;
  }

  .equipment-print-status--bad {
    color: #b91c1c;
  }

  /* 선택 설비 카드 */
  .equipment-print-focus-card {
    border: 0.5pt solid #cbd5e1;
    border-radius: 4pt;
    padding: 9pt 10pt 8pt;
    margin-bottom: 10pt;
    background: #fff;
    box-shadow: 0 1pt 0 rgba(15, 23, 42, 0.04);
    break-inside: avoid;
  }

  .equipment-print-focus-main {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6pt 10pt;
    margin-bottom: 4pt;
  }

  .equipment-print-focus-id {
    font-size: 11pt;
    font-weight: 900;
    color: #002c5f;
    letter-spacing: 0.02em;
  }

  .equipment-print-focus-name {
    font-size: 10.5pt;
    font-weight: 900;
    color: #0f1f38;
  }

  .equipment-print-focus-meta {
    font-size: 8.25pt;
    font-weight: 600;
    color: #64748b;
    margin-bottom: 8pt;
  }

  .equipment-print-focus-dot {
    margin: 0 3pt;
    color: #cbd5e1;
  }

  .equipment-print-focus-state {
    font-weight: 800;
    color: #0f1f38;
  }

  .equipment-print-focus-kpis {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 6pt 10pt;
    margin: 0;
    padding: 8pt 0 0;
    border-top: 0.5pt solid #e2e8f0;
    list-style: none;
  }

  .equipment-print-focus-kpis li {
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 2pt;
  }

  .equipment-print-focus-kpis span {
    font-size: 7.25pt;
    font-weight: 800;
    color: #94a3b8;
    letter-spacing: 0.02em;
  }

  .equipment-print-focus-kpis strong {
    font-size: 10pt;
    font-weight: 900;
    color: #0f1f38;
    font-variant-numeric: tabular-nums;
  }

  .equipment-print-detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10pt;
    align-items: start;
  }

  .equipment-print-panel-title {
    margin: 0 0 6pt;
    font-size: 9pt;
    font-weight: 900;
    color: #334155;
    letter-spacing: -0.01em;
  }

  .equipment-print-footer {
    margin-top: 12pt;
    padding-top: 8pt;
    border-top: 0.5pt dashed #cbd5e1;
    break-inside: avoid;
  }

  .equipment-print-footer p {
    margin: 0;
    font-size: 7.5pt;
    font-weight: 600;
    color: #94a3b8;
    line-height: 1.5;
    max-width: 58em;
  }

  .dashboard-main {
    padding: 0 !important;
    margin: 0 !important;
    background: #fff !important;
    max-width: none !important;
  }

  .dashboard-shell {
    display: block !important;
    min-height: 0 !important;
    background: #fff !important;
  }
}
</style>
