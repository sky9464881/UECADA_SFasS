<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  CalendarDays,
  Cog,
  Droplets,
  Factory,
  Flame,
  LayoutDashboard,
  LogOut,
  Search,
  Wrench,
  X,
} from 'lucide-vue-next'
import type { Component } from 'vue'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { realtimeBufferKey, useFactoryLayout, type FactoryRealtimeMetric } from '@/composables/useFactoryLayout'
import { useLineDetails, type LineProcessStage } from '@/composables/useLineDetails'
import { useQuery } from '@tanstack/vue-query'
import { fetchEquipmentAvailability } from '@/api/equipmentApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import { processRealtimeMetricConfigs } from '@/utils/realtimeBuffers'
import type { LineSummary, LineStatusCode } from '@/types/line'
import type { Equipment, EquipmentStatusItem, EquipmentStatusCode } from '@/types/equipment'

type StatusLabel = '정상' | '주의' | '경고'
type StatusKind = 'ok' | 'warn' | 'alert'
type StageKey = 'cast' | 'mach' | 'wash' | 'assy' | 'insp'
type LineTone = 'blue' | 'yellow' | 'neutral' | 'red'

interface EquipmentMetric {
  label: string
  value: string
}

interface EquipmentItem {
  id: string
  name: string
  status: StatusLabel
  summary: string
  metrics?: readonly EquipmentMetric[]
}

type EquipmentMatrix = Record<string, Record<StageKey, EquipmentItem[]>>

interface StageDef {
  key: StageKey
  label: string
  badge: string
  icon: Component
}

interface FactoryLine {
  id: string
  name: string
  code: string
  oee: number
  status: StatusLabel
  statusKind: StatusKind
  tone: LineTone
  sparkline: readonly number[]
}

const { navItems } = useAppNav()
const logout = useLogout()
const route = useRoute()
const { lines: lineDetails } = useLineDetails()

const STAGE_ORDER: readonly StageDef[] = [
  { key: 'cast', label: '주조', badge: 'CAST', icon: Flame },
  { key: 'mach', label: '가공', badge: 'MACHINE', icon: Cog },
  { key: 'wash', label: '세척', badge: 'WASH', icon: Droplets },
  { key: 'assy', label: '조립', badge: 'ASSEMBLE', icon: Wrench },
  { key: 'insp', label: '검사', badge: 'INSPECT', icon: Search },
] as const

const { lines: linesData, equipments: equipmentsData, statuses: statusesData, realtime: realtimeData } = useFactoryLayout()

const availabilityQuery = useQuery({
  queryKey: ['equipment-availability'],
  queryFn: () => fetchEquipmentAvailability(10),
  refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
  staleTime: 0,
  refetchIntervalInBackground: true,
})
const availabilityMap = computed(() => {
  const map = new Map<string, number>()
  for (const item of availabilityQuery.data.value ?? []) {
    map.set(item.equipmentCode, item.availabilityPct)
  }
  return map
})

const PROCESS_TYPE_TO_STAGE_KEY: Record<string, StageKey> = {
  주조: 'cast',
  가공: 'mach',
  세척: 'wash',
  조립: 'assy',
  검사: 'insp',
}

const LINE_TONE_BY_INDEX: readonly LineTone[] = ['blue', 'yellow', 'neutral']
const REALTIME_FRESH_MS = 5_000
const REALTIME_STALE_MS = 30_000
const COMMON_METRICS: readonly FactoryRealtimeMetric[] = [
  'cycle_time',
  'sensor_current',
  'sensor_voltage',
  'sensor_temperature',
  'sensor_vibration',
]

function lineStatusToLabel(status: LineStatusCode | undefined): StatusLabel {
  if (status === 'ALARM') return '경고'
  if (status === 'MAINTENANCE') return '주의'
  return '정상'
}

function lineStatusToKind(status: LineStatusCode | undefined): StatusKind {
  if (status === 'ALARM') return 'alert'
  if (status === 'MAINTENANCE') return 'warn'
  return 'ok'
}

