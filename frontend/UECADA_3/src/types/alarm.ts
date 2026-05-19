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
  /** 화면 행 컷 적용 전 전체 알람 수 (안내 배지에 사용) */
  totalCount: number
}
