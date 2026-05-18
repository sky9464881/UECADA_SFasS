<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  BarChart3,
  CalendarDays,
  Cog,
  Droplets,
  Factory,
  Flame,
  LayoutDashboard,
  LogOut,
  Search,
  Wrench,
} from 'lucide-vue-next'
import type { Component } from 'vue'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useFactoryLayout } from '@/composables/useFactoryLayout'
import {
  canOpenWebScadaPopup,
  openWebScadaPopup,
  type WebScadaStageKey,
} from '@/composables/useWebScadaLinks'
import type { LineSummary, LineStatusCode } from '@/types/line'
import type { Equipment, EquipmentStatusItem, EquipmentStatusCode } from '@/types/equipment'

type StatusLabel = '정상' | '주의' | '경고'
type StatusKind = 'ok' | 'warn' | 'alert'
type StageKey = WebScadaStageKey
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

const STAGE_ORDER: readonly StageDef[] = [
  { key: 'cast', label: '주조', badge: 'CAST', icon: Flame },
  { key: 'mach', label: '가공', badge: 'MACHINE', icon: Cog },
  { key: 'wash', label: '세척', badge: 'WASH', icon: Droplets },
  { key: 'assy', label: '조립', badge: 'ASSEMBLE', icon: Wrench },
  { key: 'insp', label: '검사', badge: 'INSPECT', icon: Search },
] as const

const { lines: linesData, equipments: equipmentsData, statuses: statusesData } = useFactoryLayout()

const PROCESS_TYPE_TO_STAGE_KEY: Record<string, StageKey> = {
  주조: 'cast',
  가공: 'mach',
  세척: 'wash',
  조립: 'assy',
  검사: 'insp',
}

const LINE_TONE_BY_INDEX: readonly LineTone[] = ['blue', 'yellow', 'neutral']

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

const factoryLines = computed<FactoryLine[]>(() => {
  const lines = (linesData.value ?? []) as LineSummary[]
  return lines.map((line, idx) => {
    const baseTone = LINE_TONE_BY_INDEX[idx] ?? 'neutral'
    const oeeRounded = line.latestOee == null ? 0 : Math.round(Number(line.latestOee))
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

    const status = equipmentStatusToLabel(statusMap.get(eq.equipmentCode))
    matrix[lineId][stageKey].push({
      id: eq.equipmentCode,
      name: eq.equipmentName,
      status,
      summary: equipmentSummary(eq),
    })
  }

  return matrix
})

const selectedStage = ref<{ lineId: string; stageKey: StageKey }>({
  lineId: '',
  stageKey: 'cast',
})

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

const webScadaReady = canOpenWebScadaPopup()
const webScadaHint = webScadaReady
  ? '웹스카다 → SMWP 자동로그인 (Pro=yhh0518 #LDV)'
  : '웹스카다: .env에 VITE_SWMP_DEFAULT_URL 설정'

function selectStage(lineId: string, stageKey: StageKey): void {
  selectedStage.value = { lineId, stageKey }
}

function openWebScada(lineId?: string): void {
  const id = lineId ?? factoryLines.value[0]?.id ?? 'LINE-A'
  if (!openWebScadaPopup(id)) {
    window.alert('웹스카다 팝업을 열 수 없습니다. 팝업 차단을 해제하거나 .env 설정을 확인하세요.')
  }
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
          <h1>공장 레이아웃</h1>
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
          <RouterLink class="ghost-button" :to="{ name: 'lines' }">
            <BarChart3 :size="16" />
            <span>라인 상세</span>
          </RouterLink>
          <button
            type="button"
            class="ghost-button factory-webscada-open-btn"
            :disabled="!webScadaReady"
            title="웹스카다 SMWP #LDV (팝업)"
            @click="openWebScada()"
          >
            <Factory :size="16" />
            <span>웹스카다</span>
          </button>
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
              <span class="factory-webscada-hint" :class="{ 'factory-webscada-hint--off': !webScadaReady }">
                {{ webScadaHint }}
              </span>
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
                class="factory-line-summary-card factory-line-summary-card--scada"
                :aria-label="`${line.code} 요약 — 클릭 시 웹스카다`"
                :title="webScadaReady ? '웹스카다 열기 (팝업)' : 'VITE_SWMP_DEFAULT_URL 미설정'"
                @click="openWebScada(line.id)"
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
              <template v-if="primaryEquipment">
                <div class="factory-detail-primary-card">
                  <div class="factory-detail-primary-head">
                    <span class="factory-detail-primary-icon" aria-hidden="true">
                      <component :is="stageDetailContext.stage.icon" :size="28" stroke-width="2" />
                    </span>
                    <div>
                      <strong class="factory-detail-primary-name">{{ primaryEquipment.name }}</strong>
                      <span class="factory-detail-primary-id">{{ primaryEquipment.id }}</span>
                    </div>
                  </div>
                  <dl
                    v-if="primaryEquipment.metrics?.length"
                    class="factory-detail-metrics"
                  >
                    <template v-for="m in primaryEquipment.metrics" :key="m.label">
                      <dt>{{ m.label }}</dt>
                      <dd>{{ m.value }}</dd>
                    </template>
                  </dl>
                  <p v-else class="factory-detail-primary-summary">{{ primaryEquipment.summary }}</p>
                </div>

                <ul v-if="secondaryEquipments.length" class="factory-stage-equip-list factory-stage-equip-list--compact">
                  <li
                    v-for="eq in secondaryEquipments"
                    :key="eq.id"
                    class="factory-stage-equip-row"
                    :class="equipmentStatusClass(eq.status)"
                  >
                    <div class="factory-stage-equip-row-main">
                      <strong>{{ eq.name }}</strong>
                      <span class="factory-stage-equip-id">{{ eq.id }}</span>
                    </div>
                    <span :class="['factory-stage-equip-status', 'line-state', eq.status]">{{ eq.status }}</span>
                    <p class="factory-stage-equip-summary">{{ eq.summary }}</p>
                  </li>
                </ul>
              </template>
              <p v-else class="factory-stage-dialog-empty">등록된 설비가 없습니다.</p>
            </div>
          </aside>
      </div>
    </section>
  </main>
</template>
