export interface DashboardStatusDonut {
  running: number
  standby: number
  alarm: number
  maintenance: number
  total: number
}

export interface DashboardAlarmSummary {
  total: number
  critical: number
  warning: number
  resolved: number
  open: number
}

export interface DashboardLineStat {
  lineId: string
  lineName: string
  oee: number | null
}

export interface DashboardOeePoint {
  time: string
  oee: number | null
}

export interface DashboardOeeHourlySeries {
  lineId: string
  lineName: string
  data: DashboardOeePoint[]
}

export interface DashboardSummary {
  factoryOee: number | null
  statusDonut: DashboardStatusDonut
  alarmSummary: DashboardAlarmSummary
  lineStats: DashboardLineStat[]
  oeeHourlySeries: DashboardOeeHourlySeries[]
}
