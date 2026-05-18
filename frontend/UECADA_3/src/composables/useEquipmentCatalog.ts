import { computed, type Component } from 'vue'
import { useQueries, useQuery } from '@tanstack/vue-query'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { fetchAnalysisResults } from '@/api/analysisApi'
import type { Equipment, EquipmentStatusCode, EquipmentStatusItem } from '@/types/equipment'
import type { AnalysisResult } from '@/types/analysis'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'

export type EquipmentState = '운전' | '정지' | '대기' | '점검'

export interface EquipmentCommonMetric {
  label: string
  value: string
}

export interface EquipmentSpecificMetric {
  label: string
  value: string
  status: string
}

export interface EquipmentDetailItem {
  id: string
  name: string
  line: string
  state: EquipmentState
  rate: number
  defects: number
  operator: string
  cycle: string
  common: EquipmentCommonMetric[]
  specific: EquipmentSpecificMetric[]
}

export interface EquipmentCategory {
  id: string
  name: string
  icon: Component | null
  status: string
  count: number
  running: number
  stopped: number
  waiting: number
  avgRate: number
  defectCount: number
  description: string
  equipment: EquipmentDetailItem[]
}

export interface CategoryDefinition {
  id: string
  name: string
  processType: string
  description: string
  iconKey: 'flame' | 'cog' | 'droplets' | 'wrench' | 'search'
}

export const CATEGORY_DEFINITIONS: readonly CategoryDefinition[] = [
  { id: 'casting', name: '주조기', processType: '주조', description: '주조 공정 설비 모니터링', iconKey: 'flame' },
  { id: 'machining', name: '가공기', processType: '가공', description: '절삭·가공 공정 설비 모니터링', iconKey: 'cog' },
  { id: 'washing', name: '세척기', processType: '세척', description: '세척 공정 설비 모니터링', iconKey: 'droplets' },
  { id: 'assembly', name: '조립기', processType: '조립', description: '조립 공정 설비 모니터링', iconKey: 'wrench' },
  { id: 'inspection', name: '검사기', processType: '검사', description: '검사 공정 설비 모니터링', iconKey: 'search' },
] as const

function statusCodeToState(code: EquipmentStatusCode | undefined): EquipmentState {
  if (code === 'ALARM') return '정지'
  if (code === 'MAINTENANCE') return '점검'
  if (code === 'STANDBY') return '대기'
  return '운전'
}

function categoryStatusLabel(running: number, stopped: number): string {
  if (stopped > 0) return '이상'
  if (running === 0) return '대기'
  return '정상'
}

function formatNumber(value: number | null | undefined, digits = 2): string {
  if (value == null || Number.isNaN(value)) return '-'
  return Number(value).toFixed(digits)
}

function alarmLevelLabel(level: string | null | undefined): string {
  if (!level) return '-'
  const v = level.toLowerCase()
  if (v === 'danger') return '위험'
  if (v === 'warning') return '경고'
  if (v === 'normal') return '정상'
  return level
}

function formatCycle(sec: number | null | undefined): string {
  if (sec == null || sec <= 0) return '-'
  return `${sec.toFixed(1)}s`
}

function buildCommon(equipment: Equipment): EquipmentCommonMetric[] {
  const metrics: EquipmentCommonMetric[] = [
    { label: '설비코드', value: equipment.equipmentCode },
    { label: '라인', value: equipment.location ?? '-' },
    { label: '모델', value: equipment.model ?? '-' },
    { label: '설치일', value: equipment.installDate ?? '-' },
  ]
  if (equipment.currentAmp != null) {
    metrics.push({ label: '전류', value: `${equipment.currentAmp.toFixed(1)} A` })
  }
  if (equipment.temperatureC != null) {
    metrics.push({ label: '온도', value: `${equipment.temperatureC.toFixed(1)} ℃` })
  }
  if (equipment.humidityPct != null) {
    metrics.push({ label: '습도', value: `${equipment.humidityPct.toFixed(1)} %` })
  }
  if (equipment.vibrationMmS != null) {
    metrics.push({ label: '진동', value: `${equipment.vibrationMmS.toFixed(2)} mm/s` })
  }
  return metrics
}

