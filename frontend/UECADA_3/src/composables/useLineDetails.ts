import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { fetchLines } from '@/api/lineApi'
import { fetchSensorLatestValues, type SensorBufferLatest } from '@/api/sensorApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import type { Equipment, EquipmentStatusCode } from '@/types/equipment'
import type { LineSummary } from '@/types/line'
import { realtimeBufferKey, type RealtimeMetric } from '@/utils/realtimeBuffers'

export interface LineEquipmentNode {
  id: string
  code: string
  name: string
  state: 'normal' | 'warn' | 'standby'
  cycle: number | null
  cycleLabel: string
  tempLabel: string
}

export interface LineProcessStage {
  key: 'casting' | 'machining' | 'washing' | 'assembly' | 'inspection'
  label: string
  nodes: LineEquipmentNode[]
}

export interface LineStationMetric {
  label: string
  value: number
  cycle: number | null
}

export interface LineDetailRow {
  id: string
  name: string
  oee: number
  equipment: number
  active: number
  alarm: number
  status: {
    run: number
    stop: number
    wait: number
    stopEnd: number
  }
  stages: LineProcessStage[]
  balance: number
  stations: LineStationMetric[]
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

const PROCESS_ORDER: readonly LineProcessStage[] = [
  { key: 'casting', label: '주조', nodes: [] },
  { key: 'machining', label: '가공', nodes: [] },
  { key: 'washing', label: '세척', nodes: [] },
  { key: 'assembly', label: '조립', nodes: [] },
  { key: 'inspection', label: '검사', nodes: [] },
]

function shortEquipmentCode(equipment: Equipment): string {
  return equipment.equipmentCode.split('_').pop() ?? equipment.equipmentCode
}

function processKey(equipment: Equipment): LineProcessStage['key'] {
  const type = equipment.processType ?? ''
  const code = shortEquipmentCode(equipment)
  if (type.includes('주조') || code.startsWith('CAST')) return 'casting'
  if (type.includes('가공') || code.startsWith('CNC')) return 'machining'
  if (type.includes('세척') || code.startsWith('WASH')) return 'washing'
  if (type.includes('조립') || code.startsWith('ASSY')) return 'assembly'
  if (type.includes('검사') || code.startsWith('TEST')) return 'inspection'
  return 'machining'
}

function equipmentState(status: EquipmentStatusCode | undefined): LineEquipmentNode['state'] {
  if (status === 'ALARM') return 'warn'
  if (status === 'STANDBY' || status === 'MAINTENANCE') return 'standby'
  return 'normal'
}

function latestValueMap(items: readonly SensorBufferLatest[]): Map<string, number> {
  const map = new Map<string, number>()
  for (const item of items) {
    const value = item.latest?.value
    if (typeof value === 'number' && Number.isFinite(value)) {
      map.set(item.bufferKey, value)
    }
  }
  return map
}

function metricValue(equipment: Equipment, values: Map<string, number>, metric: RealtimeMetric): number | null {
  const key = realtimeBufferKey(equipment.equipmentCode, metric)
  const value = key ? values.get(key) : null
  return typeof value === 'number' && value > 0 ? value : null
}

function processStages(
  line: LineSummary,
  equipments: readonly Equipment[],
  values: Map<string, number>,
  statuses: Map<string, EquipmentStatusCode>,
): LineProcessStage[] {
  const lineEquipments = equipments.filter((equipment) => equipment.location === line.lineId)
  return PROCESS_ORDER.map((process) => ({
    ...process,
    nodes: lineEquipments
      .filter((equipment) => processKey(equipment) === process.key)
      .sort((a, b) => shortEquipmentCode(a).localeCompare(shortEquipmentCode(b)))
      .map((equipment) => {
        const cycle = metricValue(equipment, values, 'cycle_time')
        const temperature = metricValue(equipment, values, 'sensor_temperature')
        return {
          id: equipment.equipmentCode,
          code: shortEquipmentCode(equipment),
          name: equipment.equipmentName,
          state: equipmentState(statuses.get(equipment.equipmentCode)),
          cycle,
          cycleLabel: cycle == null ? 'CT 대기' : `CT ${cycle.toFixed(1)}s`,
          tempLabel: temperature == null ? '온도 대기' : `${temperature.toFixed(1)}°C`,
        }
      }),
  }))
}

function lineCycleStats(stages: readonly LineProcessStage[], activeEquipmentCount: number, oee: number) {
  const processCycles = stages.map((stage) => {
    const values = stage.nodes.map((node) => node.cycle).filter((value): value is number => value != null)
    const avg = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
    return { label: stage.label, value: avg }
  })
  const values = processCycles.map((item) => item.value).filter((value): value is number => value != null)

  if (!values.length) {
    return {
      balance: 0,
      stations: processCycles.map((item) => ({ label: item.label, value: 0, cycle: item.value })),
      upmh: 0,
      uph: 0,
      productivity: oee,
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
  const upmh = uph * Math.max(1, activeEquipmentCount)
  const productivity = oee
  const stations = processCycles.map((item) => ({
    label: item.label,
    value: item.value == null ? 0 : Math.max(8, Math.round((min / item.value) * 100)),
    cycle: item.value,
  }))

  return {
    balance,
    stations,
    upmh,
    uph,
    productivity,
    upmhPercent: Math.min(100, productivity),
    uphPercent: Math.min(100, productivity),
  }
}

function toLineDetail(
  line: LineSummary,
  equipments: readonly Equipment[],
  values: Map<string, number>,
  statuses: Map<string, EquipmentStatusCode>,
): LineDetailRow {
  const total = line.equipmentTotal || 0
  const run = pctRound(line.equipmentRunning, total)
  const stop = pctRound(line.equipmentAlarm, total)
  const wait = pctRound(line.equipmentStandby + line.equipmentMaintenance, total)
  const oee = derivedLineOee(line)
  const stages = processStages(line, equipments, values, statuses)
  const cycleStats = lineCycleStats(stages, line.equipmentRunning, oee)
  return {
    id: line.lineId,
    name: line.lineName ?? line.lineId,
    oee,
    equipment: total,
    active: line.equipmentRunning,
    alarm: line.equipmentAlarm,
    status: { run, stop, wait, stopEnd: run + stop },
    stages,
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

  const equipmentCodes = computed(() => (equipmentsQuery.data.value ?? []).map((equipment) => equipment.equipmentCode))

  const statusQuery = useQuery({
    queryKey: ['line-details', 'equipment-statuses', equipmentCodes],
    queryFn: () => fetchEquipmentStatuses(equipmentCodes.value),
    enabled: computed(() => equipmentCodes.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentCategory,
    staleTime: 5_000,
  })

  const realtimeKeys = computed(() =>
    [...new Set((equipmentsQuery.data.value ?? [])
      .flatMap((equipment) => [
        realtimeBufferKey(equipment.equipmentCode, 'cycle_time'),
        realtimeBufferKey(equipment.equipmentCode, 'sensor_temperature'),
      ])
      .filter((key): key is string => !!key))],
  )

  const realtimeQuery = useQuery({
    queryKey: ['line-details', 'realtime-latest', realtimeKeys],
    queryFn: () => fetchSensorLatestValues(realtimeKeys.value),
    enabled: computed(() => realtimeKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 1_000,
  })

  const realtimeValues = computed(() => latestValueMap(realtimeQuery.data.value ?? []))
  const statuses = computed(() =>
    new Map((statusQuery.data.value ?? []).map((status) => [status.equipId, status.statusCode])),
  )
  const lines = computed<LineDetailRow[]>(() =>
    (query.data.value ?? []).map((line) =>
      toLineDetail(line, equipmentsQuery.data.value ?? [], realtimeValues.value, statuses.value),
    ),
  )

  return { lines, isPending: query.isPending, isError: query.isError, error: query.error }
}