function lineStatusToTone(status: LineStatusCode | undefined, fallback: LineTone): LineTone {
  if (status === 'ALARM') return 'red'
  if (status === 'MAINTENANCE') return 'yellow'
  return fallback
}

function equipmentStatusToLabel(status: EquipmentStatusCode | undefined): StatusLabel {
  if (status === 'ALARM') return '경고'
  if (status === 'MAINTENANCE') return '주의'
  return '정상'
}

function statusMapFromList(list: readonly EquipmentStatusItem[]): Map<string, EquipmentStatusCode> {
  const map = new Map<string, EquipmentStatusCode>()
  for (const item of list) {
    map.set(item.equipId, item.statusCode)
  }
  return map
}

function equipmentSummary(e: Equipment): string {
  const parts: string[] = []
  if (e.model) parts.push(e.model)
  if (e.installDate) parts.push(`설치 ${e.installDate}`)
  if (!parts.length) parts.push('상세 정보 없음')
  return parts.join(' · ')
}

function realtimeValue(equipmentCode: string, metric: FactoryRealtimeMetric): number | null {
  const key = realtimeBufferKey(equipmentCode, metric)
  const value = key ? realtimeData.value.get(key)?.value : null
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function realtimeTimestamp(equipmentCode: string, metric: FactoryRealtimeMetric): number | null {
  const key = realtimeBufferKey(equipmentCode, metric)
  const timestampMs = key ? realtimeData.value.get(key)?.timestampMs : null
  return typeof timestampMs === 'number' && Number.isFinite(timestampMs) ? timestampMs : null
}

function latestRealtimeTimestamp(e: Equipment): number | null {
  const metrics = [
    ...new Set([
      ...processRealtimeMetricConfigs(e.processType).map((config) => config.metric),
      ...COMMON_METRICS,
    ]),
  ]
  return metrics.reduce<number | null>((latest, metric) => {
    const timestampMs = realtimeTimestamp(e.equipmentCode, metric)
    return timestampMs != null && (latest == null || timestampMs > latest) ? timestampMs : latest
  }, null)
}

function realtimeReceiveLabel(e: Equipment): string {
  const timestampMs = latestRealtimeTimestamp(e)
  if (timestampMs == null) return '미수신'
  const ageMs = Date.now() - timestampMs
  if (ageMs <= REALTIME_FRESH_MS) return '실시간'
  if (ageMs <= REALTIME_STALE_MS) return '지연'
  return '오프라인'
}

function effectiveEquipmentStatus(e: Equipment, dbStatus: EquipmentStatusCode | undefined): StatusLabel {
  if (dbStatus === 'ALARM') return '경고'

  // 현재 센서값 기준: Type Data 전부 0 → 지금 꺼진 상태 (이력보다 우선)
  const processMetrics = processRealtimeMetricConfigs(e.processType)
  if (processMetrics.length >= 2) {
    const values = processMetrics.map((c) => realtimeValue(e.equipmentCode, c.metric))
    const hasData = values.some((v) => v !== null)
    const allZero = values.every((v) => v === null || v === 0)
    if (hasData && allZero) return '주의'
  }

  // 10분 가동률 버퍼 기반
  const availPct = availabilityMap.value.get(e.equipmentCode)
  if (availPct !== undefined) {
    return availPct > 0 ? '정상' : '주의'
  }

  // fallback: 타임스탬프 기반
  const timestampMs = latestRealtimeTimestamp(e)
  if (timestampMs == null) return equipmentStatusToLabel(dbStatus)
  return Date.now() - timestampMs <= REALTIME_STALE_MS ? '정상' : '주의'
}

function derivedLineOee(line: LineSummary): number {
  if (line.latestOee != null) return Math.round(Number(line.latestOee))
  const total = line.equipmentTotal || 0
  if (!total) return 0
  const score = (
    line.equipmentRunning
    + (line.equipmentStandby * 0.35)
    + (line.equipmentMaintenance * 0.15)
  ) / total * 100
  return Math.round(score)
}

function fmt(value: number | null, unit: string, digits = 1): string {
  if (value == null) return '-'
  return `${value.toFixed(digits)}${unit}`
}

function realtimeMetrics(e: Equipment): EquipmentMetric[] {
  const processMetrics = processRealtimeMetricConfigs(e.processType).map((config) => ({
    label: config.label,
    value: fmt(realtimeValue(e.equipmentCode, config.metric), config.unit, config.digits),
  }))
  const commonMetrics = [
    { label: '수신', value: realtimeReceiveLabel(e) },
    { label: '싸이클', value: fmt(realtimeValue(e.equipmentCode, 'cycle_time'), 's', 1) },
    { label: '전류', value: fmt(realtimeValue(e.equipmentCode, 'sensor_current'), 'A', 1) },
    { label: '전압', value: fmt(realtimeValue(e.equipmentCode, 'sensor_voltage'), 'V', 1) },
    { label: '온도', value: fmt(realtimeValue(e.equipmentCode, 'sensor_temperature'), '°C', 1) },
    { label: '진동', value: fmt(realtimeValue(e.equipmentCode, 'sensor_vibration'), '', 3) },
  ]
  const seen = new Set<string>()
  return [...processMetrics, ...commonMetrics].filter((metric) => {
    if (seen.has(metric.label)) return false
    seen.add(metric.label)
    return true
  })
}

const factoryLines = computed<FactoryLine[]>(() => {
  const lines = (linesData.value ?? []) as LineSummary[]
  return lines.map((line, idx) => {
    const baseTone = LINE_TONE_BY_INDEX[idx] ?? 'neutral'
    const oeeRounded = derivedLineOee(line)
    return {
      id: line.lineId,
      name: line.lineName,
      code: line.lineId,
      oee: oeeRounded,
      status: lineStatusToLabel(line.lineStatus),
      statusKind: lineStatusToKind(line.lineStatus),
      tone: lineStatusToTone(line.lineStatus, baseTone),
      // 백엔드 시계열 미제공: 라인 카드 sparkline 은 최신 OEE 만 평탄선으로 표시.
      sparkline: [oeeRounded, oeeRounded, oeeRounded, oeeRounded, oeeRounded, oeeRounded, oeeRounded, oeeRounded],
    }
  })
})

const equipmentMatrix = computed<EquipmentMatrix>(() => {
  const equipments = (equipmentsData.value ?? []) as Equipment[]
  const statusMap = statusMapFromList(statusesData.value ?? [])
  const matrix: EquipmentMatrix = {}

  for (const eq of equipments) {
    const lineId = eq.location ?? ''
    const stageKey = PROCESS_TYPE_TO_STAGE_KEY[eq.processType]
    if (!lineId || !stageKey) continue

    if (!matrix[lineId]) {
      matrix[lineId] = { cast: [], mach: [], wash: [], assy: [], insp: [] }
    }

    const status = effectiveEquipmentStatus(eq, statusMap.get(eq.equipmentCode))
    matrix[lineId][stageKey].push({
      id: eq.equipmentCode,
      name: eq.equipmentName,
      status,
      summary: equipmentSummary(eq),
      metrics: realtimeMetrics(eq),
    })
  }

  return matrix
})

const selectedStage = ref<{ lineId: string; stageKey: StageKey }>({
  lineId: '',
  stageKey: 'cast',
})
const isStagePopupOpen = ref(false)
const selectedLinePopupId = ref('')
const isLineDetailPopupOpen = ref(false)

watch(
  factoryLines,
  (lines) => {
    if (!lines.length) return
    if (!lines.find((l) => l.id === selectedStage.value.lineId)) {
      selectedStage.value = { lineId: lines[0].id, stageKey: 'cast' }
    }
  },
  { immediate: true },
)

const stageDetailContext = computed(() => {
  const sel = selectedStage.value
  const line = factoryLines.value.find((l) => l.id === sel.lineId)
  const stage = STAGE_ORDER.find((s) => s.key === sel.stageKey)
  if (!line || !stage) return null
  const equipments = equipmentMatrix.value[line.id]?.[stage.key] ?? []
  return { line, stage, equipments }
})

const stageAggregateKind = computed(() => {
  const ctx = stageDetailContext.value
  if (!ctx) return 'ok' as StatusKind
  return worstStatusKind(ctx.equipments)
})

const aggregateStatusEn = computed(() => {
  const k = stageAggregateKind.value
  if (k === 'alert') return 'ALERT'
  if (k === 'warn') return 'WARNING'
  return 'NORMAL'
})

const primaryEquipment = computed(() => stageDetailContext.value?.equipments[0] ?? null)

const secondaryEquipments = computed(() => {
  const list = stageDetailContext.value?.equipments ?? []
  return list.slice(1)
})

function equipmentsForLineStage(lineId: string, stageKey: StageKey): EquipmentItem[] {
  return equipmentMatrix.value[lineId]?.[stageKey] ?? []
}

function worstStatusKind(list: readonly EquipmentItem[]): StatusKind {
  if (list.some((e) => e.status === '경고')) return 'alert'
  if (list.some((e) => e.status === '주의')) return 'warn'
  return 'ok'
}

function cellToneClass(kind: StatusKind): string {
  if (kind === 'ok') return 'factory-stage-box--ok'
  if (kind === 'warn') return 'factory-stage-box--warn'
  return 'factory-stage-box--alert'
}

function lineSummaryOeeClass(kind: StatusKind): string {
  if (kind === 'ok') return 'factory-line-summary-oee--ok'
  if (kind === 'warn') return 'factory-line-summary-oee--warn'
  return 'factory-line-summary-oee--alert'
}

function equipmentStatusClass(status: StatusLabel): string {
  if (status === '정상') return 'factory-stage-equip-row--ok'
  if (status === '주의') return 'factory-stage-equip-row--warn'
  return 'factory-stage-equip-row--alert'
}

function selectStage(lineId: string, stageKey: StageKey): void {
  selectedStage.value = { lineId, stageKey }
}

function defaultStageForLine(lineId: string): StageKey {
  if (selectedStage.value.lineId === lineId) return selectedStage.value.stageKey
  return STAGE_ORDER.find((stage) => equipmentsForLineStage(lineId, stage.key).length > 0)?.key ?? 'cast'
}

function openStagePopup(lineId: string, stageKey = defaultStageForLine(lineId)): void {
  selectStage(lineId, stageKey)
  isStagePopupOpen.value = true
}

function closeStagePopup(): void {
  isStagePopupOpen.value = false
}

function isStageSelected(lineId: string, stageKey: StageKey): boolean {
  const s = selectedStage.value
  return s.lineId === lineId && s.stageKey === stageKey
}

function worstStatusLabel(list: readonly EquipmentItem[]): StatusLabel {
  const k = worstStatusKind(list)
  if (k === 'alert') return '경고'
  if (k === 'warn') return '주의'
  return '정상'
}

function sparklinePoints(values: readonly number[]): string {
  if (!values.length) return ''
  const w = 120
  const h = 36
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1 || 1)) * w
      const y = h - ((v - min) / span) * (h - 4) - 2
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

