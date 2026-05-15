import { post } from './index.js'

/**
 * 로그인
 * @param {string} loginId
 * @param {string} password
 * @returns {{ userId, userName, email, roleName }}
 */
export const login = (loginId, password) =>
  post('/api/auth/login', { loginId, password })
