import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchLines } from '@/api/lineApi'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { fetchSensorLatestValues, type SensorBufferLatest, type SensorFrame } from '@/api/sensorApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

const FACTORY_ID = 'FACTORY-01'
export type FactoryRealtimeMetric = 'cycle_time' | 'sensor_current' | 'sensor_voltage' | 'sensor_temperature' | 'sensor_vibration'

const REALTIME_METRICS: readonly FactoryRealtimeMetric[] = [
  'cycle_time',
  'sensor_current',
  'sensor_voltage',
  'sensor_temperature',
  'sensor_vibration',
] as const

export function realtimeBufferKey(equipmentCode: string, metric: FactoryRealtimeMetric): string | null {
  const match = equipmentCode.match(/^(LINE-\d{2})_(.+)$/)
  if (!match) return null
  const line = match[1].replace('-', '')
  const equipment = match[2].replace(/-/g, '')
  return `${line}.${equipment}:${metric}`
}

function realtimeKeys(equipments: readonly { equipmentCode: string }[]): string[] {
  const keys = equipments.flatMap((equipment) =>
    REALTIME_METRICS.map((metric) => realtimeBufferKey(equipment.equipmentCode, metric)).filter((key): key is string => !!key),
  )
  return [...new Set(keys)]
}

function latestFrameMap(items: readonly SensorBufferLatest[]): Map<string, SensorFrame> {
  const map = new Map<string, SensorFrame>()
  for (const item of items) {
    if (item.latest) map.set(item.bufferKey, item.latest)
  }
  return map
}

export function useFactoryLayout() {
  const linesQuery = useQuery({
    queryKey: ['layout', 'lines', FACTORY_ID],
    queryFn: () => fetchLines(FACTORY_ID),
    refetchInterval: POLL_INTERVAL_MS.factoryLayout,
    staleTime: 5_000,
  })

  const equipmentsQuery = useQuery({
    queryKey: ['layout', 'equipments', FACTORY_ID],
    queryFn: () => fetchEquipments(FACTORY_ID),
    staleTime: STALE_TIME_MS.long,
  })

  const equipIds = computed(() => (equipmentsQuery.data.value ?? []).map((e) => e.equipmentCode))
  const sensorKeys = computed(() => realtimeKeys(equipmentsQuery.data.value ?? []))

  const statusesQuery = useQuery({
    queryKey: ['layout', 'equipment-statuses', equipIds],
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.factoryLayout,
    staleTime: 5_000,
  })

  const realtimeQuery = useQuery({
    queryKey: ['layout', 'equipment-realtime-latest', sensorKeys],
    queryFn: () => fetchSensorLatestValues(sensorKeys.value),
    enabled: computed(() => sensorKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 1_000,
  })

  return {
    lines: linesQuery.data,
    equipments: equipmentsQuery.data,
    statuses: statusesQuery.data,
    realtime: computed(() => latestFrameMap(realtimeQuery.data.value ?? [])),
    isPending: computed(() => linesQuery.isPending.value || equipmentsQuery.isPending.value),
    isError: computed(() => linesQuery.isError.value || equipmentsQuery.isError.value),
    error: computed(() => linesQuery.error.value ?? equipmentsQuery.error.value),
  }
}
