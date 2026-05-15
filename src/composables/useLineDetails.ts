import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchLines } from '@/api/lineApi'
import type { LineSummary } from '@/types/line'
import { POLL_INTERVAL_MS } from '@/constants/polling'

export interface LineDetailRow {
  name: string
  oee: number
  equipment: number
  status: {
    run: number
    stop: number
    wait: number
    stopEnd: number
  }
  /** 백엔드 미제공 — 0 placeholder */
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

function toLineDetail(line: LineSummary): LineDetailRow {
  const total = line.equipmentTotal || 0
  const run = pctRound(line.equipmentRunning, total)
  const stop = pctRound(line.equipmentAlarm, total)
  const wait = pctRound(line.equipmentStandby + line.equipmentMaintenance, total)
  return {
    name: line.lineName ?? line.lineId,
    oee: line.latestOee == null ? 0 : Math.round(Number(line.latestOee)),
    equipment: total,
    status: { run, stop, wait, stopEnd: run + stop },
    // 백엔드 미제공 영역: 라인밸런싱, UPMH/UPH, 생산성.
    // TODO: backend 가 station-level 시계열 / UPMH 제공 시 매핑.
    balance: 0,
    stations: [0, 0, 0, 0, 0, 0],
    upmh: 0,
    uph: 0,
    productivity: 0,
    upmhPercent: 0,
    uphPercent: 0,
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
