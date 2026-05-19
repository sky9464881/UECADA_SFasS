import { api } from '@/api/client'
import type { AlarmHistoryRow, AlarmListResponse, AlarmSummaryItem } from '@/types/alarm'

export interface AlarmResponse {
  alarmId: number
  equipmentCode: string
  alarmType: string
  severity: string
  status: string
  alarmMessage: string
  occurredAt: string
  resolvedBy: string | null
  resolvedAt: string | null
  comment: string | null
}

export interface AlarmStatItem {
  date: string
  alarmType: string
  count: number
}

export interface AlarmResolvePayload {
  resolvedBy: string
  resolvedAt?: string
  comment?: string
}

/** 데모용 raw 알람 배열. UI 변환은 toAlarmListResponse 가 담당. */
const MOCK_RAW: AlarmResponse[] = [
  { alarmId: 1001, equipmentCode: 'CAST-02', alarmType: '온도 이상', severity: 'CRITICAL', status: 'IN_PROGRESS', alarmMessage: '용탕온도 상한 초과', occurredAt: '2026-05-11T12:38:22', resolvedBy: null, resolvedAt: null, comment: null },
  { alarmId: 1002, equipmentCode: 'ASM-05', alarmType: '하중 이상', severity: 'WARNING', status: 'OPEN', alarmMessage: '압입하중 편차 발생', occurredAt: '2026-05-11T12:21:05', resolvedBy: null, resolvedAt: null, comment: null },
  { alarmId: 1003, equipmentCode: 'INSP-02', alarmType: '치수 이상', severity: 'WARNING', status: 'OPEN', alarmMessage: '현재 물체 치수 허용범위 이탈', occurredAt: '2026-05-11T11:54:44', resolvedBy: null, resolvedAt: null, comment: null },
  { alarmId: 1004, equipmentCode: 'MACH-11', alarmType: '공구', severity: 'INFO', status: 'RESOLVED', alarmMessage: '공구사용시간 교체 기준 80% 도달', occurredAt: '2026-05-11T11:18:12', resolvedBy: '시스템', resolvedAt: '2026-05-11T11:20:00', comment: null },
  { alarmId: 1005, equipmentCode: 'WASH-03', alarmType: '농도', severity: 'INFO', status: 'RESOLVED', alarmMessage: '세척농도 보정 작업 등록', occurredAt: '2026-05-11T10:42:39', resolvedBy: '시스템', resolvedAt: '2026-05-11T10:45:00', comment: null },
]

function useMockAlarms(): boolean {
  const mock = import.meta.env.VITE_USE_MOCK_ALARMS
  const base = import.meta.env.VITE_API_BASE_URL
  return mock === 'true' || base === '' || base == null
}

export function mapSeverityToType(severity: string): string {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
      return '긴급'
    case 'WARNING':
      return '경고'
    case 'INFO':
      return '정보'
    default:
      return '점검'
  }
}

export function mapStatusLabel(status: string): string {
  switch (status?.toUpperCase()) {
    case 'OPEN':
      return '미처리'
    case 'IN_PROGRESS':
      return '조치중'
    case 'RESOLVED':
      return '처리완료'
    default:
      return status ?? '-'
  }
}

export function extractCategory(alarmType: string): string {
  if (!alarmType) return '기타'
  return alarmType.replace(/\s*이상\s*$/, '').trim() || alarmType
}

/**
 * 'YYYY-MM-DDTHH:MM:SS' → 'MM-DD HH:MM:SS'
 * 여러 날짜에 걸친 알람을 함께 표시할 때 혼선을 줄이기 위해 날짜를 함께 노출한다.
 */
function formatTime(occurredAt: string): string {
  if (!occurredAt) return ''
  const idx = occurredAt.indexOf('T')
  if (idx === -1) return occurredAt
  const date = occurredAt.slice(5, idx)
  const time = occurredAt.slice(idx + 1, idx + 9)
  return `${date} ${time}`
}

function toSummary(list: AlarmResponse[]): AlarmSummaryItem[] {
  const total = list.length
  const critical = list.filter((a) => a.severity?.toUpperCase() === 'CRITICAL').length
  const done = list.filter((a) => a.status?.toUpperCase() === 'RESOLVED').length
  const pending = list.filter((a) => a.status?.toUpperCase() === 'OPEN').length
  const completionRate = total > 0 ? `완료율 ${((done / total) * 100).toFixed(1)}%` : '완료율 -'

  return [
    { label: '전체 알람 수', value: total, detail: '서버 기준 누적', tone: 'info' },
    { label: '긴급 알람', value: critical, detail: '즉시 조치 필요', tone: 'critical' },
    { label: '처리 완료', value: done, detail: completionRate, tone: 'done' },
    { label: '미처리 알람', value: pending, detail: '담당자 확인 필요', tone: 'pending' },
  ]
}

/** 화면 렌더 상한선. 백엔드가 수천 건을 반환해도 DOM 폭주 방지. */
const MAX_TABLE_ROWS = 100

function toRows(list: AlarmResponse[]): AlarmHistoryRow[] {
  // 최신순으로 정렬 후 상한선만 잘라 렌더
  const sorted = [...list].sort((a, b) => (b.occurredAt ?? '').localeCompare(a.occurredAt ?? ''))
  return sorted.slice(0, MAX_TABLE_ROWS).map((a) => ({
    alarmId: a.alarmId,
    time: formatTime(a.occurredAt),
    equipment: a.equipmentCode ?? '-',
    type: mapSeverityToType(a.severity),
    category: extractCategory(a.alarmType),
    message: a.alarmMessage ?? '',
    status: mapStatusLabel(a.status),
  }))
}

export async function fetchAlarmsRaw(): Promise<AlarmResponse[]> {
  if (useMockAlarms()) {
    await new Promise((r) => setTimeout(r, 400))
    return typeof structuredClone === 'function'
      ? structuredClone(MOCK_RAW)
      : (JSON.parse(JSON.stringify(MOCK_RAW)) as AlarmResponse[])
  }
  const { data } = await api.get<AlarmResponse[]>('/api/alarms')
  return data
}

/** raw 알람 배열을 화면용 summary + rows 로 변환. */
export function toAlarmListResponse(data: AlarmResponse[]): AlarmListResponse {
  return {
    summary: toSummary(data),
    rows: toRows(data),
    totalCount: data.length,
  }
}

export async function fetchAlarmList(): Promise<AlarmListResponse> {
  const data = await fetchAlarmsRaw()
  return toAlarmListResponse(data)
}

export async function fetchAlarmStats(from: string, to: string): Promise<AlarmStatItem[]> {
  if (useMockAlarms()) return []
  const { data } = await api.get<AlarmStatItem[]>('/api/alarms/stats', {
    params: { from, to },
  })
  return data
}

export async function resolveAlarm(
  alarmId: number,
  payload: AlarmResolvePayload,
): Promise<AlarmResponse> {
  const { data } = await api.patch<AlarmResponse>(`/api/alarms/${alarmId}/resolve`, payload)
  return data
}