function aggregatePillTone(kind: StatusKind): 'run' | 'warn' | 'stop' {
  if (kind === 'alert') return 'stop'
  if (kind === 'warn') return 'warn'
  return 'run'
}

function stageIconToneClass(kind: StatusKind): string {
  if (kind === 'ok') return 'factory-stage-box-icon-wrap--ok'
  if (kind === 'warn') return 'factory-stage-box-icon-wrap--warn'
  return 'factory-stage-box-icon-wrap--alert'
}

const selectedLineDetail = computed(() =>
  lineDetails.value.find((line) => line.id === selectedLinePopupId.value)
    ?? lineDetails.value.find((line) => line.id === selectedStage.value.lineId)
    ?? lineDetails.value[0]
    ?? null,
)

watch(
  lineDetails,
  (list) => {
    if (!list.length) return
    const queryLineId = typeof route.query.lineId === 'string' ? route.query.lineId : ''
    if (queryLineId && list.some((line) => line.id === queryLineId)) {
      selectedLinePopupId.value = queryLineId
      if (route.query.popup === '1' || route.query.popup === 'true') {
        isLineDetailPopupOpen.value = true
      }
      return
    }
    if (!list.some((line) => line.id === selectedLinePopupId.value)) {
      selectedLinePopupId.value = list[0].id
    }
  },
  { immediate: true },
)

