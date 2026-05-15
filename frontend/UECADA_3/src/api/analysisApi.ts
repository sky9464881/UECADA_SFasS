import { api } from '@/api/client'
import type { AnalysisResult } from '@/types/analysis'

export interface FetchAnalysisResultsParams {
  equipmentCode: string
  limit?: number
  analysisType?: string
}

export async function fetchAnalysisResults(
  params: FetchAnalysisResultsParams,
): Promise<AnalysisResult[]> {
  const { equipmentCode, limit, analysisType } = params
  const query: Record<string, string | number> = {}
  if (limit != null) query.limit = limit
  if (analysisType) query.analysisType = analysisType
  const { data } = await api.get<AnalysisResult[]>(
    `/api/equipments/${encodeURIComponent(equipmentCode)}/analysis-results`,
    { params: query },
  )
  return data
}
