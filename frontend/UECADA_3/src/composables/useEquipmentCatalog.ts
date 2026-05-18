import { computed, type Component } from 'vue'
import { useQueries, useQuery } from '@tanstack/vue-query'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { fetchAnalysisResults } from '@/api/analysisApi'
import { fetchSensorLatestValues, type SensorBufferLatest, type SensorFrame } from '@/api/sensorApi'
import type { Equipment, EquipmentStatusCode, EquipmentStatusItem } from '@/types/equipment'
import type { AnalysisResult } from '@/types/analysis'
import { POLL_INTERVAL_MS, STALE_TIME_MS } from '@/constants/polling'
import {
  MONITORING_REALTIME_METRICS,
  processRealtimeMetricConfigs,
  realtimeBufferKey,
  realtimeKeysForEquipments,
  type RealtimeMetric,
  type RealtimeMetricConfig,
} from '@/utils/realtimeBuffers'

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

type LatestFrameMap = Map<string, SensorFrame>

const FRESH_MS = 5_000
const STALE_MS = 30_000

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

function formatMetric(value: number | null | undefined, unit: string, digits = 1): string {
  if (value == null || Number.isNaN(value)) return '-'
  return `${Number(value).toFixed(digits)}${unit}`
}

function alarmLevelLabel(level: string | null | undefined): string {
  if (!level) return '-'
  const v = level.toLowerCase()
  if (v === 'danger') return '위험'
  if (v === 'warning') return '경고'
  if (v === 'normal') return '정상'
  return level
}

function buildRealtimeKeys(equipments: readonly Equipment[]): string[] {
  return realtimeKeysForEquipments(equipments)
}

function latestFrameMap(items: readonly SensorBufferLatest[]): LatestFrameMap {
  const map: LatestFrameMap = new Map()
  for (const item of items) {
    if (item.latest) map.set(item.bufferKey, item.latest)
  }
  return map
}

