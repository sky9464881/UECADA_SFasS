import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 프론트 번들 사이즈를 줄이기 위해 무거운 vendor 라이브러리들을
// 별도 청크로 분리한다. 각 페이지가 자주 갱신되어도 vendor 청크는
// 캐시 히트하도록 만드는 효과도 있다.
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/health': {
        target: process.env.VITE_DEV_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('apexcharts') || id.includes('vue3-apexcharts')) {
            return 'vendor-apexcharts'
          }
          if (id.includes('echarts') || id.includes('vue-echarts') || id.includes('zrender')) {
            return 'vendor-echarts'
          }
          if (id.includes('lucide-vue-next')) {
            return 'vendor-lucide'
          }
          if (
            id.includes('@tanstack/vue-query') ||
            id.includes('@tanstack/query-core')
          ) {
            return 'vendor-vue-query'
          }
          if (id.includes('vue-router') || id.includes('pinia')) {
            return 'vendor-vue-libs'
          }
          if (id.includes('node_modules/vue/') || id.includes('@vue/')) {
            return 'vendor-vue'
          }
          if (id.includes('axios')) {
            return 'vendor-axios'
          }
          return 'vendor'
        },
      },
    },
  },
})
