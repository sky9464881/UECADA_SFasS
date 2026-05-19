import { createApp } from 'vue'
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { api } from './api/client'
import { attachAuthInterceptors, setAuthRouter } from './api/interceptors'
import './style.css'

const pinia = createPinia()
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
    },
  },
})

const app = createApp(App)
app.use(pinia)
setAuthRouter(router)
attachAuthInterceptors(api)
app.use(router)
app.use(VueQueryPlugin, { queryClient })
// vue3-apexcharts 는 초기 번들에 1MB+를 추가하므로 글로벌 등록을 제거하고,
// 실제 사용 컴포넌트(DashboardPage) 내부에서 defineAsyncComponent 로 lazy load 한다.
app.mount('#app')
