import { api } from '@/api/client'
import type { DashboardSummary } from '@/types/dashboard'

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await api.get<DashboardSummary>('/api/dashboard/frontend')
  return data
}
