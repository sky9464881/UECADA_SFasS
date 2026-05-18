import { get, post, patch } from './index.js'

/**
 * 알람 목록 조회 (임계값 기반 alarm 테이블)
 * @param {{ status?, equipmentCode?, from?, to? }} filters
 *   - status: 'OPEN' | 'RESOLVED'
 *   - from/to: ISO datetime 문자열 (예: '2026-05-14T00:00:00')
 * @returns {[{ alarmId, equipmentCode, alarmType, severity, status,
 *              alarmMessage, occurredAt, resolvedBy, resolvedAt, comment }]}
 *
 * 연결 컴포넌트: AlarmPage.vue
 */
export const getAlarms = ({ status, equipmentCode, from, to } = {}) =>
  get('/api/alarms', { status, equipmentCode, from, to })

/**
 * 알람 통계 (날짜별 × 타입별 COUNT)
 * @param {string} from - ISO datetime
 * @param {string} to   - ISO datetime
 * @returns {[{ date, alarmType, count }]}
 *
 * 연결 컴포넌트: AlarmPage.vue 시간대별 추이 차트
 */
export const getAlarmStats = (from, to) =>
  get('/api/alarms/stats', { from, to })

/**
 * 알람 발생 등록
 * @param {{ equipmentCode, alarmType, severity, alarmMessage, occurredAt?, sensorSnapshot? }} body
 */
export const createAlarm = (body) =>
  post('/api/alarms', body)

/**
 * 알람 처리 (RESOLVED 상태 변경)
 * @param {number} alarmId
 * @param {{ resolvedBy?, resolvedAt?, comment? }} body
 */
export const resolveAlarm = (alarmId, body) =>
  patch(`/api/alarms/${alarmId}/resolve`, body)

/**
 * AI 분석 기반 알람 이력 (alarm_history 테이블)
 * @returns {[{ id, equipmentCode, alarmLevel, status, message, occurredAt, ... }]}
 *
 * 연결 컴포넌트: AlarmPage.vue 발생 이력 테이블
 */
export const getAlarmHistories = (limit = 100) =>
  get('/api/alarm-histories', { limit })
