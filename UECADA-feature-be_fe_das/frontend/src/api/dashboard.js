import { get } from './index.js'

/**
 * 프론트엔드 대시보드 집계 데이터
 * @returns {{
 *   factoryOee: number|null,
 *   statusDonut: { running, standby, alarm, maintenance, total },
 *   alarmSummary: { total, critical, warning, resolved, open },
 *   lineStats: [{ lineId, lineName, oee }],
 *   oeeHourlySeries: [{ lineId, lineName, data: [{ time, oee }] }]
 * }}
 *
 * 연결 컴포넌트: DashboardPage.vue
 * 매핑 예시:
 *   totalOeeDisplay.value  = data.factoryOee?.toFixed(1) ?? '--'
 *   statusDonutSeries.value = [data.statusDonut.running, data.statusDonut.standby, data.statusDonut.alarm]
 *   alarmSummary (day)      = data.alarmSummary
 *   lineStats               = data.lineStats
 *   oeeHourlySeries         = data.oeeHourlySeries.map(l => ({ name: l.lineName, data: l.data.map(p => p.oee) }))
 */
export const getDashboard = () =>
  get('/api/dashboard/frontend')

/**
 * 기존 AI 분석 기반 대시보드 요약 (DashboardController 기존 API)
 * @returns {{ equipmentCount, recentAnalysisCount, recentAlarmCount,
 *             equipmentStatusDistribution, alarmLevelDistribution }}
 */
export const getDashboardSummary = () =>
  get('/api/dashboard/summary')
