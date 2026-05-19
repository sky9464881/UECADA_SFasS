import type { Equipment } from '@/types/equipment'

export type RealtimeMetric =
  | 'cycle_time'
  | 'sensor_current'
  | 'sensor_voltage'
  | 'sensor_temperature'
  | 'sensor_vibration'
  | 'temperature'
  | 'pressure'
  | 'spindle_load'
  | 'spindle_rpm'
  | 'feed_rate'
  | 'water_temp'
  | 'flow_rate'
  | 'torque'
  | 'leak_pressure'

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
  'temperature',
  'pressure',
  'spindle_load',
  'spindle_rpm',
  'feed_rate',
  'water_temp',
  'flow_rate',
  'torque',
  'leak_pressure',
] as const

const PROCESS_METRICS: Record<string, readonly RealtimeMetricConfig[]> = {
  주조: [
    { metric: 'temperature', label: '용탕 온도', unit: '°C', digits: 1, status: 'X_DAS 온도 버퍼' },
    { metric: 'pressure', label: '사출 압력', unit: 'bar', digits: 2, status: 'X_DAS 압력 버퍼' },
    { metric: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, status: 'X_DAS cycle_time' },
  ],
  가공: [
    { metric: 'spindle_load', label: '스핀들 부하', unit: '%', digits: 1, status: 'X_DAS spindle_load' },
    { metric: 'spindle_rpm', label: '스핀들 RPM', unit: 'RPM', digits: 0, status: 'X_DAS spindle_rpm' },
    { metric: 'feed_rate', label: '이송 속도', unit: 'mm/s', digits: 1, status: 'X_DAS feed_rate' },
    { metric: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, status: 'X_DAS cycle_time' },
  ],
  세척: [
    { metric: 'water_temp', label: '세척수 온도', unit: '°C', digits: 1, status: 'X_DAS water_temp' },
    { metric: 'flow_rate', label: '유량', unit: 'L/min', digits: 1, status: 'X_DAS flow_rate' },
    { metric: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, status: 'X_DAS cycle_time' },
  ],
  조립: [
    { metric: 'torque', label: '체결 토크', unit: 'N·m', digits: 2, status: 'X_DAS torque' },
    { metric: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, status: 'X_DAS cycle_time' },
  ],
  검사: [
    { metric: 'leak_pressure', label: '리크 압력', unit: 'Pa', digits: 3, status: 'X_DAS leak_pressure' },
    { metric: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, status: 'X_DAS cycle_time' },
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
