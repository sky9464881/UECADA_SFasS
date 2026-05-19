/**
 * 프론트 전역 폴링 / staleTime 상수.
 * VITE_REALTIME_DEMO=true 이면 대시보드·라인·설비·레이아웃을 1초마다 갱신합니다.
 */

const realtimeDemo = import.meta.env.VITE_REALTIME_DEMO === 'true'

export const POLL_INTERVAL_MS = {
  alarm: realtimeDemo ? 2_000 : 5_000,
  dashboard: realtimeDemo ? 1_000 : 10_000,
  equipmentCategory: realtimeDemo ? 1_000 : 15_000,
  equipmentAnalysis: realtimeDemo ? 5_000 : 30_000,
  factoryLayout: realtimeDemo ? 1_000 : 15_000,
  lineDetail: realtimeDemo ? 1_000 : 15_000,
  posts: 60_000,
  alarmInsights: 60_000,
} as const

export const STALE_TIME_MS = {
  short: realtimeDemo ? 500 : 15_000,
  medium: realtimeDemo ? 1_000 : 30_000,
  long: realtimeDemo ? 2_000 : 60_000,
} as const

export const HTTP_TIMEOUT_MS = 30_000
