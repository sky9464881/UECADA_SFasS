export type AlarmTone = 'info' | 'critical' | 'done' | 'pending'

export interface AlarmSummaryItem {
  label: string
  value: number
  detail: string
  tone: AlarmTone
}

export interface AlarmHistoryRow {
  alarmId: number
  time: string
  equipment: string
  type: string
  category: string
  message: string
  status: string
}

export interface AlarmListResponse {
  summary: AlarmSummaryItem[]
  rows: AlarmHistoryRow[]
}
