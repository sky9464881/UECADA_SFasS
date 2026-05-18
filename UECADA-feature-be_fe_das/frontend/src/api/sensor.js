import { get, post } from './index.js'

/**
 * 센서 버퍼 데이터 조회
 * @param {string} bufferKey - 예: 'LINE-01_CNC-01:spindle_load'
 * @param {number} [last=0]  - 최근 N개만 조회 (0이면 전체)
 * @returns {{ bufferKey, size, capacity, latest: { timestampMs, value }, frames: [{ timestampMs, value }] }}
 *
 * 연결 컴포넌트: EquipmentDetailPage.vue 실시간 센서 패널
 *
 * 버퍼 키 형식: '{equipment_code}:{sensor_type}'
 * 센서 타입 예시:
 *   :temperature, :pressure, :cycle_time        (주조)
 *   :spindle_load, :spindle_rpm, :feed_rate      (가공)
 *   :water_temp, :flow_rate                      (세척)
 *   :torque                                      (조립)
 *   :leak_pressure                               (검사)
 *   ENV:vibration_x/y/z, ENV:current, ENV:temperature (공통)
 */
export const getSensorBuffer = (bufferKey, last = 0) =>
  get(`/api/sensors/${encodeURIComponent(bufferKey)}`, last > 0 ? { last } : undefined)

/**
 * 등록된 버퍼 키 목록
 * @returns {string[]}
 */
export const getSensorKeys = () =>
  get('/api/sensors')

/**
 * 여러 버퍼의 최신값을 한 번에 조회
 * @param {string[]} bufferKeys
 * @returns {[{ bufferKey, size, capacity, latest: { timestampMs, value } }]}
 */
export const getSensorLatestValues = (bufferKeys) =>
  post('/api/sensors/latest-values', { bufferKeys })

/**
 * 센서 데이터 push (단일 또는 배치)
 * @param {string} bufferKey
 * @param {{ timestampMs: number, value: number }[]} frames
 */
export const pushSensorData = (bufferKey, frames) =>
  post(`/api/sensors/${encodeURIComponent(bufferKey)}`, { frames })
