import { getTenantId } from '@/utils/tenant'
import axios from 'axios'
import type { Project, RenderJob, UserBehaviorEvent, ErrorResponse, EffectPack } from '@/types'
import { getErrorMessage } from '@/utils/i18n'
import { isOidcEnabled } from '@/auth/oidcConfig'
import { getAccessToken, signInRedirect } from '@/auth/oidcClient'

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' }
})

let devAuthBootstrapped = false

/** Attach dev JWT when backend runs with app.security.enabled=true and profile dev. */
export async function bootstrapDevAuth(): Promise<void> {
  if (isOidcEnabled()) return
  if (devAuthBootstrapped) return
  devAuthBootstrapped = true
  const cached = localStorage.getItem('dev_access_token')
  if (cached) {
    api.defaults.headers.common.Authorization = `Bearer ${cached}`
    return
  }
  try {
    const { data } = await axios.post('/api/v1/dev/auth/token', { userId: 'user-1' })
    if (data?.accessToken) {
      localStorage.setItem('dev_access_token', data.accessToken)
      api.defaults.headers.common.Authorization = `Bearer ${data.accessToken}`
    }
  } catch {
    /* security disabled or dev endpoint unavailable */
  }
}

api.interceptors.request.use(async config => {
  if (isOidcEnabled()) {
    const token = await getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  } else if (import.meta.env.DEV) {
    await bootstrapDevAuth()
  }
  return config
})

api.interceptors.response.use(
  resp => resp,
  async err => {
    const errorData = err.response?.data as ErrorResponse | undefined
    if (errorData?.errorCode) {
      errorData.message = getErrorMessage(errorData.errorCode)
    }
    if (
      err.response?.status === 401 &&
      isOidcEnabled() &&
      !window.location.pathname.startsWith('/oauth/callback')
    ) {
      sessionStorage.setItem('oidc_post_login_redirect', window.location.pathname + window.location.search)
      await signInRedirect()
    }
    console.error('API error:', err.response?.status, errorData || err.message)
    return Promise.reject(err)
  }
)

export const ProjectAPI = {
  async list(): Promise<Project[]> {
    const { data } = await api.get('/projects')
    return data
  },
  async get(id: string): Promise<Project> {
    const { data } = await api.get(`/projects/${id}`)
    return data
  },
  async create(name: string, description?: string): Promise<Project> {
    const { data } = await api.post('/projects', { name, description })
    return data
  }
}

export { IncrementalRenderAPI } from './render-incremental'
export { AiTimelineAPI } from './ai-timeline'
export type {
  AiTimelineEditRequest,
  AiTimelineEditResponse,
  AiProposalDto,
  TimelineInternalPreviewResponse,
} from './ai-timeline'
export { DeliveryAPI } from './delivery'
export type {
  DeliveryDestination,
  DeliveryJob,
  DeliveryProtocol,
  CreateDeliveryDestinationRequest,
  CreateDeliveryPolicyRequest,
  ProbeDestinationResponse,
  DeliveryPolicy
} from './delivery'
export type {
  IncrementalRenderPlanResponse,
  GenerateIncrementalPlanRequest,
  SubmitIncrementalRenderRequest,
  RenderCachePresignResponse,
  RenderCacheEntryPresign,
  RenderCacheCleanupResponse
} from './render-incremental'

export { TimelineSyncAPI } from './timelineSync'
export { TimelineRevisionAPI } from './timelineRevision'
export type {
  TimelineSyncPushResponse,
  TimelineSyncPullResponse,
  TimelineSyncSyncResponse
} from './timelineSync'

