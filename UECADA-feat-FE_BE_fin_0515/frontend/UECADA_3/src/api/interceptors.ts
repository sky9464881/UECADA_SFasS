import type { AxiosError, AxiosInstance } from 'axios'
import { useAuthStore } from '@/stores/auth'

let routerRef: { push: (path: string) => void } | null = null

export function setAuthRouter(router: { push: (path: string) => void }) {
  routerRef = router
}

/**
 * 외부에서 토스트 / 알림 시스템을 주입할 수 있는 훅.
 * 화면 UI 컴포넌트에서 등록한 핸들러가 있다면 호출, 없으면 콘솔에 기록.
 */
type NetworkErrorHandler = (message: string, err: AxiosError) => void
let onNetworkError: NetworkErrorHandler | null = null

export function setNetworkErrorHandler(handler: NetworkErrorHandler | null) {
  onNetworkError = handler
}

function describeError(err: AxiosError): string {
  const status = err.response?.status
  if (!status) return '서버 연결에 실패했습니다.'
  if (status === 401) return '인증이 만료되었습니다. 다시 로그인해 주세요.'
  if (status === 403) return '권한이 없습니다.'
  if (status >= 500) return `서버 오류 (${status})`
  return err.message ?? `요청 실패 (${status})`
}

export function attachAuthInterceptors(instance: AxiosInstance) {
  instance.interceptors.request.use((config) => {
    const auth = useAuthStore()
    const token = auth.accessToken
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (res) => res,
    (err: AxiosError) => {
      const status = err?.response?.status
      const message = describeError(err)

      if (status === 401) {
        const auth = useAuthStore()
        auth.clearSession()
        routerRef?.push('/login')
      } else if (!status || status >= 500) {
        // 네트워크 단절 / 서버 오류는 사용자 통지 후보. 핸들러가 없으면 콘솔 기록만.
        if (onNetworkError) {
          try {
            onNetworkError(message, err)
          } catch (handlerError) {
            console.error('[interceptors] network error handler failed', handlerError)
          }
        } else if (import.meta.env.DEV) {
          console.warn('[api]', message, err.config?.url ?? '')
        }
      }

      return Promise.reject(err)
    },
  )
}
