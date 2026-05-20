import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { extractCategory, fetchAlarmStats, fetchAlarmsRaw } from '@/api/alarmApi'
import type { AlarmResponse, AlarmStatItem } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'

export interface AlarmTrendDay {
  label: string
  count: number
  percent: number
}

export interface AlarmFrequencyItem {
  label: string
  count: number
  percent: number
  type?: string
}

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

function toLocalIso(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

/** 당일 00:00:00 ~ 23:59:59 */
function todayRange(): { from: string; to: string } {
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  const to   = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  return { from: toLocalIso(from), to: toLocalIso(to) }
}

/** 이번 주 월요일 00:00 ~ 일요일 23:59:59 */
function thisWeekRange(): { from: string; to: string; dayLabels: string[] } {
  const today = new Date()
  const dow = today.getDay()
  const mondayOffset = dow === 0 ? -6 : 1 - dow

  const monday = new Date(today)
  monday.setDate(today.getDate() + mondayOffset)
  monday.setHours(0, 0, 0, 0)

  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  sunday.setHours(23, 59, 59, 999)

  const dayLabels: string[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    dayLabels.push(`${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`)
  }

  return { from: toLocalIso(monday), to: toLocalIso(sunday), dayLabels }
}

function withPercent<T extends { count: number }>(items: T[]): (T & { percent: number })[] {
  const max = items.reduce((m, it) => Math.max(m, it.count), 0)
  return items.map((it) => ({ ...it, percent: max ? Math.round((it.count / max) * 100) : 0 }))
}

export function useAlarmInsights() {
  const { from: weekFrom, to: weekTo, dayLabels } = thisWeekRange()
  const { from: todayFrom, to: todayTo } = todayRange()

  // 추이 차트: 이번 주 기준
  const statsQuery = useQuery({
    queryKey: ['alarm-stats', weekFrom, weekTo],
    queryFn: () => fetchAlarmStats(weekFrom, weekTo),
    refetchInterval: POLL_INTERVAL_MS.alarmInsights,
    refetchIntervalInBackground: true,
    staleTime: 0,
  })

  // 설비별·유형별 빈도: 당일 기준
  const rawTodayQuery = useQuery({
    queryKey: ['alarms', 'raw-today', todayFrom, todayTo],
    queryFn: () => fetchAlarmsRaw(null, todayFrom, todayTo),
    refetchInterval: POLL_INTERVAL_MS.alarmInsights,
    refetchIntervalInBackground: true,
    staleTime: 0,
  })

  /** 이번 주 월~일 7일 고정, 없는 날 0건 */
  const trendDays = computed<AlarmTrendDay[]>(() => {
    const stats = (statsQuery.data.value ?? []) as AlarmStatItem[]
    const countMap = new Map<string, number>()
    for (const s of stats) {
      const label = s.date.slice(5)
      countMap.set(label, (countMap.get(label) ?? 0) + Number(s.count))
    }
    const items = dayLabels.map((label) => ({ label, count: countMap.get(label) ?? 0, percent: 0 }))
    return withPercent(items)
  })

  /** 설비별 빈도 — 당일 기준 */
  const equipmentFrequency = computed<AlarmFrequencyItem[]>(() => {
    const list = (rawTodayQuery.data.value ?? []) as AlarmResponse[]
    if (!list.length) return []
    const counts = new Map<string, number>()
    for (const a of list) {
      const k = a.equipmentCode ?? '-'
      counts.set(k, (counts.get(k) ?? 0) + 1)
    }
    const items = [...counts.entries()]
      .map(([label, count]) => ({ label, count, type: '설비', percent: 0 }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 8)
    return withPercent(items)
  })

  /** 유형별 빈도 — 당일 기준 */
  const typeFrequency = computed<AlarmFrequencyItem[]>(() => {
    const list = (rawTodayQuery.data.value ?? []) as AlarmResponse[]
    if (!list.length) return []
    const counts = new Map<string, number>()
    for (const a of list) {
      const k = extractCategory(a.alarmType ?? '')
      counts.set(k, (counts.get(k) ?? 0) + 1)
    }
    const items = [...counts.entries()]
      .map(([label, count]) => ({ label, count, percent: 0 }))
      .sort((a, b) => b.count - a.count)
    return withPercent(items)
  })

  return {
    trendDays,
    equipmentFrequency,
    typeFrequency,
    rangeFrom: weekFrom,
    rangeTo: weekTo,
    isPending: computed(() => statsQuery.isPending.value || rawTodayQuery.isPending.value),
    isError: computed(() => statsQuery.isError.value || rawTodayQuery.isError.value),
  }
}
