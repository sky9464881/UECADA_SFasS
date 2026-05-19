/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_USE_MOCK_ALARMS?: string
  /** true 이면 라인/설비/대시보드 API 를 1초마다 폴링 */
  readonly VITE_REALTIME_DEMO?: string
  readonly VITE_SWMP_DEFAULT_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
