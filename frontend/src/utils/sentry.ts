import { ref, readonly } from 'vue'

export interface SentryConfig {
  dsn: string
  environment: string
  release: string
  tracesSampleRate: number
  replaysSessionSampleRate: number
  replaysOnErrorSampleRate: number
  enabled: boolean
}

export interface SentryUser {
  id?: string
  tenantId?: string
  email?: string
  username?: string
}

export interface SentryContext {
  renderJobId?: string
  promptExecutionId?: string
  providerKey?: string
  workerId?: string
  [key: string]: unknown
}

const config = ref<SentryConfig>({
  dsn: '',
  environment: 'development',
  release: '0.1.0',
  tracesSampleRate: 1.0,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,
  enabled: false
})

const initialized = ref(false)
let sentrySdk: Record<string, unknown> | null = null

export function initSentry(sdk: Record<string, unknown>, userConfig: Partial<SentryConfig> = {}) {
  Object.assign(config.value, userConfig)
  sentrySdk = sdk

  if (!config.value.dsn || !config.value.enabled) {
    console.info('[Sentry] Disabled - no DSN configured')
    return
  }

  try {
    if (sdk && sdk.init) {
      sdk.init({
        dsn: config.value.dsn,
        environment: config.value.environment,
        release: config.value.release,
        tracesSampleRate: config.value.tracesSampleRate,
        replaysSessionSampleRate: config.value.replaysSessionSampleRate,
        replaysOnErrorSampleRate: config.value.replaysOnErrorSampleRate,
        integrations: sdk.getReplayIntegrations ? sdk.getReplayIntegrations() : [],
        beforeSend(event: Record<string, unknown>) {
          return sanitizeEvent(event)
        },
        beforeBreadcrumb(breadcrumb: Record<string, unknown>) {
          return sanitizeBreadcrumb(breadcrumb)
        }
      })
      initialized.value = true
      console.info('[Sentry] Initialized')
    }
  } catch (e) {
    console.warn('[Sentry] Init failed:', e)
  }
}

export function setSentryUser(user: SentryUser) {
  if (!initialized.value || !sentrySdk) return
  try {
    sentrySdk.setUser(user)
  } catch (e) {
    console.warn('[Sentry] setUser failed:', e)
  }
}

export function setSentryContext(context: SentryContext) {
  if (!initialized.value || !sentrySdk) return
  try {
    sentrySdk.setContext('mediaPlatform', context)
  } catch (e) {
    console.warn('[Sentry] setContext failed:', e)
  }
}

export function setSentryTag(key: string, value: string) {
  if (!initialized.value || !sentrySdk) return
  try {
    sentrySdk.setTag(key, value)
  } catch (e) {
    console.warn('[Sentry] setTag failed:', e)
  }
}

export function captureSentryException(error: Error, context?: SentryContext) {
  if (!initialized.value || !sentrySdk) {
    console.error('[Sentry] Exception (not sent):', error.message)
    return
  }
  try {
    sentrySdk.captureException(error, {
      contexts: context ? { mediaPlatform: context } : undefined
    })
  } catch (e) {
    console.warn('[Sentry] captureException failed:', e)
  }
}

export function captureSentryMessage(message: string, level: 'info' | 'warning' | 'error' = 'info') {
  if (!initialized.value || !sentrySdk) return
  try {
    sentrySdk.captureMessage(message, level)
  } catch (e) {
    console.warn('[Sentry] captureMessage failed:', e)
  }
}

export function getSentryReplayId(): string | null {
  if (!initialized.value || !sentrySdk) return null
  try {
    if (sentrySdk.getReplayId) return sentrySdk.getReplayId()
    return null
  } catch {
    return null
  }
}

function sanitizeEvent(event: Record<string, unknown>): Record<string, unknown> {
  if (!event) return event
  // Redact sensitive data from event
  if (event.request) {
    if (event.request.headers) {
      redactHeaders(event.request.headers)
    }
    if (event.request.data) {
      event.request.data = redactSensitiveData(event.request.data)
    }
  }
  if (event.exception?.values) {
    for (const value of event.exception.values) {
      if (value.stacktrace?.frames) {
        for (const frame of value.stacktrace.frames) {
          if (frame.vars) {
            frame.vars = redactSensitiveData(frame.vars)
          }
        }
      }
    }
  }
  return event
}

function sanitizeBreadcrumb(breadcrumb: Record<string, unknown>): Record<string, unknown> {
  if (!breadcrumb) return breadcrumb
  if (breadcrumb.data) {
    breadcrumb.data = redactSensitiveData(breadcrumb.data)
  }
  return breadcrumb
}

function redactHeaders(headers: Record<string, string>) {
  const sensitive = ['authorization', 'cookie', 'x-api-key', 'x-auth-token']
  for (const key of Object.keys(headers)) {
    if (sensitive.includes(key.toLowerCase())) {
      headers[key] = '[REDACTED]'
    }
  }
}

function redactSensitiveData(data: unknown): unknown {
  if (typeof data === 'string') {
    // Redact potential secrets in strings
    return data
      .replace(/sk-[a-zA-Z0-9]{20,}/g, '[REDACTED_API_KEY]')
      .replace(/password["\s:=]+[^\s"]+/gi, 'password=[REDACTED]')
      .replace(/api[_-]?key["\s:=]+[^\s"]+/gi, 'api_key=[REDACTED]')
      .replace(/token["\s:=]+[^\s"]+/gi, 'token=[REDACTED]')
      .replace(/secret["\s:=]+[^\s"]+/gi, 'secret=[REDACTED]')
  }
  if (typeof data === 'object' && data !== null) {
    const result: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(data)) {
      const lowerKey = key.toLowerCase()
      if (lowerKey.includes('password') || lowerKey.includes('secret')
          || lowerKey.includes('api_key') || lowerKey.includes('apikey')
          || lowerKey.includes('token') || lowerKey.includes('credential')) {
        result[key] = '[REDACTED]'
      } else if (typeof value === 'object') {
        result[key] = redactSensitiveData(value)
      } else {
        result[key] = value
      }
    }
    return result
  }
  return data
}

export function isSentryInitialized() {
  return readonly(initialized)
}

export function getSentryConfig() {
  return readonly(config)
}
