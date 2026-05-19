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
app.mount('#app')