function buildSpecific(analysis: AnalysisResult | null): EquipmentSpecificMetric[] {
  if (!analysis) return [{ label: '분석 결과', value: '데이터 없음', status: '백엔드 분석 결과 미수신' }]

  const items: EquipmentSpecificMetric[] = []
  if (analysis.prediction) {
    items.push({ label: '예측', value: analysis.prediction, status: `모델 ${analysis.modelVersion ?? '-'}` })
  }
  if (analysis.alarmLevel) {
    items.push({ label: '알람 레벨', value: alarmLevelLabel(analysis.alarmLevel), status: analysis.modelStatus ?? '-' })
  }
  if (analysis.confidence != null) {
    items.push({ label: '신뢰도', value: `${formatNumber(analysis.confidence * 100, 1)}%`, status: '모델 confidence' })
  }
  if (analysis.anomalyScore != null) {
    items.push({ label: 'Anomaly Score', value: formatNumber(analysis.anomalyScore, 3), status: '낮을수록 정상' })
  }
  if (analysis.rms != null) {
    items.push({ label: 'RMS', value: formatNumber(analysis.rms, 3), status: '진동 신호 RMS' })
  }
  if (analysis.peakFrequency != null) {
    items.push({ label: 'Peak Freq', value: `${formatNumber(analysis.peakFrequency, 2)}Hz`, status: '주요 주파수' })
  }
  if (analysis.kurtosis != null) {
    items.push({ label: 'Kurtosis', value: formatNumber(analysis.kurtosis, 3), status: '신호 첨도' })
  }
  if (analysis.crestFactor != null) {
    items.push({ label: 'Crest Factor', value: formatNumber(analysis.crestFactor, 3), status: '피크 대 RMS' })
  }

  return items.length ? items : [{ label: '분석 결과', value: '항목 없음', status: '-' }]
}

export function useEquipmentCatalog() {
  const equipmentsQuery = useQuery({
    queryKey: ['equipments', 'FACTORY-01'],
    queryFn: () => fetchEquipments('FACTORY-01'),
    staleTime: STALE_TIME_MS.long,
  })

  const equipIds = computed(() => (equipmentsQuery.data.value ?? []).map((e) => e.equipmentCode))

  const statusQuery = useQuery({
    queryKey: ['equipment-statuses', equipIds],
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentCategory,
    staleTime: 5_000,
  })

  const analysisQueries = useQueries({
    queries: computed(() =>
      equipIds.value.map((code) => ({
        queryKey: ['analysis-results', code, 'latest'],
        queryFn: () => fetchAnalysisResults({ equipmentCode: code, limit: 1 }),
        staleTime: STALE_TIME_MS.medium,
        refetchInterval: POLL_INTERVAL_MS.equipmentAnalysis,
      })),
    ),
  })

  const analysisMap = computed(() => {
    const map = new Map<string, AnalysisResult | null>()
    equipIds.value.forEach((code, idx) => {
      const data = analysisQueries.value[idx]?.data
      const list = (data as AnalysisResult[] | undefined) ?? []
      map.set(code, list[0] ?? null)
    })
    return map
  })

  const statusMap = computed(() => {
    const map = new Map<string, EquipmentStatusCode>()
    for (const item of (statusQuery.data.value ?? []) as EquipmentStatusItem[]) {
      map.set(item.equipId, item.statusCode)
    }
    return map
  })

  const categories = computed<EquipmentCategory[]>(() => {
    const equipments = (equipmentsQuery.data.value ?? []) as Equipment[]
    const sMap = statusMap.value
    const aMap = analysisMap.value

    return CATEGORY_DEFINITIONS.map((def) => {
      const inCategory = equipments.filter((e) => e.processType === def.processType)
      const items: EquipmentDetailItem[] = inCategory.map((e) => {
        const code = sMap.get(e.equipmentCode)
        const state = statusCodeToState(code)
        const analysis = aMap.get(e.equipmentCode) ?? null
        return {
          id: e.equipmentCode,
          name: e.equipmentName,
          line: e.location ?? '-',
          state,
          rate: e.utilizationRate ?? 0,
          defects: e.defectCount ?? 0,
          operator: e.operatorName ?? '-',
          cycle: formatCycle(e.cycleTimeSec),
          common: buildCommon(e),
          specific: buildSpecific(analysis),
        }
      })

      const running = items.filter((it) => it.state === '운전').length
      const stopped = items.filter((it) => it.state === '정지').length
      const waiting = items.filter((it) => it.state === '대기' || it.state === '점검').length
      const avgRate =
        items.length > 0
          ? Math.round(items.reduce((sum, it) => sum + it.rate, 0) / items.length)
          : 0
      const defectCount = items.reduce((sum, it) => sum + it.defects, 0)

      return {
        id: def.id,
        name: def.name,
        icon: null,
        status: categoryStatusLabel(running, stopped),
        count: items.length,
        running,
        stopped,
        waiting,
        avgRate,
        defectCount,
        description: def.description,
        equipment: items,
      }
    })
  })

  return {
    categories,
    isPending: equipmentsQuery.isPending,
    isError: equipmentsQuery.isError,
    error: equipmentsQuery.error,
  }
}
