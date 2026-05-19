import { useQuery } from '@tanstack/vue-query'
import { fetchAlarmsRaw, toAlarmListResponse } from '@/api/alarmApi'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

/**
 * 알람 raw 목록 쿼리. AlarmPage·useAlarmInsights 양쪽에서 동일한 캐시 키를 공유한다.
 * - 백그라운드 폴링은 비활성화하여 숨김 탭에서 불필요한 트래픽이 발생하지 않게 한다.
 */
export function useAlarmsRaw() {
  return useQuery({
    queryKey: ['alarms', 'list'],
    queryFn: fetchAlarmsRaw,
    refetchInterval: POLL_INTERVAL_MS.alarm,
    refetchIntervalInBackground: false,
    staleTime: STALE_TIME_MS.short,
  })
}

/**
 * AlarmPage 가 사용하는 summary + rows 변환 뷰.
 * Vue Query 의 select 옵션으로 변환하면 구조적 공유가 적용되어
 * 동일 데이터에 대해 안정된 참조가 유지된다 → 불필요한 자식 렌더 감소.
 */
export function useAlarms() {
  return useQuery({
    queryKey: ['alarms', 'list'],
    queryFn: fetchAlarmsRaw,
    refetchInterval: POLL_INTERVAL_MS.alarm,
    refetchIntervalInBackground: false,
    staleTime: STALE_TIME_MS.short,
    select: toAlarmListResponse,
  })
}
