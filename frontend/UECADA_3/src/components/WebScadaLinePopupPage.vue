<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Activity, Cog, Droplets, Factory, Flame, Gauge, Search, TrendingUp, Wrench } from 'lucide-vue-next'
import type { Component } from 'vue'
import { useDashboard } from '@/composables/useDashboard'
import { useFactoryLayout } from '@/composables/useFactoryLayout'
import { useLineDetails } from '@/composables/useLineDetails'
import { openExternalWebScada } from '@/composables/useWebScadaLinks'
import type { Equipment, EquipmentStatusCode } from '@/types/equipment'

const route = useRoute()
const lineId = computed(() => String(route.query.line ?? 'LINE-A'))

const STAGES: { key: string; label: string; icon: Component }[] = [
  { key: 'cast', label: '주조', icon: Flame },
  { key: 'mach', label: '가공', icon: Cog },
  { key: 'wash', label: '세척', icon: Droplets },
  { key: 'assy', label: '조립', icon: Wrench },
  { key: 'insp', label: '검사', icon: Search },
]

const PROCESS_TYPE_TO_KEY: Record<string, string> = {
  주조: 'cast',
  가공: 'mach',
  세척: 'wash',
  조립: 'assy',
  검사: 'insp',
}

const { data: dashboardData } = useDashboard()
const { lines } = useLineDetails()
const { equipments, statuses } = useFactoryLayout()

const line = computed(() => {
  const id = lineId.value
  return (
    lines.value.find((l) => l.lineId === id || l.name === id) ??
    lines.value[0]
  )
})

const alarmSummary = computed(() => {
  const a = dashboardData.value?.alarmSummary
  return {
    total: a?.total ?? 0,
    urgent: a?.critical ?? 0,
    resolved: a?.resolved ?? 0,
    pending: a?.open ?? 0,
  }
})

function statusForEquip(code: string): string {
  const map = new Map<string, EquipmentStatusCode>()
  for (const s of statuses.value ?? []) {
    map.set(s.equipId, s.statusCode)
  }
  const st = map.get(code)
  if (st === 'ALARM') return '이상'
  if (st === 'MAINTENANCE') return '경고'
  return '정상'
}

const stageNodes = computed(() => {
  const list = (equipments.value ?? []) as Equipment[]
  const byStage = new Map<string, Equipment[]>()
  for (const eq of list) {
    if (eq.location !== lineId.value && !eq.location?.includes('LINE')) continue
    const sk = PROCESS_TYPE_TO_KEY[eq.processType]
    if (!sk) continue
    if (!byStage.has(sk)) byStage.set(sk, [])
    byStage.get(sk)!.push(eq)
  }
  return STAGES.map((stage) => {
    const eqs = byStage.get(stage.key) ?? []
    const primary = eqs[0]
    const status = primary ? statusForEquip(primary.equipmentCode) : '정상'
    return {
      ...stage,
      name: primary?.equipmentName ?? stage.label,
      status,
    }
  })
})

const displayLine = computed(() => {
  const l = line.value
  if (!l) {
    return {
      name: lineId.value.replace('LINE-', 'Line '),
      oee: 0,
      equipment: 0,
      status: { run: 0, stop: 0, wait: 0, stopEnd: 0 },
      balance: 0,
      stations: [0, 0, 0, 0, 0, 0],
      productivity: 0,
      upmh: 0,
      uph: 0,
      upmhPercent: 0,
      uphPercent: 0,
    }
  }
  return l
})

function openExternal() {
  openExternalWebScada()
}
</script>

