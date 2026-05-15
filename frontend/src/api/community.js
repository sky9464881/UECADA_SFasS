import { get, post } from './index.js'

/**
 * 게시글 목록 조회
 * @param {string} [category] - 'NOTICE' | 'GENERAL' 등 post_type 값
 * @returns {[{ postId, authorUserId, title, category, createdAt }]}
 *
 * 연결 컴포넌트: CommunityPage.vue 게시판 섹션
 * is_deleted=0 인 글만 반환됨
 */
export const getPosts = (category) =>
  get('/api/posts', { category })

/**
 * 게시글 등록
 * @param {{ authorUserId, title, content, category? }} body
 */
export const createPost = (body) =>
  post('/api/posts', body)
