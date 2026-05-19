/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_USE_MOCK_ALARMS?: string
  readonly VITE_SWMP_DEFAULT_URL?: string
  /** true 이면 라인/설비/대시보드 API 를 1초마다 폴링 (로컬 데모용, develop 키 순서와 병합) */
  readonly VITE_REALTIME_DEMO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
