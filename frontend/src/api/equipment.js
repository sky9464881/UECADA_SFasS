import { get, put } from './index.js'

/**
 * 설비 목록 조회
 * @param {string} [factoryId] - 공장 ID 필터 (예: 'FACTORY-01')
 * @returns {[{ id, equipmentCode, equipmentName, processType, model,
 *              installDate, location, locationX, locationY, createdAt }]}
 *
 * 연결 컴포넌트: EquipmentDetailPage.vue, FactoryLayoutPage.vue
 * processType 으로 카테고리 분류 가능 ('주조', '가공', '세척', '조립', '검사')
 */
export const getEquipments = (factoryId) =>
  get('/api/equipments', { factoryId })

/**
 * 설비 상태 일괄 조회
 * @param {string[]} equipIds - equipment_code 배열
 * @returns {[{ equipId, statusCode, updatedAt }]}
 *
 * 연결 컴포넌트: EquipmentDetailPage.vue, FactoryLayoutPage.vue
 * statusCode: RUNNING | STANDBY | ALARM | MAINTENANCE
 */
export const getEquipmentStatus = (equipIds) =>
  get('/api/equipment-status', { equipIds })

/**
 * 설비 상태 변경 (UPSERT)
 * @param {string} equipId
 * @param {string} statusCode - RUNNING | STANDBY | ALARM | MAINTENANCE
 */
export const updateEquipmentStatus = (equipId, statusCode) =>
  put(`/api/equipment-status/${equipId}`, { statusCode })

/**
 * 설비별 분석 결과 조회
 * @param {string} equipmentCode
 * @param {string} [analysisType] - 'vibration' | 'temperature' | 'leak' 등
 * @param {number} [limit=100]
 */
export const getAnalysisResults = (equipmentCode, analysisType, limit = 100) =>
  get(`/api/equipments/${equipmentCode}/analysis-results`, { analysisType, limit })
