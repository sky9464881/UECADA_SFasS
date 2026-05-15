import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { extractCategory, fetchAlarmStats, fetchAlarmsRaw } from '@/api/alarmApi'
import type { AlarmResponse, AlarmStatItem } from '@/api/alarmApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

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

function defaultRange(days = 30): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - days)
  from.setHours(0, 0, 0, 0)
  return { from: toLocalIso(from), to: toLocalIso(to) }
}

function withPercent<T extends { count: number }>(items: T[]): (T & { percent: number })[] {
  const max = items.reduce((m, it) => Math.max(m, it.count), 0)
  return items.map((it) => ({ ...it, percent: max ? Math.round((it.count / max) * 100) : 0 }))
}

export function useAlarmInsights() {
  const { from, to } = defaultRange(30)

  const statsQuery = useQuery({
    queryKey: ['alarm-stats', from, to],
    queryFn: () => fetchAlarmStats(from, to),
    refetchInterval: POLL_INTERVAL_MS.alarmInsights,
    staleTime: STALE_TIME_MS.medium,
  })

  const rawQuery = useQuery({
    queryKey: ['alarms', 'raw'],
    queryFn: fetchAlarmsRaw,
    refetchInterval: POLL_INTERVAL_MS.alarmInsights,
    staleTime: STALE_TIME_MS.medium,
  })

  /** 일별 발생 건수 추이 — 백엔드 stats(GROUP BY date, alarmType) 를 date 기준으로 합산 */
  const trendDays = computed<AlarmTrendDay[]>(() => {
    const stats = (statsQuery.data.value ?? []) as AlarmStatItem[]
    if (!stats.length) return []
    const byDate = new Map<string, number>()
    for (const s of stats) {
      byDate.set(s.date, (byDate.get(s.date) ?? 0) + Number(s.count))
    }
    const sorted = [...byDate.entries()].sort(([a], [b]) => a.localeCompare(b))
    const items = sorted.map(([date, count]) => ({
      label: date.slice(5),
      count,
      percent: 0,
    }))
    return withPercent(items)
  })

  /** 설비별 빈도 — 원본 알람 목록에서 equipmentCode 그룹화 */
  const equipmentFrequency = computed<AlarmFrequencyItem[]>(() => {
    const list = (rawQuery.data.value ?? []) as AlarmResponse[]
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

  /** 유형별 빈도 — 백엔드 stats 를 alarmType 기준으로 합산 (`'온도 이상'` → `'온도'`) */
  const typeFrequency = computed<AlarmFrequencyItem[]>(() => {
    const stats = (statsQuery.data.value ?? []) as AlarmStatItem[]
    if (!stats.length) return []
    const counts = new Map<string, number>()
    for (const s of stats) {
      const k = extractCategory(s.alarmType)
      counts.set(k, (counts.get(k) ?? 0) + Number(s.count))
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
    rangeFrom: from,
    rangeTo: to,
    isPending: computed(() => statsQuery.isPending.value || rawQuery.isPending.value),
    isError: computed(() => statsQuery.isError.value || rawQuery.isError.value),
  }
}
