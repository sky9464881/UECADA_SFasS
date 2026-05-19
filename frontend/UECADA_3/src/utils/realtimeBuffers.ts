import type { Equipment } from '@/types/equipment'

export type RealtimeMetric =
  | 'cycle_time'
  | 'sensor_current'
  | 'sensor_voltage'
  | 'sensor_temperature'
  | 'sensor_vibration'
  | 'injection_pressure'
  | 'mold_temperature'
  | 'cooling_flow'
  | 'spindle_speed'
  | 'tool_usage'
  | 'coolant_flow'
  | 'cleaning_concentration'
  | 'cleaning_temperature'
  | 'cleaning_pressure'
  | 'tightening_torque'
  | 'tightening_angle'
  | 'press_force'
  | 'bore_dimension'
  | 'hole_dimension'
  | 'result_ok'

export interface RealtimeMetricConfig {
  metric: RealtimeMetric
  label: string
  unit: string
  digits: number
  status: string
}

export const COMMON_REALTIME_METRICS: readonly RealtimeMetric[] = [
  'cycle_time',
  'sensor_current',
  'sensor_voltage',
  'sensor_temperature',
  'sensor_vibration',
] as const

export const MONITORING_REALTIME_METRICS: readonly RealtimeMetric[] = [
  'cycle_time',
  'sensor_current',
  'sensor_voltage',
  'sensor_temperature',
  'sensor_vibration',
  'injection_pressure',
  'mold_temperature',
  'cooling_flow',
  'spindle_speed',
  'tool_usage',
  'coolant_flow',
  'cleaning_concentration',
  'cleaning_temperature',
  'cleaning_pressure',
  'tightening_torque',
  'tightening_angle',
  'press_force',
  'bore_dimension',
  'hole_dimension',
  'result_ok',
] as const

const PROCESS_METRICS: Record<string, readonly RealtimeMetricConfig[]> = {
  주조: [
    { metric: 'injection_pressure', label: 'injection_pressure', unit: 'MPa', digits: 2, status: 'X_DAS injection_pressure' },
    { metric: 'mold_temperature', label: 'mold_temperature', unit: '℃', digits: 1, status: 'X_DAS mold_temperature' },
    { metric: 'cooling_flow', label: 'cooling_flow', unit: 'L/min', digits: 1, status: 'X_DAS cooling_flow' },
  ],
  가공: [
    { metric: 'spindle_speed', label: 'spindle_speed', unit: 'rpm', digits: 0, status: 'X_DAS spindle_speed' },
    { metric: 'tool_usage', label: 'tool_usage', unit: '%', digits: 1, status: 'X_DAS tool_usage' },
    { metric: 'coolant_flow', label: 'coolant_flow', unit: 'L/min', digits: 1, status: 'X_DAS coolant_flow' },
  ],
  세척: [
    { metric: 'cleaning_concentration', label: 'cleaning_concentration', unit: '%', digits: 2, status: 'X_DAS cleaning_concentration' },
    { metric: 'cleaning_temperature', label: 'cleaning_temperature', unit: '℃', digits: 1, status: 'X_DAS cleaning_temperature' },
    { metric: 'cleaning_pressure', label: 'cleaning_pressure', unit: 'bar', digits: 2, status: 'X_DAS cleaning_pressure' },
  ],
  조립: [
    { metric: 'tightening_torque', label: 'tightening_torque', unit: 'Nm', digits: 2, status: 'X_DAS tightening_torque' },
    { metric: 'tightening_angle', label: 'tightening_angle', unit: 'deg', digits: 1, status: 'X_DAS tightening_angle' },
    { metric: 'press_force', label: 'press_force', unit: 'N', digits: 1, status: 'X_DAS press_force' },
  ],
  검사: [
    { metric: 'bore_dimension', label: 'bore_dimension', unit: 'mm', digits: 3, status: 'X_DAS bore_dimension' },
    { metric: 'hole_dimension', label: 'hole_dimension', unit: 'mm', digits: 3, status: 'X_DAS hole_dimension' },
    { metric: 'result_ok', label: 'result_ok', unit: 'bool', digits: 0, status: 'X_DAS result_ok' },
  ],
}

export function realtimeBufferKey(equipmentCode: string, metric: RealtimeMetric): string | null {
  const match = equipmentCode.match(/^(LINE[-_]?\d{2})[_.](.+)$/i)
  if (!match) return null
  const line = match[1].replace(/[-_]/g, '').toUpperCase()
  const equipment = match[2].replace(/[-_]/g, '').toUpperCase()
  return `${line}.${equipment}:${metric}`
}

export function processRealtimeMetricConfigs(processType: string | null | undefined): readonly RealtimeMetricConfig[] {
  return processType ? (PROCESS_METRICS[processType] ?? []) : []
}

export function realtimeMetricsForEquipment(equipment: Pick<Equipment, 'processType'>): RealtimeMetric[] {
  return [
    ...new Set([
      ...COMMON_REALTIME_METRICS,
      ...processRealtimeMetricConfigs(equipment.processType).map((config) => config.metric),
    ]),
  ]
}

export function realtimeKeysForEquipments(equipments: readonly Pick<Equipment, 'equipmentCode' | 'processType'>[]): string[] {
  const keys = equipments.flatMap((equipment) =>
    realtimeMetricsForEquipment(equipment)
      .map((metric) => realtimeBufferKey(equipment.equipmentCode, metric))
      .filter((key): key is string => !!key),
  )
  return [...new Set(keys)]
}

/** FNV-1a 32-bit — 설비코드·메트릭별 안정적인 데모 숫자용 시드 */
function seedHash(input: string): number {
  let h = 2166136261
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * X_DAS 버퍼가 비어 있을 때 공정별 지표에 표시할 예시값(설비코드 기준 결정적).
 * 실시간 대체가 아니라 데모/오프라인 UI용.
 */
export function demoRealtimeMetricValue(equipmentCode: string, config: RealtimeMetricConfig): number {
  const n = seedHash(`${equipmentCode}:${config.metric}`)
  const u01 = (n % 10_000) / 10_000
  switch (config.metric) {
    case 'injection_pressure':
      return 6 + u01 * 12
    case 'mold_temperature':
      return 190 + u01 * 90
    case 'cooling_flow':
      return 25 + u01 * 85
    case 'spindle_speed':
      return 1200 + Math.floor(u01 * 6800)
    case 'tool_usage':
      return 20 + u01 * 70
    case 'coolant_flow':
      return 15 + u01 * 45
    case 'cleaning_concentration':
      return 2 + u01 * 8
    case 'cleaning_temperature':
      return 35 + u01 * 45
    case 'cleaning_pressure':
      return 1.5 + u01 * 4
    case 'tightening_torque':
      return 8 + u01 * 35
    case 'tightening_angle':
      return 45 + u01 * 135
    case 'press_force':
      return 800 + u01 * 4200
    case 'bore_dimension':
      return 10 + u01 * 4.999
    case 'hole_dimension':
      return 8 + u01 * 3.999
    case 'result_ok':
      return (n % 10) < 2 ? 0 : 1
    default:
      return u01 * 100
  }
}
