import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchEquipments } from '@/api/equipmentApi'
import { fetchLines } from '@/api/lineApi'
import { fetchSensorLatestValues, type SensorBufferLatest } from '@/api/sensorApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import type { Equipment } from '@/types/equipment'
import type { LineSummary } from '@/types/line'
import { realtimeBufferKey } from '@/utils/realtimeBuffers'

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

function derivedLineOee(line: LineSummary): number {
  if (line.latestOee != null) return Math.round(Number(line.latestOee))
  const total = line.equipmentTotal || 0
  if (!total) return 0
  const score = (
    (line.equipmentRunning * 1.0)
    + (line.equipmentStandby * 0.35)
    + (line.equipmentMaintenance * 0.15)
  ) / total * 100
  return Math.round(score)
}

function latestCycleMap(items: readonly SensorBufferLatest[]): Map<string, number> {
  const map = new Map<string, number>()
  for (const item of items) {
    const value = item.latest?.value
    if (typeof value === 'number' && Number.isFinite(value)) {
      map.set(item.bufferKey, value)
    }
  }
  return map
}

function cycleTime(equipment: Equipment, cycles: Map<string, number>): number | null {
  const key = realtimeBufferKey(equipment.equipmentCode, 'cycle_time')
  const value = key ? cycles.get(key) : null
  return typeof value === 'number' && value > 0 ? value : null
}

function lineCycleStats(line: LineSummary, equipments: readonly Equipment[], cycles: Map<string, number>) {
  const values = equipments
    .filter((equipment) => equipment.location === line.lineId)
    .map((equipment) => cycleTime(equipment, cycles))
    .filter((value): value is number => value != null)

  if (!values.length) {
    const productivity = derivedLineOee(line)
    return {
      balance: 0,
      stations: [0, 0, 0, 0, 0, 0],
      upmh: 0,
      uph: 0,
      productivity,
      upmhPercent: 0,
      uphPercent: 0,
    }
  }

  const min = Math.min(...values)
  const max = Math.max(...values)
  const avg = values.reduce((sum, value) => sum + value, 0) / values.length
  const balance = max > 0 ? Math.max(0, Math.round((min / max) * 100)) : 0
  const bottleneckCycle = max || avg
  const uph = bottleneckCycle > 0 ? Math.round(3600 / bottleneckCycle) : 0
  const upmh = uph * values.length
  const productivity = derivedLineOee(line)
  const stationPercents = values.slice(0, 6).map((value) => Math.max(8, Math.round((min / value) * 100)))

  return {
    balance,
    stations: [...stationPercents, ...Array(Math.max(0, 6 - stationPercents.length)).fill(0)].slice(0, 6),
    upmh,
    uph,
    productivity,
    upmhPercent: Math.min(100, productivity),
    uphPercent: Math.min(100, productivity),
  }
}

function toLineDetail(line: LineSummary, equipments: readonly Equipment[], cycles: Map<string, number>): LineDetailRow {
  const total = line.equipmentTotal || 0
  const run = pctRound(line.equipmentRunning, total)
  const stop = pctRound(line.equipmentAlarm, total)
  const wait = pctRound(line.equipmentStandby + line.equipmentMaintenance, total)
  const cycleStats = lineCycleStats(line, equipments, cycles)
  return {
    name: line.lineName ?? line.lineId,
    oee: derivedLineOee(line),
    equipment: total,
    status: { run, stop, wait, stopEnd: run + stop },
    ...cycleStats,
  }
}

export function useLineDetails() {
  const query = useQuery({
    queryKey: ['line-details', 'FACTORY-01'],
    queryFn: () => fetchLines('FACTORY-01'),
    refetchInterval: POLL_INTERVAL_MS.lineDetail,
    staleTime: 5_000,
  })

  const equipmentsQuery = useQuery({
    queryKey: ['line-details', 'equipments', 'FACTORY-01'],
    queryFn: () => fetchEquipments('FACTORY-01'),
    staleTime: 60_000,
  })

  const cycleKeys = computed(() =>
    [...new Set((equipmentsQuery.data.value ?? [])
      .map((equipment) => realtimeBufferKey(equipment.equipmentCode, 'cycle_time'))
      .filter((key): key is string => !!key))],
  )

  const cycleQuery = useQuery({
    queryKey: ['line-details', 'cycle-time-latest', cycleKeys],
    queryFn: () => fetchSensorLatestValues(cycleKeys.value),
    enabled: computed(() => cycleKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 1_000,
  })

  const cycles = computed(() => latestCycleMap(cycleQuery.data.value ?? []))
  const lines = computed<LineDetailRow[]>(() =>
    (query.data.value ?? []).map((line) =>
      toLineDetail(line, equipmentsQuery.data.value ?? [], cycles.value),
    ),
  )

  return { lines, isPending: query.isPending, isError: query.isError, error: query.error }
}
