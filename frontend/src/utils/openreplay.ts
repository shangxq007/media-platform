export interface OpenReplayConfig {
  projectKey: string
  ingestPoint: string
  resourceBaseHref: string
  captureNetwork: boolean
  captureConsole: boolean
  capturePerformance: boolean
  verbose: boolean
  enabled: boolean
  sessionToken: string
}

export interface OpenReplayUser {
  id?: string
  tenantId?: string
  name?: string
  email?: string
}

export interface FeedbackData {
  type: 'bug' | 'feature' | 'other'
  title: string
  description: string
  severity: 'low' | 'medium' | 'high' | 'critical'
  renderJobId?: string
  promptExecutionId?: string
  customData?: Record<string, unknown>
}

const config: OpenReplayConfig = {
  projectKey: '',
  ingestPoint: 'https://openrelay.yourdomain.com',
  resourceBaseHref: '/',
  captureNetwork: true,
  captureConsole: true,
  capturePerformance: true,
  verbose: false,
  enabled: false,
  sessionToken: ''
}

let initialized = false
let sessionId: string | null = null

interface OpenReplaySdk {
  start?: (options?: Record<string, unknown>) => Promise<unknown>
  setUserID?: (id: string) => void
  setMetadata?: (key: string, value: string) => void
  event?: (name: string, data?: Record<string, unknown>) => void
  handleError?: (type: string, error: Error) => void
  getSessionID?: () => string | null
  getSessionToken?: () => string | null
  addIssue?: (issue: Record<string, unknown>) => void
  track?: (name: string, data?: unknown) => void
  addEvent?: (name: string, data?: unknown) => void
  [key: string]: unknown
}

let openReplaySdk: OpenReplaySdk | null = null

export function initOpenReplay(sdk: OpenReplaySdk, userConfig: Partial<OpenReplayConfig> = {}) {
  Object.assign(config, userConfig)

  if (!config.projectKey || !config.enabled) {
    console.info('[OpenReplay] Disabled - no project key configured')
    return
  }

  try {
    if (sdk && sdk.start) {
      sdk.start?.({
        projectKey: config.projectKey,
        ingestPoint: config.ingestPoint,
        resourceBaseHref: config.resourceBaseHref,
        captureNetwork: config.captureNetwork,
        captureConsole: config.captureConsole,
        capturePerformance: config.capturePerformance,
        verbose: config.verbose,
        privacy: {
          captureText: true,
          captureInput: true,
          captureChange: true,
          captureNetwork: true,
          captureConsole: true,
          capturePerformance: true,
          captureNotifications: true,
          capturePageTitle: true,
          capturePageURL: true,
          captureExceptions: true,
          captureNetworkRequest: true,
          captureNetworkResponse: true,
          textSanitizer: (text: string) => redactText(text),
          inputSanitizer: (text: string) => redactText(text),
          changeSanitizer: (text: string) => redactText(text),
          networkSanitizer: (request: Record<string, unknown>) => sanitizeNetworkData(request),
        }
      })
      openReplaySdk = sdk
      initialized = true
      if (sdk.getSessionID) {
        sessionId = sdk.getSessionID?.() ?? null
      }
      console.info('[OpenReplay] Initialized')
    }
  } catch (e) {
    console.warn('[OpenReplay] Init failed:', e)
  }
}

export function setOpenReplayUser(user: OpenReplayUser) {
  if (!initialized || !openReplaySdk) return
  try {
    openReplaySdk.setUserID?.(user.id || '')
    openReplaySdk.setMetadata?.('tenantId', user.tenantId || '')
    openReplaySdk.setMetadata?.('name', user.name || '')
    openReplaySdk.setMetadata?.('email', user.email || '')
  } catch (e) {
    console.warn('[OpenReplay] setUser failed:', e)
  }
}

export function submitOpenReplayFeedback(feedback: FeedbackData): Promise<boolean> {
  if (!initialized || !openReplaySdk) {
    console.info('[OpenReplay] Feedback (not sent):', feedback.title)
    return Promise.resolve(false)
  }

  try {
    return new Promise((resolve) => {
      try {
        if (openReplaySdk?.addIssue) {
          openReplaySdk.addIssue?.({
            type: feedback.type,
            title: feedback.title,
            description: feedback.description,
            severity: feedback.severity,
            customData: {
              ...feedback.customData,
              renderJobId: feedback.renderJobId,
              promptExecutionId: feedback.promptExecutionId,
              sessionId: sessionId
            }
          })
          resolve(true)
        } else if (openReplaySdk?.track) {
          openReplaySdk.track?.('user_feedback', feedback)
          resolve(true)
        } else {
          resolve(false)
        }
      } catch (e) {
        console.warn('[OpenReplay] submitFeedback failed:', e)
        resolve(false)
      }
    })
  } catch (e) {
    console.warn('[OpenReplay] submitFeedback failed:', e)
    return Promise.resolve(false)
  }
}

export function recordOpenReplayEvent(eventName: string, data?: Record<string, unknown>) {
  if (!initialized || !openReplaySdk) return
  try {
    if (openReplaySdk?.track) {
      openReplaySdk.track?.(eventName, data)
    } else if (openReplaySdk?.addEvent) {
      openReplaySdk.addEvent?.(eventName, data)
    }
  } catch (e) {
    console.warn('[OpenReplay] recordEvent failed:', e)
  }
}

export function getOpenReplaySessionId(): string | null {
  return sessionId
}

export function getOpenReplaySessionUrl(): string | null {
  if (!sessionId || !config.ingestPoint) return null
  return `${config.ingestPoint}/sessions/${sessionId}`
}

function redactText(text: string): string {
  if (!text) return text
  return text
    .replace(/sk-[a-zA-Z0-9]{20,}/g, '[REDACTED_API_KEY]')
    .replace(/password["\s:=]+[^\s"]+/gi, 'password=[REDACTED]')
    .replace(/api[_-]?key["\s:=]+[^\s"]+/gi, 'api_key=[REDACTED]')
    .replace(/token["\s:=]+[^\s"]+/gi, 'token=[REDACTED]')
    .replace(/secret["\s:=]+[^\s"]+/gi, 'secret=[REDACTED]')
}

function sanitizeNetworkData(request: Record<string, unknown>): Record<string, unknown> {
  if (!request) return request
  if (request.headers) {
    const headers = request.headers as Record<string, string>
    const sensitive = ['authorization', 'cookie', 'x-api-key', 'x-auth-token']
    for (const key of Object.keys(headers)) {
      if (sensitive.includes(key.toLowerCase())) {
        headers[key] = '[REDACTED]'
      }
    }
  }
  if (request.body && typeof request.body === 'string') {
    request.body = redactText(request.body)
  }
  return request
}

export function isOpenReplayInitialized(): boolean {
  return initialized
}

export function getOpenReplayConfig(): Readonly<OpenReplayConfig> {
  return config
}
