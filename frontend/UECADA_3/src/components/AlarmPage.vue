<script setup lang="ts">
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import 'vue-echarts/style.css'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  BarChart3,
  Bell,
  CalendarDays,
  Factory,
  LogOut,
  Wrench,
} from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useAlarms } from '@/composables/useAlarms'
import { useAlarmInsights } from '@/composables/useAlarmInsights'
import { useResolveAlarm } from '@/composables/useResolveAlarm'
import { useAuthStore } from '@/stores/auth'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const { navItems } = useAppNav()
const logout = useLogout()
const auth = useAuthStore()
const { data, isPending, isError, error, refetch, isFetching } = useAlarms()
const { trendDays, trendHasData, equipmentFrequency, typeFrequency, isTrendPending } = useAlarmInsights()
const resolveMutation = useResolveAlarm()

/** ECharts 알람 추이 — CSS % 막대 대신 축·툴팁·반응형을 안정적으로 처리 */
const trendChartOption = computed(() => {
  const days = trendDays.value
  const n = days.length
  const labelStep = n > 14 ? Math.ceil(n / 7) : 1

  return {
    grid: { left: 4, right: 8, top: 12, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(15, 31, 56, 0.92)',
      borderColor: 'transparent',
      textStyle: { color: '#f8fafc', fontSize: 12 },
      formatter: (params: { axisValue?: string; dataIndex?: number; value?: number }[]) => {
        const p = params[0]
        if (!p || p.dataIndex == null) return ''
        const day = days[p.dataIndex]
        return `${day.date}<br/><strong>${Number(p.value ?? 0).toLocaleString()}건</strong>`
      },
    },
    xAxis: {
      type: 'category',
      data: days.map((d) => d.date.slice(5)),
      axisLine: { lineStyle: { color: '#dce5ef' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        interval: 0,
        formatter: (_value: string, index: number) => {
          if (index === 0 || index === n - 1 || index % labelStep === 0) {
            return days[index]?.date.slice(5) ?? ''
          }
          return ''
        },
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(0, 44, 95, 0.06)' } },
      axisLabel: {
        color: '#94a3b8',
        fontSize: 10,
        formatter: (v: number) => (v >= 1000 ? `${Math.round(v / 1000)}k` : String(v)),
      },
    },
    series: [
      {
        type: 'bar',
        data: days.map((d) => d.count),
        barMaxWidth: 16,
        itemStyle: {
          borderRadius: [4, 4, 2, 2],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#3ec9e8' },
              { offset: 1, color: '#0057a4' },
            ],
          },
        },
        emphasis: {
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: '#62d9ff' },
                { offset: 1, color: '#0070cc' },
              ],
            },
          },
        },
      },
    ],
  }
})

const pad2 = (n: number) => String(n).padStart(2, '0')

function formatNow(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

const now = ref(new Date())
const currentTimeText = computed(() => formatNow(now.value))
let timerId: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  // 다음 분 시작 직후로 정렬한 뒤 1분 간격으로 갱신
  const ms = (60 - now.value.getSeconds()) * 1000 - now.value.getMilliseconds()
  timerId = setTimeout(() => {
    now.value = new Date()
    timerId = setInterval(() => {
      now.value = new Date()
    }, 60_000)
  }, Math.max(ms, 0))
})
onBeforeUnmount(() => {
  if (timerId) {
    clearTimeout(timerId)
    clearInterval(timerId)
  }
})

// 처리 중인 알람 ID 집합 — 동일 버튼만 비활성화/스피너 표시
const pendingIds = ref<Set<number>>(new Set())
const isResolving = (id: number) => pendingIds.value.has(id)
const resolverLabel = computed(() => (auth.role === 'admin' ? '관리자' : '작업자'))

