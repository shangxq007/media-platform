import axios from 'axios'
import type { Project, RenderJob, UserBehaviorEvent, ErrorResponse } from '@/types'
import { getErrorMessage } from '@/utils/i18n'

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.request.use(config => {
  const tenant = localStorage.getItem('tenant_id') || 'tenant-1'
  config.headers['X-Tenant-ID'] = tenant
  return config
})

api.interceptors.response.use(
  resp => resp,
  err => {
    const errorData = err.response?.data as ErrorResponse | undefined
    if (errorData?.errorCode) {
      errorData.message = getErrorMessage(errorData.errorCode)
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

export const RenderAPI = {
  async createJob(projectId: string, settings: Record<string, string>): Promise<RenderJob> {
    const { data } = await api.post('/render/jobs', {
      projectId,
      ...settings
    })
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

export const EntitlementAPI = {
  async getCapabilities() {
    const { data } = await api.get('/entitlements/me/capabilities')
    return data
  },
  async validateExport(preset: string, outputFormat: string, estimatedDurationSeconds?: number) {
    const { data } = await api.post('/render/export/validate', {
      preset,
      outputFormat,
      estimatedDurationSeconds
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
