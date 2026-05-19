import type { Ref } from 'vue'
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchAlarmList } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'

export function useAlarms(statusFilter?: Ref<string | null>) {
  return useQuery({
    queryKey: computed(() => ['alarms', 'list', statusFilter?.value ?? null]),
    queryFn: () => fetchAlarmList(statusFilter?.value ?? null),
    refetchInterval: POLL_INTERVAL_MS.alarm,
    refetchIntervalInBackground: true,
  })
}
