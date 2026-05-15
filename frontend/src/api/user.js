import { get, post, patch } from './index.js'

/**
 * 사용자 목록 조회
 * @param {string} [roleName] - 'ADMIN' | 'MANAGER' | 'OPERATOR'
 * @returns {[{ userId, userName, email, roleName, createdAt }]}
 *
 * 연결 컴포넌트: UserManagementPage.vue
 */
export const getUsers = (roleName) =>
  get('/api/users', { roleName })

/**
 * 사용자 등록
 * @param {{ userId, loginId, userName, email, roleName, password }} body
 */
export const createUser = (body) =>
  post('/api/users', body)

/**
 * 사용자 역할 변경
 * @param {string} userId
 * @param {string} roleName - 'ADMIN' | 'MANAGER' | 'OPERATOR'
 */
export const updateUserRole = (userId, roleName) =>
  patch(`/api/users/${userId}/role`, { roleName })
