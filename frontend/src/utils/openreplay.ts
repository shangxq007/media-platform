import { ref, readonly } from 'vue'

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

const config = ref<OpenReplayConfig>({
  projectKey: '',
  ingestPoint: 'https://openrelay.yourdomain.com',
  resourceBaseHref: '/',
  captureNetwork: true,
  captureConsole: true,
  capturePerformance: true,
  verbose: false,
  enabled: false,
  sessionToken: ''
})

const initialized = ref(false)
const sessionId = ref<string | null>(null)
let openReplaySdk: any = null

export function initOpenReplay(sdk: any, userConfig: Partial<OpenReplayConfig> = {}) {
  Object.assign(config.value, userConfig)

  if (!config.value.projectKey || !config.value.enabled) {
    console.info('[OpenReplay] Disabled - no project key configured')
    return
  }

  try {
    if (sdk && sdk.start) {
      sdk.start({
        projectKey: config.value.projectKey,
        ingestPoint: config.value.ingestPoint,
        resourceBaseHref: config.value.resourceBaseHref,
        captureNetwork: config.value.captureNetwork,
        captureConsole: config.value.captureConsole,
        capturePerformance: config.value.capturePerformance,
        verbose: config.value.verbose,
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
          networkSanitizer: (request: any) => sanitizeNetworkData(request),
        }
      })
      openReplaySdk = sdk
      initialized.value = true
      if (sdk.getSessionID) {
        sessionId.value = sdk.getSessionID()
      }
      console.info('[OpenReplay] Initialized')
    }
  } catch (e) {
    console.warn('[OpenReplay] Init failed:', e)
  }
}

export function setOpenReplayUser(user: OpenReplayUser) {
  if (!initialized.value || !openReplaySdk) return
  try {
    if (openReplaySdk.setUserID) {
      openReplaySdk.setUserID(user.id || '')
    }
    if (openReplaySdk.setMetadata) {
      openReplaySdk.setMetadata({
        tenantId: user.tenantId || '',
        name: user.name || '',
        email: user.email || ''
      })
    }
  } catch (e) {
    console.warn('[OpenReplay] setUser failed:', e)
  }
}

export function submitOpenReplayFeedback(feedback: FeedbackData): Promise<boolean> {
  if (!initialized.value || !openReplaySdk) {
    console.info('[OpenReplay] Feedback (not sent):', feedback.title)
    return Promise.resolve(false)
  }

  try {
    return new Promise((resolve) => {
      try {
        if (openReplaySdk.addIssue) {
          openReplaySdk.addIssue({
            type: feedback.type,
            title: feedback.title,
            description: feedback.description,
            severity: feedback.severity,
            customData: {
              ...feedback.customData,
              renderJobId: feedback.renderJobId,
              promptExecutionId: feedback.promptExecutionId,
              sessionId: sessionId.value
            }
          })
          resolve(true)
        } else if (openReplaySdk.track) {
          openReplaySdk.track('user_feedback', feedback)
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
  if (!initialized.value || !openReplaySdk) return
  try {
    if (openReplaySdk.track) {
      openReplaySdk.track(eventName, data)
    } else if (openReplaySdk.addEvent) {
      openReplaySdk.addEvent(eventName, data)
    }
  } catch (e) {
    console.warn('[OpenReplay] recordEvent failed:', e)
  }
}

export function getOpenReplaySessionId(): string | null {
  return sessionId.value
}

export function getOpenReplaySessionUrl(): string | null {
  if (!sessionId.value || !config.value.ingestPoint) return null
  return `${config.value.ingestPoint}/sessions/${sessionId.value}`
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

function sanitizeNetworkData(request: any): any {
  if (!request) return request
  if (request.headers) {
    const sensitive = ['authorization', 'cookie', 'x-api-key', 'x-auth-token']
    for (const key of Object.keys(request.headers)) {
      if (sensitive.includes(key.toLowerCase())) {
        request.headers[key] = '[REDACTED]'
      }
    }
  }
  if (request.body && typeof request.body === 'string') {
    request.body = redactText(request.body)
  }
  return request
}

export function isOpenReplayInitialized() {
  return readonly(initialized)
}

export function getOpenReplayConfig() {
  return readonly(config)
}