<template>
  <div class="web-scada-popup-shell">
    <header class="web-scada-popup-head">
      <div>
        <p class="panel-kicker">Web SCADA · Line View</p>
        <h1>{{ displayLine.name }}</h1>
      </div>
      <div class="web-scada-popup-actions">
        <button type="button" class="ghost-button" @click="openExternal">
          <Factory :size="16" />
          <span>외부 SMWP (#LDV)</span>
        </button>
      </div>
    </header>

    <section class="dashboard-panel web-scada-popup-alarms">
      <div class="section-title-row">
        <div>
          <p class="panel-kicker">Alarm</p>
          <h2>전체 알람 현황</h2>
        </div>
        <Activity :size="20" />
      </div>
      <div class="dash-alarm-summary-grid">
        <div class="dash-alarm-summary-tile dash-alarm-summary-tile--info">
          <span class="dash-alarm-summary-label">전체</span>
          <strong class="dash-alarm-summary-value">{{ alarmSummary.total }}</strong>
        </div>
        <div class="dash-alarm-summary-tile dash-alarm-summary-tile--urgent">
          <span class="dash-alarm-summary-label">긴급</span>
          <strong class="dash-alarm-summary-value">{{ alarmSummary.urgent }}</strong>
        </div>
        <div class="dash-alarm-summary-tile dash-alarm-summary-tile--done">
          <span class="dash-alarm-summary-label">처리완료</span>
          <strong class="dash-alarm-summary-value">{{ alarmSummary.resolved }}</strong>
        </div>
        <div class="dash-alarm-summary-tile dash-alarm-summary-tile--pending">
          <span class="dash-alarm-summary-label">미처리</span>
          <strong class="dash-alarm-summary-value">{{ alarmSummary.pending }}</strong>
        </div>
      </div>
    </section>

    <section class="dashboard-panel line-popup-layout-panel">
      <div class="section-title-row">
        <div>
          <p class="panel-kicker">Process</p>
          <h3>라인별 현황</h3>
        </div>
      </div>
      <div class="line-popup-mini-layout">
        <div class="line-popup-mini-label">
          <span>LINE FLOW</span>
          <strong>{{ displayLine.name }}</strong>
          <b>주조 → 가공 → 세척 → 조립 → 검사</b>
        </div>
        <div class="line-popup-mini-conveyor line-popup-mini-conveyor-dense" aria-label="공정 흐름">
          <button
            v-for="node in stageNodes"
            :key="node.key"
            type="button"
            class="line-popup-mini-node"
            :class="node.status"
          >
            <component :is="node.icon" :size="20" aria-hidden="true" />
            <strong>{{ node.label }}</strong>
            <small>{{ node.name }}</small>
          </button>
        </div>
      </div>
    </section>

    <section class="line-popup-quadrant-grid">
      <article class="dashboard-panel line-popup-chart-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line OEE</p>
            <h3>종합 설비 효율 라인</h3>
          </div>
          <Gauge :size="20" />
        </div>
        <div class="line-popup-single-metric line-oee-donut-grid">
          <article class="line-oee-donut-card">
            <div class="line-analysis-donut" :style="{ '--value': `${displayLine.oee}%` }">
              <strong>{{ displayLine.oee }}%</strong>
            </div>
            <div>
              <h3>{{ displayLine.name }}</h3>
              <p>{{ displayLine.equipment }}대 설비 기준 종합 설비 효율</p>
            </div>
          </article>
        </div>
      </article>

      <article class="dashboard-panel line-popup-chart-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Status</p>
            <h3>라인에 해당하는 설비 상태 분포도</h3>
          </div>
          <Activity :size="20" />
        </div>
        <div class="line-popup-single-metric line-status-donut-grid">
          <article class="line-status-card">
            <div
              class="line-status-donut"
              :style="{
                '--run-end': `${displayLine.status.run}%`,
                '--stop-end': `${displayLine.status.stopEnd}%`,
              }"
            >
              <strong>{{ displayLine.equipment }}대</strong>
            </div>
            <div class="line-status-info">
              <h3>{{ displayLine.name }}</h3>
              <div class="line-status-legend">
                <span class="run">가동 {{ displayLine.status.run }}%</span>
                <span class="stop">정지 {{ displayLine.status.stop }}%</span>
                <span class="wait">대기 {{ displayLine.status.wait }}%</span>
              </div>
            </div>
          </article>
        </div>
      </article>

      <article class="dashboard-panel line-popup-chart-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line Balancing</p>
            <h3>라인밸런싱 분석 차트</h3>
          </div>
          <TrendingUp :size="20" />
        </div>
        <div class="line-popup-balance-single line-balance-chart-grid">
          <article class="line-balance-chart">
            <div class="line-chart-head">
              <strong>{{ displayLine.name }}</strong>
              <span>{{ displayLine.balance }}%</span>
            </div>
            <div class="line-station-bars">
              <i
                v-for="(value, index) in displayLine.stations"
                :key="index"
                :style="{ height: `${value}%` }"
              >
                <b>{{ index + 1 }}</b>
              </i>
            </div>
          </article>
        </div>
      </article>

      <article class="dashboard-panel line-popup-chart-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">UPMH / UPH</p>
            <h3>라인별 생산성 분석 차트</h3>
          </div>
          <Factory :size="20" />
        </div>
        <div class="line-popup-productivity-single productivity-chart">
          <article>
            <div class="productivity-label">
              <strong>{{ displayLine.name }}</strong>
              <span>{{ displayLine.productivity }}%</span>
            </div>
            <div class="productivity-bars">
              <div>
                <i :style="{ width: `${displayLine.upmhPercent}%` }"></i>
                <span>UPMH {{ displayLine.upmh.toLocaleString() }}</span>
              </div>
              <div>
                <i :style="{ width: `${displayLine.uphPercent}%` }"></i>
                <span>UPH {{ displayLine.uph.toLocaleString() }}</span>
              </div>
            </div>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>
