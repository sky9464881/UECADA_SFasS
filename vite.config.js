import { defineConfig } from "vite";

/** 루트 `index.html`이 `dist/` 빌드 산출물을 로드합니다. Vue 소스가 없을 때 로컬 미리보기용입니다. */
export default defineConfig({
  server: {
    port: 5173,
    strictPort: false,
  },
  preview: {
    port: 4173,
  },
});
