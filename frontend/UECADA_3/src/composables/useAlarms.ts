import { useQuery } from '@tanstack/vue-query'
import { fetchAlarmList } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'

// 운영 환경에서는 polling.ts 의 alarm 값을 5_000 이상으로 늘리는 것을 권장.
export function useAlarms() {
  return useQuery({
    queryKey: ['alarms', 'list'],
    queryFn: fetchAlarmList,
    refetchInterval: POLL_INTERVAL_MS.alarm,
    refetchIntervalInBackground: true,
  })
}
