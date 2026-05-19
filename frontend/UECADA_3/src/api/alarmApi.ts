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

const MOCK: AlarmListResponse = {
  summary: [
    { label: '전체 알람 수', value: 47, detail: '금일 00:00 ~ 12:40', tone: 'info' },
    { label: '긴급 알람', value: 2, detail: '즉시 조치 필요', tone: 'critical' },
    { label: '처리 완료', value: 38, detail: '완료율 80.8%', tone: 'done' },
    { label: '미처리 알람', value: 7, detail: '담당자 확인 필요', tone: 'pending' },
  ],
  rows: [
    { alarmId: 0, time: '12:38:22', equipment: 'CAST-02', type: '긴급', category: '온도', message: '용탕온도 상한 초과', status: '조치중' },
    { alarmId: 0, time: '12:21:05', equipment: 'ASM-05', type: '경고', category: '하중', message: '압입하중 편차 발생', status: '미처리' },
    { alarmId: 0, time: '11:54:44', equipment: 'INSP-02', type: '경고', category: '치수', message: '현재 물체 치수 허용범위 이탈', status: '미처리' },
    { alarmId: 0, time: '11:18:12', equipment: 'MACH-11', type: '정보', category: '공구', message: '공구사용시간 교체 기준 80% 도달', status: '처리완료' },
    { alarmId: 0, time: '10:42:39', equipment: 'WASH-03', type: '점검', category: '농도', message: '세척농도 보정 작업 등록', status: '처리완료' },
  ],
}

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

function formatTime(occurredAt: string): string {
  if (!occurredAt) return ''
  const idx = occurredAt.indexOf('T')
  if (idx === -1) return occurredAt
  return occurredAt.slice(idx + 1, idx + 9)
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

function toRows(list: AlarmResponse[]): AlarmHistoryRow[] {
  return list.map((a) => ({
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
  if (useMockAlarms()) return []
  const { data } = await api.get<AlarmResponse[]>('/api/alarms')
  return data
}

export async function fetchAlarmList(): Promise<AlarmListResponse> {
  if (useMockAlarms()) {
    await new Promise((r) => setTimeout(r, 400))
    if (typeof structuredClone === 'function') return structuredClone(MOCK)
    return JSON.parse(JSON.stringify(MOCK)) as AlarmListResponse
  }
  const data = await fetchAlarmsRaw()
  return {
    summary: toSummary(data),
    rows: toRows(data),
  }
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
