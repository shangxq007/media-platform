import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  root,
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: path.resolve(__dirname, '../platform-app/src/main/resources/static'),
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
    rollupOptions: {
      external: ['@sentry/vue', '@openreplay/tracker'],
      output: {
        manualChunks: {
          vendor: ['vue', 'pinia', 'vue-router'],
          timeline: ['@/stores/timeline', '@/components/timeline/TimelineEditor.vue'],
          export: ['@/components/export/ExportPanel.vue', '@/stores/project']
        }
      }
    }
  }
})
