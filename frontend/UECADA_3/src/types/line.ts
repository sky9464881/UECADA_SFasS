export type LineStatusCode = 'RUNNING' | 'STANDBY' | 'ALARM' | 'MAINTENANCE' | string

export interface LineSummary {
  lineId: string
  lineName: string
  lineStatus: LineStatusCode
  factoryId: string
  equipmentTotal: number
  equipmentRunning: number
  equipmentAlarm: number
  equipmentStandby: number
  equipmentMaintenance: number
  openAlarmCount: number
  latestOee: number | null
  balanceRate?: number | null
  uph?: number | null
  upmh?: number | null
  productivity?: number | null
  stationUtilization?: number[] | null
}