function handleResolve(alarmId: number) {
  if (!alarmId || isResolving(alarmId)) return
  const next = new Set(pendingIds.value)
  next.add(alarmId)
  pendingIds.value = next

  const d = new Date()
  const resolvedAt = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  resolveMutation.mutate(
    {
      alarmId,
      payload: {
        resolvedBy: resolverLabel.value,
        resolvedAt,
        comment: 'UI에서 처리 완료',
      },
    },
    {
      onSettled: () => {
        const after = new Set(pendingIds.value)
        after.delete(alarmId)
        pendingIds.value = after
      },
    },
  )
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
        <p>알림 현황, 발생 이력 및 추이, 조건별 빈도 분석</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">Alarm Analytics</p>
          <h1>알람 화면</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            {{ currentTimeText }}
          </span>
          <RouterLink class="ghost-button" :to="{ name: 'equipment' }">
            <Wrench :size="16" />
            <span>설비별 화면</span>
          </RouterLink>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <p v-if="isFetching && !isPending" class="dashboard-kicker" style="margin: 0 0 8px">
        최신 알람 동기화 중…
      </p>

      <div v-if="isPending" class="dashboard-panel" style="padding: 48px; text-align: center">
        <p class="panel-kicker">Loading</p>
        <h2>알람 데이터를 불러오는 중입니다</h2>
        <p style="color: #64748b; margin-top: 8px">잠시만 기다려 주세요.</p>
      </div>

      <div v-else-if="isError" class="dashboard-panel" style="padding: 48px; text-align: center">
        <p class="panel-kicker">Error</p>
        <h2>알람을 불러오지 못했습니다</h2>
        <p style="color: #64748b; margin-top: 8px">{{ error?.message ?? '알 수 없는 오류' }}</p>
        <button type="button" class="login-button" style="margin-top: 16px" @click="() => refetch()">
          다시 시도
        </button>
      </div>

      <template v-else-if="data">
        <section class="alarm-summary-grid" aria-label="알림 현황요약">
          <article v-for="item in data.summary" :key="item.label" :class="['alarm-summary-card', item.tone]">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <p>{{ item.detail }}</p>
          </article>
        </section>

        <section class="alarm-trend-history-grid">
          <article class="dashboard-panel alarm-trend-panel">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Alarm Trend</p>
                <h2>알람 발생 추이 (최근 30일)</h2>
              </div>
              <BarChart3 :size="22" />
            </div>

            <div v-if="isTrendPending && !trendHasData" class="alarm-trend-chart alarm-trend-chart--empty">
              <span class="alarm-trend-empty">추이 데이터를 불러오는 중…</span>
            </div>
            <div v-else-if="!trendHasData" class="alarm-trend-chart alarm-trend-chart--empty">
              <span class="alarm-trend-empty">표시할 추이 데이터가 없습니다.</span>
            </div>
            <div v-else class="alarm-trend-chart alarm-trend-chart--echarts">
              <v-chart class="alarm-trend-echart" :option="trendChartOption" autoresize />
            </div>
          </article>

          <article class="dashboard-panel alarm-history-panel-wide">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Alarm History</p>
                <h2>
                  알람 발생 이력
                  <small
                    v-if="data.totalCount > data.rows.length"
                    class="alarm-history-count-note"
                  >
                    최신 {{ data.rows.length }}건 / 전체 {{ data.totalCount.toLocaleString() }}건
                  </small>
                </h2>
              </div>
              <AlertTriangle :size="22" />
            </div>

            <div v-if="data.rows.length === 0" class="alarm-history-table-wrap" style="padding: 32px; text-align: center">
              <p class="panel-kicker">Empty</p>
              <h2>표시할 알람이 없습니다</h2>
              <p style="color: #64748b; margin-top: 8px">필터를 변경하거나 나중에 다시 확인해 주세요.</p>
            </div>

            <div v-else class="alarm-history-table-wrap">
              <table class="alarm-history-table">
                <thead>
                  <tr>
                    <th>발생 시간</th>
                    <th>설비명</th>
                    <th>알람 유형</th>
                    <th>분류</th>
                    <th>내용</th>
                    <th>상태</th>
                    <th>조치</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(row, idx) in data.rows"
                    :key="`${row.alarmId || 'na'}-${row.time}-${idx}`"
                    :class="{ 'alarm-history-row--critical': row.type === '긴급' }"
                  >
                    <td>{{ row.time }}</td>
                    <td><strong>{{ row.equipment }}</strong></td>
                    <td><span :class="['alarm-level', row.type]">{{ row.type }}</span></td>
                    <td>{{ row.category }}</td>
                    <td>{{ row.message }}</td>
                    <td><span :class="['alarm-status-badge', row.status]">{{ row.status }}</span></td>
                    <td>
                      <button
                        v-if="row.status !== '처리완료' && row.alarmId"
                        type="button"
                        class="ghost-button"
                        style="padding: 4px 10px; font-size: 12px"
                        :disabled="isResolving(row.alarmId)"
                        :aria-busy="isResolving(row.alarmId)"
                        @click="handleResolve(row.alarmId)"
                      >
                        {{ isResolving(row.alarmId) ? '처리중…' : '처리' }}
                      </button>
                      <span v-else style="color: #94a3b8">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
        </section>

        <section class="alarm-frequency-grid">
          <article class="dashboard-panel">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Equipment Frequency</p>
                <h2>설비별 알람 발생 횟수</h2>
              </div>
              <Factory :size="22" />
            </div>

            <div v-if="equipmentFrequency.length === 0" class="frequency-empty">
              <span>표시할 설비별 빈도 데이터가 없습니다.</span>
            </div>
            <div v-else class="frequency-bar-list">
              <article v-for="item in equipmentFrequency" :key="item.label">
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.type }}</span>
                </div>
                <div class="frequency-meter">
                  <i :style="{ width: `${item.percent}%` }"></i>
                </div>
                <b>{{ item.count }}건</b>
              </article>
            </div>
          </article>

          <article class="dashboard-panel">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Type Frequency</p>
                <h2>알람 유형별 발생 횟수</h2>
              </div>
              <Bell :size="22" />
            </div>

            <div v-if="typeFrequency.length === 0" class="frequency-empty">
              <span>표시할 유형별 빈도 데이터가 없습니다.</span>
            </div>
            <div v-else class="frequency-bar-list type-frequency">
              <article v-for="item in typeFrequency" :key="item.label">
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>알람 유형</span>
                </div>
                <div class="frequency-meter">
                  <i :style="{ width: `${item.percent}%` }"></i>
                </div>
                <b>{{ item.count }}건</b>
              </article>
            </div>
          </article>
        </section>
      </template>
    </section>
  </main>
</template>