watch(
  () => route.query.lineId,
  () => {
    const queryLineId = typeof route.query.lineId === 'string' ? route.query.lineId : ''
    if (!queryLineId || !lineDetails.value.some((line) => line.id === queryLineId)) return
    selectedLinePopupId.value = queryLineId
    if (route.query.popup === '1' || route.query.popup === 'true') {
      isLineDetailPopupOpen.value = true
    }
  },
)

function openLineDetailPopup(lineId: string): void {
  selectedLinePopupId.value = lineId
  isLineDetailPopupOpen.value = true
}

function closeLineDetailPopup(): void {
  isLineDetailPopupOpen.value = false
}

function processIcon(key: LineProcessStage['key']) {
  const map = {
    casting: Flame,
    machining: Cog,
    washing: Droplets,
    assembly: Wrench,
    inspection: Search,
  }
  return map[key] ?? Factory
}
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
        <p>라인 A/B/C 3개 · 공정 순서: 주조→가공→세척→조립→검사</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">Factory Layout</p>
          <h1>라인별 현황</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <RouterLink class="ghost-button" :to="{ name: 'dashboard' }">
            <LayoutDashboard :size="16" />
            <span>대시보드</span>
          </RouterLink>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <div class="factory-layout-workspace factory-layout-workspace--process">
        <section class="dashboard-panel factory-flow-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Process overview</p>
              <h2>라인별 공정 흐름</h2>
            </div>
            <div class="section-title-trail">
              <div class="factory-status-legend" aria-label="설비 상태 범례">
                <span class="normal">정상</span>
                <span class="caution">주의</span>
                <span class="warn-strong">경고</span>
              </div>
              <Factory :size="22" aria-hidden="true" />
            </div>
          </div>

          <div class="factory-line-windows-canvas factory-line-windows-canvas--rows" aria-label="라인별 공정 배치">
            <section
              v-for="line in factoryLines"
              :key="line.id"
              class="factory-line-process-row"
              :class="`factory-line-process-row--${line.tone}`"
            >
              <button
                type="button"
                class="factory-line-summary-card"
                :aria-label="`${line.code} 라인 상세보기 팝업 열기`"
                @click="openLineDetailPopup(line.id)"
              >
                <strong class="factory-line-summary-code">{{ line.code }}</strong>
                <div class="factory-line-summary-spark" aria-hidden="true">
                  <svg
                    class="factory-sparkline-svg"
                    viewBox="0 0 120 36"
                    preserveAspectRatio="none"
                    width="120"
                    height="36"
                  >
                    <polyline
                      class="factory-sparkline-poly"
                      fill="none"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      :points="sparklinePoints(line.sparkline)"
                    />
                  </svg>
                </div>
                <span
                  class="factory-line-summary-oee"
                  :class="lineSummaryOeeClass(line.statusKind)"
                >
                  OEE {{ line.oee }}%
                </span>
              </button>

              <div class="factory-line-stages" :aria-label="`${line.name} 공정 순서`">
                <template v-for="(stage, si) in STAGE_ORDER" :key="stage.key">
                  <button
                    type="button"
                    class="factory-stage-box"
                    :class="[
                      cellToneClass(worstStatusKind(equipmentsForLineStage(line.id, stage.key))),
                      { 'factory-stage-box--selected': isStageSelected(line.id, stage.key) },
                    ]"
                    :aria-pressed="isStageSelected(line.id, stage.key)"
                    @click="selectStage(line.id, stage.key)"
                  >
                    <span
                      class="factory-stage-box-icon-wrap"
                      :class="
                        stageIconToneClass(worstStatusKind(equipmentsForLineStage(line.id, stage.key)))
                      "
                    >
                      <component :is="stage.icon" :size="22" stroke-width="2" aria-hidden="true" />
                    </span>
                    <span class="factory-stage-box-label">{{ stage.label }}</span>
                    <span class="factory-stage-box-badge">{{ stage.badge }}</span>
                  </button>
                  <span
                    v-if="si < STAGE_ORDER.length - 1"
                    class="factory-line-stage-arrow"
                    aria-hidden="true"
                  >
                    →
                  </span>
                </template>
              </div>
            </section>
          </div>
        </section>

        <aside
          v-if="stageDetailContext"
          class="dashboard-panel factory-equipment-detail-pane factory-equipment-detail-pane--right"
          aria-label="선택 공정 설비 상세"
        >
          <header class="factory-equipment-detail-head">
            <div class="section-title-row factory-equipment-detail-title-row">
              <div>
                <p class="panel-kicker">상세 설비 정보</p>
                <p class="factory-detail-en-caption">EQUIPMENT DETAILS</p>
                <h2 class="factory-detail-stage-heading">
                  {{ stageDetailContext.line.code }} — {{ stageDetailContext.stage.label }} ({{
                    stageDetailContext.stage.badge
                  }})
                </h2>
              </div>
              <span class="pill factory-detail-status-pill" :class="aggregatePillTone(stageAggregateKind)">
                <span>{{ worstStatusLabel(stageDetailContext.equipments) }}</span>
                <span class="factory-detail-status-pill-en">{{ aggregateStatusEn }}</span>
              </span>
            </div>
          </header>

          <div class="factory-equipment-detail-body">
            <template v-if="stageDetailContext?.equipments?.length">
              <div
                v-for="eq in stageDetailContext.equipments"
                :key="eq.id"
                class="factory-detail-primary-card"
              >
                <div class="factory-detail-primary-head">
                  <span class="factory-detail-primary-icon" aria-hidden="true">
                    <component :is="stageDetailContext.stage.icon" :size="28" stroke-width="2" />
                  </span>
                  <div>
                    <strong class="factory-detail-primary-name">{{ eq.name }}</strong>
                    <span class="factory-detail-primary-id">{{ eq.id }}</span>
                  </div>
                </div>
                <dl v-if="eq.metrics?.length" class="factory-detail-metrics">
                  <template v-for="m in eq.metrics" :key="m.label">
                    <dt>{{ m.label }}</dt>
                    <dd>{{ m.value }}</dd>
                  </template>
                </dl>
                <p v-else class="factory-detail-primary-summary">{{ eq.summary }}</p>
              </div>
            </template>
            <p v-else class="factory-stage-dialog-empty">등록된 설비가 없습니다.</p>
          </div>
          </aside>
      </div>

      <Teleport to="body">
        <div
          v-if="isLineDetailPopupOpen && selectedLineDetail"
          class="line-modal-backdrop"
          @click.self="closeLineDetailPopup"
        >
          <article
            class="line-detail-modal"
            role="dialog"
            aria-modal="true"
            :aria-label="`${selectedLineDetail.name} 라인 상세보기`"
          >
            <header class="line-detail-popup-head">
              <div>
                <p class="panel-kicker">Line Detail</p>
                <h2>{{ selectedLineDetail.name }}</h2>
                <p>
                  전체 알람 현황 · 전체 {{ selectedLineDetail.equipment }}대 /
                  처리완료 00 · 미처리 {{ selectedLineDetail.alarm }}
                </p>
              </div>
              <div class="line-modal-actions">
                <span class="pill run">OEE {{ selectedLineDetail.oee }}%</span>
                <button class="line-modal-close" type="button" aria-label="라인 상세 팝업 닫기" @click="closeLineDetailPopup">
                  <X :size="18" />
                </button>
              </div>
            </header>

            <section class="line-popup-layout-panel">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Line Flow</p>
                  <h3>라인별 현황</h3>
                </div>
                <Factory :size="22" />
              </div>
              <div class="line-flow-stage-row line-popup-mini-layout">
                <template v-for="(stage, index) in selectedLineDetail.stages" :key="stage.key">
                  <div v-if="index > 0" class="line-flow-arrow" aria-hidden="true">→</div>
                  <section class="line-flow-stage">
                    <h3>
                      <component :is="processIcon(stage.key)" :size="18" />
                      <span>{{ stage.label }}</span>
                    </h3>
                    <div class="line-flow-equipment-list">
                      <article
                        v-for="node in stage.nodes"
                        :key="node.id"
                        :class="['line-flow-equipment', `line-flow-equipment--${node.state}`]"
                      >
                        <component :is="processIcon(stage.key)" :size="24" />
                        <strong>{{ node.name }}</strong>
                        <small>{{ node.code }}</small>
                        <span>{{ node.cycleLabel }} · {{ node.tempLabel }}</span>
                      </article>
                    </div>
                  </section>
                </template>
              </div>
            </section>

            <section class="line-popup-quadrant-grid">
              <article class="line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line OEE</p>
                    <h3>종합 설비 효율 라인</h3>
                  </div>
                  <LayoutDashboard :size="22" />
                </div>
                <div class="line-popup-single-metric">
                  <article class="line-oee-donut-card">
                    <div class="line-analysis-donut" :style="{ '--value': `${selectedLineDetail.oee}%` }">
                      <strong>{{ selectedLineDetail.oee }}%</strong>
                    </div>
                    <div>
                      <h3>{{ selectedLineDetail.name }}</h3>
                      <p>{{ selectedLineDetail.equipment }}대 설비 기준 종합 설비 효율</p>
                    </div>
                  </article>
                </div>
              </article>

              <article class="line-popup-chart-panel">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line Equipment Status</p>
                    <h3>라인에 해당하는 설비 상태 분포도</h3>
                  </div>
                  <Factory :size="22" />
                </div>
                <article class="line-status-card">
                  <div
                    class="line-status-donut"
                    :style="{
                      '--run-end': `${selectedLineDetail.status.run}%`,
                      '--stop-end': `${selectedLineDetail.status.stopEnd}%`,
                    }"
                  >
                    <strong>{{ selectedLineDetail.equipment }}대</strong>
                  </div>
                  <div class="line-status-info">
                    <h3>{{ selectedLineDetail.name }}</h3>
                    <div class="line-status-legend">
                      <span class="run">가동 {{ selectedLineDetail.status.run }}%</span>
                      <span class="stop">정지 {{ selectedLineDetail.status.stop }}%</span>
                      <span class="wait">대기 {{ selectedLineDetail.status.wait }}%</span>
                    </div>
                  </div>
                </article>
              </article>

              <article class="line-popup-chart-panel line-popup-balance-single">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">Line Balancing</p>
                    <h3>라인밸런싱</h3>
                  </div>
                  <strong>{{ selectedLineDetail.balance }}%</strong>
                </div>
                <div class="line-balance-chart">
                  <div class="line-station-bars">
                    <i
                      v-for="station in selectedLineDetail.stations"
                      :key="`${selectedLineDetail.id}-${station.label}`"
                      :title="station.cycle ? `${station.label} ${station.cycle.toFixed(1)}s` : station.label"
                      :style="{ height: `${station.value}%` }"
                    >
                      <b>{{ station.label }}</b>
                    </i>
                  </div>
                </div>
              </article>

              <article class="line-popup-chart-panel line-popup-productivity-single">
                <div class="section-title-row">
                  <div>
                    <p class="panel-kicker">UPMH / UPH</p>
                    <h3>라인 생산성 분석</h3>
                  </div>
                  <strong>{{ selectedLineDetail.productivity }}%</strong>
                </div>
                <div class="productivity-chart">
                  <article>
                    <div class="productivity-label">
                      <strong>{{ selectedLineDetail.name }}</strong>
                      <span>{{ selectedLineDetail.productivity }}%</span>
                    </div>
                    <div class="productivity-bars">
                      <div>
                        <i :style="{ width: `${selectedLineDetail.upmhPercent}%` }"></i>
                        <span>UPMH {{ selectedLineDetail.upmh.toLocaleString() }}</span>
                      </div>
                      <div>
                        <i :style="{ width: `${selectedLineDetail.uphPercent}%` }"></i>
                        <span>UPH {{ selectedLineDetail.uph.toLocaleString() }}</span>
                      </div>
                    </div>
                  </article>
                </div>
              </article>
            </section>
          </article>
        </div>
      </Teleport>

      <Teleport to="body">
        <div
          v-if="isStagePopupOpen && stageDetailContext"
          class="factory-stage-dialog-backdrop"
          @click.self="closeStagePopup"
        >
          <article
            class="factory-stage-dialog"
            role="dialog"
            aria-modal="true"
            :aria-label="`${stageDetailContext.line.code} ${stageDetailContext.stage.label} 공정 상세`"
          >
            <header class="factory-stage-dialog-head">
              <div class="factory-stage-dialog-title-block">
                <component
                  :is="stageDetailContext.stage.icon"
                  class="factory-stage-dialog-icon"
                  :size="28"
                  stroke-width="2"
                  aria-hidden="true"
                />
                <div>
                  <p class="panel-kicker">Line Process Detail</p>
                  <h2 class="factory-stage-dialog-title">
                    {{ stageDetailContext.line.code }} — {{ stageDetailContext.stage.label }}
                  </h2>
                  <p class="factory-stage-dialog-sub">
                    OEE {{ stageDetailContext.line.oee }}% · 설비 {{ stageDetailContext.equipments.length }}대 ·
                    {{ worstStatusLabel(stageDetailContext.equipments) }}
                  </p>
                </div>
              </div>
              <button
                type="button"
                class="factory-stage-dialog-close"
                aria-label="공정 상세 팝업 닫기"
                @click="closeStagePopup"
              >
                <X :size="18" />
              </button>
            </header>

            <div class="factory-stage-dialog-body">
              <ul v-if="stageDetailContext.equipments.length" class="factory-stage-equip-list">
                <li
                  v-for="eq in stageDetailContext.equipments"
                  :key="`popup-${eq.id}`"
                  class="factory-stage-equip-row"
                  :class="equipmentStatusClass(eq.status)"
                >
                  <div class="factory-stage-equip-row-main">
                    <strong>{{ eq.name }}</strong>
                    <span class="factory-stage-equip-id">{{ eq.id }}</span>
                  </div>
                  <span :class="['factory-stage-equip-status', 'line-state', eq.status]">{{ eq.status }}</span>
                  <p class="factory-stage-equip-summary">{{ eq.summary }}</p>
                  <dl v-if="eq.metrics?.length" class="factory-detail-metrics factory-detail-metrics--popup">
                    <template v-for="metric in eq.metrics" :key="`popup-${eq.id}-${metric.label}`">
                      <dt>{{ metric.label }}</dt>
                      <dd>{{ metric.value }}</dd>
                    </template>
                  </dl>
                </li>
              </ul>
              <p v-else class="factory-stage-dialog-empty">등록된 설비가 없습니다.</p>
            </div>
          </article>
        </div>
      </Teleport>
    </section>
  </main>
</template>
