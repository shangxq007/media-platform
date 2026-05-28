import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'
import { bootstrapDevAuth } from './api/index'
import { useAuthStore } from './stores/auth'
import { isOidcEnabled } from './auth/oidcConfig'

// Import monitoring utilities
import { initSentry, setSentryUser, captureSentryException } from './utils/sentry'
import { initOpenReplay, setOpenReplayUser } from './utils/openreplay'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// Initialize monitoring SDKs
function initMonitoring() {
  // NOTE: tenantId is NOT trusted from localStorage — it's only used here for
  // monitoring context (Sentry/OpenReplay). The real tenant is resolved server-side
  // from the authentication token. An empty string is used as fallback, not 'tenant-1'.
  const tenantId = localStorage.getItem('tenant_id') || ''
  const userId = localStorage.getItem('user_id') || ''
  setSentryUser({ id: userId, tenantId })
  setOpenReplayUser({ id: userId, tenantId })

  // Sentry initialization - lazy loaded via dynamic import (externalized by Vite)
  const sentryDsn = import.meta.env?.VITE_SENTRY_DSN || ''
  if (sentryDsn) {
    import('@sentry/vue').then((SentryVue) => {
      const sdk = SentryVue as unknown as Record<string, unknown>
      if (sdk && sdk.init) {
        initSentry(sdk, { dsn: sentryDsn })
        ;(sdk.init as (opts: Record<string, unknown>) => void)({ app, dsn: sentryDsn, tracesSampleRate: 1.0 })
      }
    }).catch(() => console.info('[Sentry] @sentry/vue not available'))
  }

  // OpenReplay initialization - lazy loaded via dynamic import (externalized by Vite)
  const orKey = import.meta.env?.VITE_OPENREPLAY_PROJECT_KEY || ''
  if (orKey) {
    import('@openreplay/tracker').then((Tracker) => {
      const trackerModule = Tracker as unknown as { default?: new (opts: Record<string, unknown>) => Record<string, unknown> }
      if (trackerModule && trackerModule.default) {
        const tracker = new trackerModule.default({ projectKey: orKey })
        initOpenReplay(tracker, { projectKey: orKey, enabled: true })
      }
    }).catch(() => console.info('[OpenReplay] @openreplay/tracker not available'))
  }
}

initMonitoring()

async function bootstrapApp() {
  const auth = useAuthStore()
  await auth.init()
  if (!isOidcEnabled()) {
    await bootstrapDevAuth()
  }
  app.mount('#app')
}

bootstrapApp()

// Global error handler
app.config.errorHandler = (err: unknown, instance: { $options?: { name?: string } } | null, info: string) => {
  console.error('[Vue Error]', err, info)
  captureSentryException(err instanceof Error ? err : new Error(String(err)), {
    component: instance?.$options?.name || 'unknown',
    info
  })
}

export { app }