export const RenderAPI = {
  async saveTimelineSnapshot(
    projectId: string,
    editorTimeline: unknown,
    options?: { ensureInternal?: boolean }
  ): Promise<{ snapshotId: string; projectId: string }> {
    const { data } = await api.post('/render/timeline-snapshots', {
      projectId,
      editorTimeline,
      schemaVersion: '2.0.0',
      ensureInternal: options?.ensureInternal === true
    })
    return data
  },
  async createJob(projectId: string, settings: Record<string, string> & { timelineSnapshotId?: string }): Promise<RenderJob> {
    const tenantId = getTenantId() || 'default'
    const { data } = await api.post(`/tenants/${tenantId}/projects/${projectId}/render-jobs`, {
      projectId,
      timelineSnapshotId: settings.timelineSnapshotId,
      profile: settings.profile,
    })
    return data
  },
  async executeJob(jobId: string): Promise<{ jobId: string; status: string }> {
    const { data } = await api.post(`/render/jobs/${jobId}/execute`)
    return data
  },
  async getJob(jobId: string): Promise<RenderJob> {
    const { data } = await api.get(`/render/jobs/${jobId}`)
    return data
  },
  async listJobs(): Promise<RenderJob[]> {
    const { data } = await api.get('/render/jobs')
    return data
  }
}

export const AnalyticsAPI = {
  async trackEvent(userId: string, eventType: string, action?: string, resourceType?: string, metadata?: Record<string, string>): Promise<UserBehaviorEvent> {
    const { data } = await api.post('/analytics/events', {
      userId, eventType, action, resourceType, metadata
    })
    return data
  },
  async getProfile(userId: string) {
    const { data } = await api.get(`/analytics/profiles/${userId}`)
    return data
  },
  async getHabits(userId: string) {
    const { data } = await api.get(`/analytics/habits/${userId}`)
    return data
  }
}

// -------------------------------------------------------------------------
// Prompt 44: Cost, Entitlement, Anomaly API clients
// -------------------------------------------------------------------------

export const EffectPackAPI = {
  async list(): Promise<EffectPack[]> {
    const { data } = await api.get('/effect-packs')
    return data
  },
  async get(packId: string, version: string): Promise<EffectPack> {
    const { data } = await api.get(`/effect-packs/${packId}/versions/${version}`)
    return data
  },
  async create(pack: EffectPack): Promise<EffectPack> {
    const { data } = await api.post('/effect-packs', pack)
    return data
  },
  async update(packId: string, version: string, pack: EffectPack): Promise<EffectPack> {
    const { data } = await api.put(`/effect-packs/${packId}/versions/${version}`, {
      name: pack.name,
      description: pack.description,
      author: pack.author,
      compatibility: pack.compatibility,
      allowedTiers: pack.allowedTiers,
      effects: pack.effects
    })
    return data
  },
  async remove(packId: string, version: string): Promise<void> {
    await api.delete(`/effect-packs/${packId}/versions/${version}`)
  }
}

export const EntitlementAPI = {
  async getCapabilities() {
    const { data } = await api.get('/entitlements/me/capabilities')
    return data
  },
  async validateExport(
    preset: string,
    outputFormat: string,
    estimatedDurationSeconds?: number,
    options?: { effectKeys?: string[]; timelineJson?: string }
  ) {
    const { data } = await api.post('/render/export/validate', {
      preset,
      outputFormat,
      estimatedDurationSeconds,
      effectKeys: options?.effectKeys,
      timelineJson: options?.timelineJson,
    })
    return data
  }
}

export const CostAPI = {
  async getBudgetStatus(tenantId: string) {
    const { data } = await api.get(`/billing/tenants/${tenantId}/budget`)
    return data
  },
  async estimateCost(providerKey: string, preset: string, outputFormat: string, durationSeconds: number) {
    const { data } = await api.post('/billing/cost/estimate', {
      providerKey, preset, outputFormat, durationSeconds
    })
    return data
  }
}

export const UsageAlertAPI = {
  async getAlerts(tenantId: string, userId: string) {
    const { data } = await api.get(`/audit/usage-alerts`, {
      params: { tenantId, userId }
    })
    return data
  },
  async getRiskProfile(tenantId: string, userId: string) {
    const { data } = await api.get(`/audit/risk-profile`, {
      params: { tenantId, userId }
    })
    return data
  }
}

export { PromptAPI } from './prompt'

export default api
