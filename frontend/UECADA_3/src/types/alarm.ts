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
  /** 화면 표시용 설비명/코드 */
  equipment: string
  /** 드릴다운용 원본 설비 코드 */
  equipmentCode: string
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
