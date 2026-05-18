import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchLines } from '@/api/lineApi'
import type { LineSummary } from '@/types/line'
import { POLL_INTERVAL_MS } from '@/constants/polling'

export interface LineDetailRow {
  lineId: string
  name: string
  oee: number
  equipment: number
  status: {
    run: number
    stop: number
    wait: number
    stopEnd: number
  }
  balance: number
  stations: number[]
  upmh: number
  uph: number
  productivity: number
  upmhPercent: number
  uphPercent: number
}

function pctRound(value: number, total: number): number {
  if (!total) return 0
  return Math.round((value / total) * 100)
}

function upmhPercent(upmh: number): number {
  if (upmh <= 0) return 0
  return Math.min(100, Math.round(upmh / 15))
}

function uphPercent(uph: number): number {
  if (uph <= 0) return 0
  return Math.min(100, Math.round(uph / 6))
}

function toLineDetail(line: LineSummary): LineDetailRow {
  const total = line.equipmentTotal || 0
  const run = pctRound(line.equipmentRunning, total)
  const stop = pctRound(line.equipmentAlarm, total)
  const wait = pctRound(line.equipmentStandby + line.equipmentMaintenance, total)
  const upmh = line.upmh ?? 0
  const uph = line.uph ?? 0
  const stations =
    line.stationUtilization?.length ? [...line.stationUtilization] : [0, 0, 0, 0, 0, 0]

  return {
    lineId: line.lineId,
    name: line.lineName ?? line.lineId,
    oee: line.latestOee == null ? 0 : Math.round(Number(line.latestOee)),
    equipment: total,
    status: { run, stop, wait, stopEnd: run + stop },
    balance: line.balanceRate ?? 0,
    stations,
    upmh,
    uph,
    productivity: line.productivity ?? 0,
    upmhPercent: upmhPercent(upmh),
    uphPercent: uphPercent(uph),
  }
}

export function useLineDetails() {
  const query = useQuery({
    queryKey: ['line-details', 'FACTORY-01'],
    queryFn: () => fetchLines('FACTORY-01'),
    refetchInterval: POLL_INTERVAL_MS.lineDetail,
    staleTime: 5_000,
  })

  const lines = computed<LineDetailRow[]>(() => (query.data.value ?? []).map(toLineDetail))

  return { lines, isPending: query.isPending, isError: query.isError, error: query.error }
}
