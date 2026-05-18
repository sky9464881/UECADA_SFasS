import { get } from './index.js'

/**
 * 라인 목록 조회 (설비 상태 카운트 + 최신 OEE 포함)
 * @param {string} [factoryId] - 'FACTORY-01'
 * @returns {[{
 *   lineId, lineName, lineStatus, factoryId,
 *   equipmentTotal, equipmentRunning, equipmentAlarm, equipmentStandby,
 *   openAlarmCount, latestOee
 * }]}
 *
 * 연결 컴포넌트: FactoryLayoutPage.vue, LineDetailPage.vue
 * lineStatus: RUNNING | STANDBY
 * latestOee: null 이면 KPI 미적재 상태
 */
export const getLines = (factoryId) =>
  get('/api/lines', { factoryId })
