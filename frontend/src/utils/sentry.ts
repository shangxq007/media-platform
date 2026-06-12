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

const config: SentryConfig = {
  dsn: '',
  environment: 'development',
  release: '0.1.0',
  tracesSampleRate: 1.0,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,
  enabled: false
}

let initialized = false

interface SentrySdk {
  init?: (options: Record<string, unknown>) => void
  getReplayIntegrations?: () => unknown[]
  getReplayId?: () => string | null
  setUser?: (user: Record<string, unknown>) => void
  setContext?: (key: string, context: Record<string, unknown>) => void
  setTag?: (key: string, value: string) => void
  captureException?: (error: Error, options?: Record<string, unknown>) => void
  captureMessage?: (message: string, level?: string) => void
  [key: string]: unknown
}

let sentrySdk: SentrySdk | null = null

export function initSentry(sdk: SentrySdk, userConfig: Partial<SentryConfig> = {}) {
  Object.assign(config, userConfig)
  sentrySdk = sdk

  if (!config.dsn || !config.enabled) {
    console.info('[Sentry] Disabled - no DSN configured')
    return
  }

  try {
    if (sdk && sdk.init) {
      sdk.init({
        dsn: config.dsn,
        environment: config.environment,
        release: config.release,
        tracesSampleRate: config.tracesSampleRate,
        replaysSessionSampleRate: config.replaysSessionSampleRate,
        replaysOnErrorSampleRate: config.replaysOnErrorSampleRate,
        integrations: sdk.getReplayIntegrations ? sdk.getReplayIntegrations() : [],
        beforeSend(event: Record<string, unknown>) {
          return sanitizeEvent(event)
        },
        beforeBreadcrumb(breadcrumb: Record<string, unknown>) {
          return sanitizeBreadcrumb(breadcrumb)
        }
      })
      initialized = true
      console.info('[Sentry] Initialized')
    }
  } catch (e) {
    console.warn('[Sentry] Init failed:', e)
  }
}

export function setSentryUser(user: SentryUser) {
  if (!initialized || !sentrySdk) return
  try {
    sentrySdk.setUser?.(user as Record<string, unknown>)
  } catch (e) {
    console.warn('[Sentry] setUser failed:', e)
  }
}

export function setSentryContext(context: SentryContext) {
  if (!initialized || !sentrySdk) return
  try {
    sentrySdk.setContext?.('mediaPlatform', context)
  } catch (e) {
    console.warn('[Sentry] setContext failed:', e)
  }
}

export function setSentryTag(key: string, value: string) {
  if (!initialized || !sentrySdk) return
  try {
    sentrySdk.setTag?.(key, value)
  } catch (e) {
    console.warn('[Sentry] setTag failed:', e)
  }
}

export function captureSentryException(error: Error, context?: SentryContext) {
  if (!initialized || !sentrySdk) {
    console.error('[Sentry] Exception (not sent):', error.message)
    return
  }
  try {
    sentrySdk.captureException?.(error, {
      contexts: context ? { mediaPlatform: context } : undefined
    })
  } catch (e) {
    console.warn('[Sentry] captureException failed:', e)
  }
}

export function captureSentryMessage(message: string, level: 'info' | 'warning' | 'error' = 'info') {
  if (!initialized || !sentrySdk) return
  try {
    sentrySdk.captureMessage?.(message, level)
  } catch (e) {
    console.warn('[Sentry] captureMessage failed:', e)
  }
}

export function getSentryReplayId(): string | null {
  if (!initialized || !sentrySdk) return null
  try {
    if (sentrySdk.getReplayId) return sentrySdk.getReplayId()
    return null
  } catch {
    return null
  }
}

function sanitizeEvent(event: Record<string, unknown>): Record<string, unknown> {
  if (!event) return event
  if (event.request) {
    const request = event.request as Record<string, unknown>
    if (request.headers) {
      redactHeaders(request.headers as Record<string, string>)
    }
    if (request.data) {
      request.data = redactSensitiveData(request.data)
    }
  }
  if (event.exception) {
    const exception = event.exception as Record<string, unknown>
    if (exception.values) {
      const values = exception.values as Record<string, unknown>[]
      for (const value of values) {
        if (value.stacktrace) {
          const stacktrace = value.stacktrace as Record<string, unknown>
          if (stacktrace.frames) {
            const frames = stacktrace.frames as Record<string, unknown>[]
            for (const frame of frames) {
              if (frame.vars) {
                frame.vars = redactSensitiveData(frame.vars)
              }
            }
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

export function isSentryInitialized(): boolean {
  return initialized
}

export function getSentryConfig(): Readonly<SentryConfig> {
  return config
}
