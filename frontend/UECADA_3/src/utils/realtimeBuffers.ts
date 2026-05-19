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
