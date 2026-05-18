import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { injectSmwpBootstrap, SMWP_PROXY_PREFIX } from './src/plugins/smwpProxyBootstrap'

// 프론트 번들 사이즈를 줄이기 위해 무거운 vendor 라이브러리들을
// 별도 청크로 분리한다. 각 페이지가 자주 갱신되어도 vendor 청크는
// 캐시 히트하도록 만드는 효과도 있다.
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // SMWP 정적 리소스 — /swmp-proxy HTML 이 src="/extension/..." 로 요청할 때 404 방지 (수동 테스트용)
      '/extension': { target: 'http://222.108.180.36:11005', changeOrigin: true },
      '/static': { target: 'http://222.108.180.36:11005', changeOrigin: true },
      '/public': { target: 'http://222.108.180.36:11005', changeOrigin: true },
      [SMWP_PROXY_PREFIX]: {
        target: 'http://222.108.180.36:11005',
        changeOrigin: true,
        selfHandleResponse: true,
        rewrite: (path) => path.replace(new RegExp(`^${SMWP_PROXY_PREFIX}`), '') || '/',
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req: IncomingMessage, res: ServerResponse) => {
            const chunks: Buffer[] = []
            proxyRes.on('data', (chunk: Buffer) => chunks.push(chunk))
            proxyRes.on('end', () => {
              const body = Buffer.concat(chunks)
              const type = String(proxyRes.headers['content-type'] ?? '')
              const url = req.url ?? ''
              const isIndexHtml =
                type.includes('text/html') &&
                (url === '/' || url.startsWith('/?') || !url.includes('.'))

              if (!isIndexHtml) {
                res.writeHead(proxyRes.statusCode ?? 200, proxyRes.headers)
                res.end(body)
                return
              }

              const html = injectSmwpBootstrap(body.toString('utf8'))
              const headers = { ...proxyRes.headers }
              delete headers['content-length']
              headers['content-length'] = String(Buffer.byteLength(html))
              res.writeHead(proxyRes.statusCode ?? 200, headers)
              res.end(html)
            })
          })
        },
      },
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
