import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'

// Import monitoring utilities
import { initSentry, setSentryUser, captureSentryException } from './utils/sentry'
import { initOpenReplay, setOpenReplayUser } from './utils/openreplay'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// Initialize monitoring SDKs
function initMonitoring() {
  const tenantId = localStorage.getItem('tenant_id') || 'tenant-1'
  const userId = localStorage.getItem('user_id') || 'user-1'
  setSentryUser({ id: userId, tenantId })
  setOpenReplayUser({ id: userId, tenantId })

  // Sentry initialization - lazy loaded (runtime import to avoid Vite pre-resolution)
  const sentryDsn = (import.meta as any).env?.VITE_SENTRY_DSN || ''
  if (sentryDsn) {
    const sentryImport = new Function('m', 'return import(m)')
    sentryImport('@sentry/vue').then((SentryVue: any) => {
      if (SentryVue && SentryVue.init) {
        initSentry(SentryVue, { dsn: sentryDsn })
        SentryVue.init({ app, dsn: sentryDsn, tracesSampleRate: 1.0 })
      }
    }).catch(() => console.info('[Sentry] @sentry/vue not available'))
  }

  // OpenReplay initialization - lazy loaded (runtime import to avoid Vite pre-resolution)
  const orKey = (import.meta as any).env?.VITE_OPENREPLAY_PROJECT_KEY || ''
  if (orKey) {
    const dynamicImport = new Function('m', 'return import(m)')
    dynamicImport('@openreplay/tracker').then((Tracker: any) => {
      if (Tracker && Tracker.default) {
        const tracker = new Tracker.default({ projectKey: orKey })
        initOpenReplay(tracker, { projectKey: orKey, enabled: true })
      }
    }).catch(() => console.info('[OpenReplay] @openreplay/tracker not available'))
  }
}

initMonitoring()

app.mount('#app')

// Global error handler
app.config.errorHandler = (err: any, instance: any, info: string) => {
  console.error('[Vue Error]', err, info)
  captureSentryException(err instanceof Error ? err : new Error(String(err)), {
    component: instance?.$options?.name || 'unknown',
    info
  })
}

export { app }
