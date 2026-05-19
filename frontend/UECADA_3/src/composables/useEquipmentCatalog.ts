import { computed, type Component } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchEquipments, fetchEquipmentStatuses, fetchEquipmentAvailability } from '@/api/equipmentApi'
import { fetchSensorLatestValues, type SensorBufferLatest, type SensorFrame } from '@/api/sensorApi'
import type { Equipment, EquipmentStatusCode, EquipmentStatusItem } from '@/types/equipment'
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

function realtimeRate(
  map: LatestFrameMap,
  equipmentCode: string,
  fallbackState: EquipmentState,
  processType: string | null | undefined,
): number {
  // 설비 타입 전용 센서(Type Data)가 전부 0.0이면 → 장비 꺼진 것
  const processMetrics = processRealtimeMetricConfigs(processType)
  if (processMetrics.length >= 2) {
    const values = processMetrics.map((c) => latestValue(map, equipmentCode, c.metric))
    const hasData = values.some((v) => v !== null)
    const allZero = values.every((v) => v === null || v === 0)
    if (hasData && allZero) return 0
  }

  // 데이터 수신 시각 기반 계산
  const ts = latestTimestamp(map, equipmentCode)
  if (ts == null) return fallbackState === '운전' ? 50 : 0
  const age = Date.now() - ts
  if (age <= FRESH_MS) return 100
  if (age >= STALE_MS) return 0
  return Math.max(0, Math.round(100 - ((age - FRESH_MS) / (STALE_MS - FRESH_MS)) * 100))
}

function buildCommon(equipment: Equipment, realtime: LatestFrameMap): EquipmentCommonMetric[] {
  const current = latestValue(realtime, equipment.equipmentCode, 'sensor_current')
  const voltage = latestValue(realtime, equipment.equipmentCode, 'sensor_voltage')
  const temperature = latestValue(realtime, equipment.equipmentCode, 'sensor_temperature')
  const vibration = latestValue(realtime, equipment.equipmentCode, 'sensor_vibration')

  return [
    { label: '전류', value: formatMetric(current, 'A', 1) },
    { label: '전압', value: formatMetric(voltage, 'V', 1) },
    { label: '온도', value: formatMetric(temperature, '℃', 1) },
    { label: '진동', value: formatMetric(vibration, '', 3) },
  ]
}

function formatRealtimeMetric(value: number | null | undefined, config: RealtimeMetricConfig): string {
  if (value == null || Number.isNaN(Number(value))) return '-'
  if (config.metric === 'result_ok') return value >= 0.5 ? 'true' : 'false'
  return formatMetric(value, config.unit, config.digits)
}

function buildSpecific(equipment: Equipment, realtime: LatestFrameMap): EquipmentSpecificMetric[] {
  const items = processRealtimeMetricConfigs(equipment.processType).map((config: RealtimeMetricConfig) => {
    const value = latestValue(realtime, equipment.equipmentCode, config.metric)
    return {
      label: config.label,
      value: formatRealtimeMetric(value, config),
      status: value == null ? '버퍼 수신 대기' : config.status,
    }
  })
  return items.length ? items : [{ label: 'type_data', value: '-', status: '버퍼 수신 대기' }]
}

export function useEquipmentCatalog() {
  const equipmentsQuery = useQuery({
    queryKey: ['equipments', 'FACTORY-01'],
    queryFn: () => fetchEquipments('FACTORY-01'),
    staleTime: STALE_TIME_MS.long,
  })

  const equipIds = computed(() => (equipmentsQuery.data.value ?? []).map((e) => e.equipmentCode))
  const realtimeKeys = computed(() => realtimeKeysForEquipments(equipmentsQuery.data.value ?? []))

  const statusQuery = useQuery({
    queryKey: computed(() => ['equipment-statuses', equipIds.value]),
    queryFn: () => fetchEquipmentStatuses(equipIds.value),
    enabled: computed(() => equipIds.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentCategory,
    staleTime: 0,
    refetchIntervalInBackground: true,
  })

  const realtimeQuery = useQuery({
    queryKey: computed(() => ['equipment-realtime-latest', realtimeKeys.value]),
    queryFn: () => fetchSensorLatestValues(realtimeKeys.value),
    enabled: computed(() => realtimeKeys.value.length > 0),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 0,
    refetchIntervalInBackground: true,
  })

  const availabilityQuery = useQuery({
    queryKey: ['equipment-availability'],
    queryFn: () => fetchEquipmentAvailability(10),
    refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
    staleTime: 0,
    refetchIntervalInBackground: true,
  })

  const statusMap = computed(() => {
    const map = new Map<string, EquipmentStatusCode>()
    for (const item of (statusQuery.data.value ?? []) as EquipmentStatusItem[]) {
      map.set(item.equipId, item.statusCode)
    }
    return map
  })

  const realtimeMap = computed(() => latestFrameMap(realtimeQuery.data.value ?? []))

  const availabilityMap = computed(() => {
    const map = new Map<string, number>()
    for (const item of availabilityQuery.data.value ?? []) {
      map.set(item.equipmentCode, item.availabilityPct)
    }
    return map
  })

  const categories = computed<EquipmentCategory[]>(() => {
    const equipments = (equipmentsQuery.data.value ?? []) as Equipment[]
    const sMap = statusMap.value
    const rMap = realtimeMap.value

    return CATEGORY_DEFINITIONS.map((def) => {
      const inCategory = equipments.filter((e) => e.processType === def.processType)
      const items: EquipmentDetailItem[] = inCategory.map((e) => {
        const code = sMap.get(e.equipmentCode)
        let state = statusCodeToState(code)
        const cycleTime = latestValue(rMap, e.equipmentCode, 'cycle_time')
        // 현재 센서값(Type Data) 먼저 체크 → 꺼진 상태 즉시 반영
        const sensorRate = realtimeRate(rMap, e.equipmentCode, state, e.processType)
        // 센서가 꺼진 상태(0)면 즉시 0, 아니면 10분 가동률 버퍼 사용
        const dbRate = availabilityMap.value.get(e.equipmentCode)
        const rate = sensorRate === 0 ? 0 : (dbRate !== undefined ? dbRate : sensorRate)
        if (rate === 0 && (state === '운전' || state === '대기')) state = '점검'
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
          specific: buildSpecific(e, rMap),
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
