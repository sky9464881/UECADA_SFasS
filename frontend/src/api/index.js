/**
 * API 기본 유틸리티 (fetch 기반, 외부 의존성 없음)
 * 사용 예시:
 *   import { get, post, patch } from '@/api/index.js'
 *   const data = await get('/api/equipments', { factoryId: 'FACTORY-01' })
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request(method, path, { params, body } = {}) {
  const url = new URL(BASE_URL + path)
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') {
        if (Array.isArray(v)) v.forEach(item => url.searchParams.append(k, item))
        else url.searchParams.set(k, v)
      }
    })
  }

  const res = await fetch(url.toString(), {
    method,
    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
    body: body ? JSON.stringify(body) : undefined,
  })

  if (res.status === 204) return null
  if (!res.ok) {
    const msg = await res.text().catch(() => res.statusText)
    throw new Error(`[${method} ${path}] ${res.status}: ${msg}`)
  }
  return res.json()
}

export const get   = (path, params)       => request('GET',   path, { params })
export const post  = (path, body)         => request('POST',  path, { body })
export const patch = (path, body)         => request('PATCH', path, { body })
export const put   = (path, body)         => request('PUT',   path, { body })
