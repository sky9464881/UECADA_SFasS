import { useQuery } from '@tanstack/vue-query'
import { fetchDashboardSummary } from '@/api/dashboardApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: fetchDashboardSummary,
    refetchInterval: POLL_INTERVAL_MS.dashboard,
    staleTime: 0,
    refetchIntervalInBackground: true,
  })
}
