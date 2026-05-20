<script setup lang="ts">
import {
  AlertTriangle,
  BarChart3,
  Bell,
  CalendarDays,
  Factory,
  LogOut,
  Wrench,
} from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useAlarms } from '@/composables/useAlarms'
import { useAlarmInsights } from '@/composables/useAlarmInsights'
import { useResolveAlarm } from '@/composables/useResolveAlarm'
import { useAuthStore } from '@/stores/auth'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { fetchAlarmCounts, resolveAlarm } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'

const { navItems } = useAppNav()
const logout = useLogout()
const authStore = useAuthStore()
const queryClient = useQueryClient()

const statusFilter = ref<string | null>(null)
const statusFilterOptions = [
  { label: '전체', value: null },
  { label: '미처리', value: 'OPEN' },
  { label: '처리완료', value: 'RESOLVED' },
]

const PAGE_SIZE = 5
const currentPage = ref(1)

watch(statusFilter, () => {
  currentPage.value = 1
  selectedIds.value = new Set()
})
watch(currentPage, () => { selectedIds.value = new Set() })

const { data, isPending, isError, error, refetch } = useAlarms(statusFilter)
const { trendDays, equipmentFrequency, typeFrequency } = useAlarmInsights()
const resolveMutation = useResolveAlarm()

const pagedRows = computed(() => {
  const rows = data.value?.rows ?? []
  const start = (currentPage.value - 1) * PAGE_SIZE
  return rows.slice(start, start + PAGE_SIZE)
})
const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.rows?.length ?? 0) / PAGE_SIZE)))

// 요약 카드: 필터와 무관하게 전체 DB 기준 카운트
const { data: counts } = useQuery({
  queryKey: ['alarms', 'counts'],
  queryFn: fetchAlarmCounts,
  refetchInterval: POLL_INTERVAL_MS.alarm,
  refetchIntervalInBackground: true,
})
const summaryCards = computed(() => [
  { label: '전체 알람 수', value: counts.value?.total ?? 0, detail: '당일 기준', tone: 'info' as const },
  { label: '긴급 알람', value: counts.value?.critical ?? 0, detail: '즉시 조치 필요', tone: 'critical' as const },
  { label: '처리 완료', value: counts.value?.resolved ?? 0, detail: '당일 기준', tone: 'done' as const },
  { label: '미처리 알람', value: counts.value?.open ?? 0, detail: '담당자 확인 필요', tone: 'pending' as const },
])

// 전체 선택 / 일괄 처리
const selectedIds = ref<Set<number>>(new Set())

const pageUnresolvedIds = computed(() =>
  pagedRows.value
    .filter((row) => row.status !== '처리완료' && row.alarmId)
    .map((row) => row.alarmId),
)

const isAllPageSelected = computed(() =>
  pageUnresolvedIds.value.length > 0 &&
  pageUnresolvedIds.value.every((id) => selectedIds.value.has(id)),
)

function toggleSelectAll() {
  const next = new Set(selectedIds.value)
  if (isAllPageSelected.value) {
    pageUnresolvedIds.value.forEach((id) => next.delete(id))
  } else {
    pageUnresolvedIds.value.forEach((id) => next.add(id))
  }
  selectedIds.value = next
}

function toggleSelect(id: number) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

const isBulkResolving = ref(false)

async function handleBulkResolve() {
  if (!selectedIds.value.size || isBulkResolving.value) return
  isBulkResolving.value = true
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const resolvedAt = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  try {
    await Promise.all(
      [...selectedIds.value].map((id) =>
        resolveAlarm(id, { resolvedBy: '관리자', resolvedAt, comment: 'UI 일괄 처리' }),
      ),
    )
    selectedIds.value = new Set()
    await queryClient.invalidateQueries({ queryKey: ['alarms'] })
  } finally {
    isBulkResolving.value = false
  }
}

const userDisplayName = computed(() => authStore.user?.userName ?? '사용자')
const userRoleDisplay = computed(() => {
  const r = authStore.role
  if (r === 'admin') return '전체 관리자'
  if (r === 'manager') return '라인 관리자'
  return '작업자'
})

function formatDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval> | null = null
onMounted(() => { clockTimer = setInterval(() => { now.value = new Date() }, 60_000) })
onUnmounted(() => { if (clockTimer) clearInterval(clockTimer) })
const currentTimeDisplay = computed(() => formatDateTime(now.value))

function handleResolve(alarmId: number) {
  if (!alarmId) return
  if (resolveMutation.isPending.value) return
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const resolvedAt = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  resolveMutation.mutate({
    alarmId,
    payload: {
      resolvedBy: '관리자',
      resolvedAt,
      comment: 'UI에서 처리 완료',
    },
  })
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
        <span>{{ userRoleDisplay }}</span>
        <strong>{{ userDisplayName }}</strong>
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
            {{ currentTimeDisplay }}
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
          <article v-for="item in summaryCards" :key="item.label" :class="['alarm-summary-card', item.tone]">
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
                <h2>알람 발생 추이 (이번 주)</h2>
              </div>
              <BarChart3 :size="22" />
            </div>

            <div v-if="trendDays.length === 0" class="alarm-trend-chart" style="align-items: center; justify-content: center; min-height: 180px">
              <span style="color: #64748b">표시할 추이 데이터가 없습니다.</span>
            </div>
            <div v-else class="alarm-trend-chart" :style="{ gridTemplateColumns: `repeat(${trendDays.length}, minmax(0, 1fr))` }">
              <div v-for="item in trendDays" :key="item.label" class="alarm-trend-column">
                <i :style="{ height: `${item.percent}%` }"></i>
                <b>{{ item.count }}건</b>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </article>

          <article class="dashboard-panel alarm-history-panel-wide">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Alarm History</p>
                <h2>알람 발생 이력</h2>
              </div>
              <div class="alarm-filter-seg" role="group" aria-label="상태 필터">
                <button
                  v-for="opt in statusFilterOptions"
                  :key="String(opt.value)"
                  type="button"
                  :class="{ on: statusFilter === opt.value }"
                  @click="statusFilter = opt.value"
                >{{ opt.label }}</button>
              </div>
            </div>

            <div v-if="data.rows.length === 0" class="alarm-history-table-wrap" style="padding: 32px; text-align: center">
              <p class="panel-kicker">Empty</p>
              <h2>표시할 알람이 없습니다</h2>
              <p style="color: #64748b; margin-top: 8px">필터를 변경하거나 나중에 다시 확인해 주세요.</p>
            </div>

            <div v-else class="alarm-history-table-wrap">
              <div v-if="selectedIds.size > 0" class="alarm-bulk-bar">
                <span>{{ selectedIds.size }}건 선택됨</span>
                <button
                  type="button"
                  class="ghost-button"
                  style="padding: 4px 14px; font-size: 12px; background:#dc2626; color:#fff; border:0"
                  :disabled="isBulkResolving"
                  @click="handleBulkResolve"
                >
                  {{ isBulkResolving ? '처리중…' : '선택 처리' }}
                </button>
              </div>
              <table class="alarm-history-table">
                <thead>
                  <tr>
                    <th style="width:36px; text-align:center">
                      <input
                        type="checkbox"
                        :checked="isAllPageSelected"
                        :indeterminate="selectedIds.size > 0 && !isAllPageSelected"
                        @change="toggleSelectAll"
                      />
                    </th>
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
                  <tr v-for="row in pagedRows" :key="`${row.alarmId}-${row.time}`">
                    <td style="text-align:center">
                      <input
                        v-if="row.status !== '처리완료' && row.alarmId"
                        type="checkbox"
                        :checked="selectedIds.has(row.alarmId)"
                        @change="toggleSelect(row.alarmId)"
                      />
                    </td>
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
                        :disabled="resolveMutation.isPending.value"
                        @click="handleResolve(row.alarmId)"
                      >
                        {{ resolveMutation.isPending.value ? '처리중…' : '처리' }}
                      </button>
                      <span v-else style="color: #94a3b8">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="alarm-pagination">
                <button type="button" :disabled="currentPage <= 1" @click="currentPage--">‹</button>
                <span>{{ currentPage }} / {{ totalPages }}</span>
                <button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">›</button>
              </div>
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

            <div class="frequency-bar-list">
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

            <div class="frequency-bar-list type-frequency">
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
