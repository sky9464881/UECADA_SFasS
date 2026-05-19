import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { resolveAlarm, type AlarmResolvePayload, type AlarmResponse } from '@/api/alarmApi'

export interface ResolveAlarmArgs {
  alarmId: number
  payload: AlarmResolvePayload
}

const RAW_KEY = ['alarms', 'list'] as const

export function useResolveAlarm() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ alarmId, payload }: ResolveAlarmArgs) => resolveAlarm(alarmId, payload),
    /**
     * 낙관적 업데이트 — 서버 응답 전에 캐시의 해당 알람을 RESOLVED 로 마킹.
     * 폴링/refetch 비용을 줄이고, UI 응답성을 개선.
     */
    onMutate: async ({ alarmId, payload }) => {
      await queryClient.cancelQueries({ queryKey: RAW_KEY })
      const prev = queryClient.getQueryData<AlarmResponse[]>([...RAW_KEY])
      if (prev) {
        const next = prev.map((a) =>
          a.alarmId === alarmId
            ? {
                ...a,
                status: 'RESOLVED',
                resolvedBy: payload.resolvedBy,
                resolvedAt: payload.resolvedAt ?? null,
                comment: payload.comment ?? null,
              }
            : a,
        )
        queryClient.setQueryData([...RAW_KEY], next)
      }
      return { prev }
    },
    onError: (_err, _vars, context) => {
      if (context?.prev) {
        queryClient.setQueryData([...RAW_KEY], context.prev)
      }
    },
    onSettled: () => {
      // 통계는 서버 집계가 필요하므로 invalidate, 목록은 캐시만 갱신했으므로 가벼운 refetch
      queryClient.invalidateQueries({ queryKey: ['alarm-stats'] })
      queryClient.invalidateQueries({ queryKey: RAW_KEY })
    },
  })
}
