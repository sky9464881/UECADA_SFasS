/**
 * 프론트 전역 폴링 / staleTime 상수.
 * 컴포저블마다 흩어져있던 매직 넘버를 모아 일관성 + 가독성을 확보한다.
 *
 * 정책 요약:
 *  - 실시간성이 가장 중요한 알람은 빠른 폴링
 *  - 대시보드/라인/설비는 10~15초 주기
 *  - 사용자/게시글/알람 인사이트는 분 단위로 충분
 */

export const POLL_INTERVAL_MS = {
  alarm: 1_000,
  equipmentRealtime: 1_000,
  dashboard: 10_000,
  equipmentCategory: 15_000,
  equipmentAnalysis: 30_000,
  factoryLayout: 15_000,
  lineDetail: 15_000,
  posts: 60_000,
  alarmInsights: 60_000,
} as const

export const STALE_TIME_MS = {
  short: 15_000,
  medium: 30_000,
  long: 60_000,
} as const

export const HTTP_TIMEOUT_MS = 30_000
