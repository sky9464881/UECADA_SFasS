import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { extractCategory, fetchAlarmStats } from '@/api/alarmApi'
import type { AlarmResponse, AlarmStatItem } from '@/api/alarmApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'
import { useAlarmsRaw } from '@/composables/useAlarms'

export interface AlarmTrendDay {
  /** YYYY-MM-DD */
  date: string
  /** 축 라벨용 MM-DD (빈 문자열이면 라벨 숨김) */
  label: string
  count: number
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

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/**
 * 추이 통계용 범위. queryKey 가 매 분/초마다 바뀌면 캐시가 미스나므로
 * 하루 단위(YYYY-MM-DD)로 고정해 같은 날 동안 캐시 히트되도록 한다.
 * days=30 이면 오늘 포함 정확히 30개 일자를 만든다.
 */
function defaultRange(days = 30): { from: string; to: string } {
  const today = new Date()
  const from = new Date(today)
  from.setDate(from.getDate() - (days - 1))
  return { from: `${isoDate(from)}T00:00:00`, to: `${isoDate(today)}T23:59:59` }
}

function withPercent<T extends { count: number }>(items: T[]): (T & { percent: number })[] {
  const max = items.reduce((m, it) => Math.max(m, it.count), 0)
  return items.map((it) => ({ ...it, percent: max ? Math.round((it.count / max) * 100) : 0 }))
}

/** 'YYYY-MM-DD' 문자열에 days 만큼 더한 새 ISO 날짜 반환. */
function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setDate(dt.getDate() + days)
  return isoDate(dt)
}

export function useAlarmInsights() {
  const { from, to } = defaultRange(30)

  const statsQuery = useQuery({
    queryKey: ['alarm-stats', from, to],
    queryFn: () => fetchAlarmStats(from, to),
    refetchInterval: POLL_INTERVAL_MS.alarmInsights,
    refetchIntervalInBackground: false,
    staleTime: STALE_TIME_MS.medium,
  })

  // /api/alarms 는 useAlarms 에서 이미 폴링 중이므로 동일 캐시 키를 공유한다.
  const rawQuery = useAlarmsRaw()

  /**
   * 일별 발생 건수 추이 (정확히 30일).
   * - 백엔드 stats 를 date 기준 합산
   * - 누락 날짜는 0건으로 채워 연속 시계열 유지
   * - 막대 높이·축·툴팁은 AlarmPage 의 ECharts 가 담당
   */
  const trendDays = computed<AlarmTrendDay[]>(() => {
    const stats = (statsQuery.data.value ?? []) as AlarmStatItem[]
    const byDate = new Map<string, number>()
    for (const s of stats) {
      byDate.set(s.date, (byDate.get(s.date) ?? 0) + Number(s.count))
    }

    const fromDay = from.slice(0, 10)
    const toDay = to.slice(0, 10)
    const filled: { date: string; count: number }[] = []
    for (let d = fromDay; d <= toDay; d = addDays(d, 1)) {
      filled.push({ date: d, count: byDate.get(d) ?? 0 })
    }

    const n = filled.length
    const step = n > 14 ? Math.ceil(n / 7) : 1
    return filled.map((it, idx) => {
      const showLabel = idx === 0 || idx === n - 1 || idx % step === 0
      return {
        date: it.date,
        label: showLabel ? it.date.slice(5) : '',
        count: it.count,
      }
    })
  })

  const trendHasData = computed(() => trendDays.value.some((d) => d.count > 0))

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
    trendHasData,
    equipmentFrequency,
    typeFrequency,
    rangeFrom: from,
    rangeTo: to,
    isPending: computed(() => statsQuery.isPending.value || rawQuery.isPending.value),
    isError: computed(() => statsQuery.isError.value || rawQuery.isError.value),
    isTrendPending: computed(() => statsQuery.isPending.value),
  }
}
