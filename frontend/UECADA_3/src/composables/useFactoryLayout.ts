import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchLines } from '@/api/lineApi'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

const FACTORY_ID = 'FACTORY-01'

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

  const statusesQuery = useQuery({
    queryKey: ['layout', 'equipment-statuses', equipIds],
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.factoryLayout,
    staleTime: 5_000,
  })

  return {
    lines: linesQuery.data,
    equipments: equipmentsQuery.data,
    statuses: statusesQuery.data,
    isPending: computed(() => linesQuery.isPending.value || equipmentsQuery.isPending.value),
    isError: computed(() => linesQuery.isError.value || equipmentsQuery.isError.value),
    error: computed(() => linesQuery.error.value ?? equipmentsQuery.error.value),
  }
}
