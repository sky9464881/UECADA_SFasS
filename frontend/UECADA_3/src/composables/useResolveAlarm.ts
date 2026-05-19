import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { resolveAlarm, type AlarmResolvePayload } from '@/api/alarmApi'

export interface ResolveAlarmArgs {
  alarmId: number
  payload: AlarmResolvePayload
}

export function useResolveAlarm() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ alarmId, payload }: ResolveAlarmArgs) => resolveAlarm(alarmId, payload),
    onSuccess: () => {
      // 알람 목록·통계·빈도 모두 갱신.
      queryClient.invalidateQueries({ queryKey: ['alarms'] })
      queryClient.invalidateQueries({ queryKey: ['alarm-stats'] })
    },
  })
}
