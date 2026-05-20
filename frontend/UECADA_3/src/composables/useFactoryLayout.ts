import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchLines } from '@/api/lineApi'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { fetchSensorLatestValues, type SensorBufferLatest, type SensorFrame } from '@/api/sensorApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'
import {
  realtimeBufferKey,
  realtimeKeysForEquipments,
  type RealtimeMetric,
} from '@/utils/realtimeBuffers'

const FACTORY_ID = 'FACTORY-01'
export { realtimeBufferKey }
export type FactoryRealtimeMetric = RealtimeMetric

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
  const sensorKeys = computed(() => realtimeKeysForEquipments(equipmentsQuery.data.value ?? []))

  const statusesQuery = useQuery({
    queryKey: computed(() => ['layout', 'equipment-statuses', equipIds.value]),
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.factoryLayout,
    staleTime: 5_000,
  })

  const realtimeQuery = useQuery({
    queryKey: computed(() => ['layout', 'equipment-realtime-latest', sensorKeys.value]),
    queryFn: () => fetchSensorLatestValues(sensorKeys.value),
    enabled: computed(() => sensorKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 0,
    refetchIntervalInBackground: true,
  })

  const realtimeUpdatedAt = computed(() => realtimeQuery.dataUpdatedAt.value)

  const isRealtimeStale = computed(() => {
    const at = realtimeUpdatedAt.value
    if (!at) return false
    return Date.now() - at > POLL_INTERVAL_MS.equipmentRealtime * 2
  })

  return {
    lines: linesQuery.data,
    equipments: equipmentsQuery.data,
    statuses: statusesQuery.data,
    realtime: computed(() => latestFrameMap(realtimeQuery.data.value ?? [])),
    realtimeUpdatedAt,
    isRealtimeFetching: computed(() => realtimeQuery.isFetching.value),
    isRealtimeStale,
    isPending: computed(() => linesQuery.isPending.value || equipmentsQuery.isPending.value),
    isError: computed(() => linesQuery.isError.value || equipmentsQuery.isError.value),
    error: computed(() => linesQuery.error.value ?? equipmentsQuery.error.value),
  }
}