function latestValue(map: LatestFrameMap, equipmentCode: string, metric: RealtimeMetric): number | null {
  const key = realtimeBufferKey(equipmentCode, metric)
  if (!key) return null
  const value = map.get(key)?.value
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function latestTimestamp(map: LatestFrameMap, equipmentCode: string): number | null {
  let latest: number | null = null
  for (const metric of MONITORING_REALTIME_METRICS) {
    const key = realtimeBufferKey(equipmentCode, metric)
    const ts = key ? map.get(key)?.timestampMs : null
    if (typeof ts === 'number' && (latest == null || ts > latest)) latest = ts
  }
  return latest
}

function realtimeRate(map: LatestFrameMap, equipmentCode: string, fallbackState: EquipmentState): number {
  const ts = latestTimestamp(map, equipmentCode)
  if (ts == null) return fallbackState === '운전' ? 50 : 0
  const age = Date.now() - ts
  if (age <= FRESH_MS) return 100
  if (age >= STALE_MS) return 0
  return Math.max(0, Math.round(100 - ((age - FRESH_MS) / (STALE_MS - FRESH_MS)) * 100))
}

function realtimeLabel(map: LatestFrameMap, equipmentCode: string): string {
  const ts = latestTimestamp(map, equipmentCode)
  if (ts == null) return '미수신'
  const age = Date.now() - ts
  if (age <= FRESH_MS) return '실시간'
  if (age <= STALE_MS) return '지연'
  return '오프라인'
}

function buildCommon(equipment: Equipment, realtime: LatestFrameMap): EquipmentCommonMetric[] {
  const current = latestValue(realtime, equipment.equipmentCode, 'sensor_current')
  const voltage = latestValue(realtime, equipment.equipmentCode, 'sensor_voltage')
  const temperature = latestValue(realtime, equipment.equipmentCode, 'sensor_temperature')
  const vibration = latestValue(realtime, equipment.equipmentCode, 'sensor_vibration')

  return [
    { label: '설비코드', value: equipment.equipmentCode },
    { label: '라인', value: equipment.location ?? '-' },
    { label: '모델', value: equipment.model ?? '-' },
    { label: '설치일', value: equipment.installDate ?? '-' },
    { label: '수신상태', value: realtimeLabel(realtime, equipment.equipmentCode) },
    { label: '전류', value: formatMetric(current, 'A', 1) },
    { label: '전압', value: formatMetric(voltage, 'V', 1) },
    { label: '온도', value: formatMetric(temperature, '°C', 1) },
    { label: '습도', value: '-' },
    { label: '진동', value: formatMetric(vibration, '', 3) },
    { label: 'OK', value: '0' },
    { label: 'NG', value: '0' },
  ]
}

function buildRealtimeSpecific(
  equipment: Equipment,
  realtime: LatestFrameMap,
): EquipmentSpecificMetric[] {
  return processRealtimeMetricConfigs(equipment.processType).map((config: RealtimeMetricConfig) => {
    const value = latestValue(realtime, equipment.equipmentCode, config.metric)
    return {
      label: config.label,
      value: formatMetric(value, config.unit, config.digits),
      status: value == null ? '버퍼 수신 대기' : config.status,
    }
  })
}

function buildAnalysisSpecific(analysis: AnalysisResult | null): EquipmentSpecificMetric[] {
  const items: EquipmentSpecificMetric[] = []
  if (!analysis) return items
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

  return items
}

function buildSpecific(
  equipment: Equipment,
  realtime: LatestFrameMap,
  analysis: AnalysisResult | null,
): EquipmentSpecificMetric[] {
  const items = [
    ...buildRealtimeSpecific(equipment, realtime),
    ...buildAnalysisSpecific(analysis),
  ]
  return items.length ? items : [{ label: '분석 결과', value: '데이터 없음', status: '실시간 분석 대기' }]
}

export function useEquipmentCatalog() {
  const equipmentsQuery = useQuery({
    queryKey: ['equipments', 'FACTORY-01'],
    queryFn: () => fetchEquipments('FACTORY-01'),
    staleTime: STALE_TIME_MS.long,
  })

  const equipIds = computed(() => (equipmentsQuery.data.value ?? []).map((e) => e.equipmentCode))
  const realtimeKeys = computed(() => buildRealtimeKeys(equipmentsQuery.data.value ?? []))

  const statusQuery = useQuery({
    queryKey: ['equipment-statuses', equipIds],
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentCategory,
    staleTime: 5_000,
  })

  const realtimeQuery = useQuery({
    queryKey: ['equipment-realtime-latest', realtimeKeys],
    queryFn: () => fetchSensorLatestValues(realtimeKeys.value),
    enabled: computed(() => realtimeKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 1_000,
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

  const realtimeMap = computed(() => latestFrameMap(realtimeQuery.data.value ?? []))

  const categories = computed<EquipmentCategory[]>(() => {
    const equipments = (equipmentsQuery.data.value ?? []) as Equipment[]
    const sMap = statusMap.value
    const aMap = analysisMap.value
    const rMap = realtimeMap.value

    return CATEGORY_DEFINITIONS.map((def) => {
      const inCategory = equipments.filter((e) => e.processType === def.processType)
      const items: EquipmentDetailItem[] = inCategory.map((e) => {
        const code = sMap.get(e.equipmentCode)
        const state = statusCodeToState(code)
        const analysis = aMap.get(e.equipmentCode) ?? null
        const cycleTime = latestValue(rMap, e.equipmentCode, 'cycle_time')
        const rate = realtimeRate(rMap, e.equipmentCode, state)
        return {
          id: e.equipmentCode,
          name: e.equipmentName,
          line: e.location ?? '-',
          state,
          rate,
          defects: 0,
          operator: '-',
          cycle: cycleTime == null ? '-' : `${formatNumber(cycleTime, 1)}s`,
          common: buildCommon(e, rMap),
          specific: buildSpecific(e, rMap, analysis),
        }
      })

      const running = items.filter((it) => it.state === '운전').length
      const stopped = items.filter((it) => it.state === '정지').length
      const waiting = items.filter((it) => it.state === '대기' || it.state === '점검').length
      const avgRate = items.length
        ? Math.round(items.reduce((sum, item) => sum + item.rate, 0) / items.length)
        : 0

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
        defectCount: items.reduce((sum, item) => sum + item.defects, 0),
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
