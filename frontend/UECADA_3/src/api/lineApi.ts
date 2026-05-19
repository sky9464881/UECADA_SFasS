import { api } from '@/api/client'
import type { LineSummary } from '@/types/line'

export async function fetchLines(factoryId?: string): Promise<LineSummary[]> {
  const { data } = await api.get<LineSummary[]>('/api/lines', {
    params: factoryId ? { factoryId } : undefined,
  })
  return data
}
