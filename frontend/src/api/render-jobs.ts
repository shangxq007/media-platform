import { useQuery } from '@tanstack/react-query'
import api from './index'
import { safeApiCall, type SafeApiResult } from './safeApiCall'
import {
  RenderJobSummary,
  RenderWorkspaceScope,
} from '../contracts/app/render-job'

export type { RenderJobSummary, RenderWorkspaceScope }

// --- API ---

function requireValidated<T>(result: SafeApiResult<T>): T {
  if (result.success) return result.data
  const error = new Error(result.error.message)
  error.name = result.error.code
  throw error
}

export const RenderJobsAPI = {
  async getWorkspaceScope(): Promise<RenderWorkspaceScope | null> {
    const result = await safeApiCall(
      RenderWorkspaceScope,
      () => api.get('/me/dashboard').then(r => r.data),
      'RenderJobs.getWorkspaceScope'
    )
    return requireValidated(result)
  },

  async list(tenantId: string, projectId: string): Promise<RenderJobSummary[]> {
    const result = await safeApiCall(
      RenderJobSummary.array(),
      () => api.get(`/tenants/${tenantId}/projects/${projectId}/render-jobs`).then(r => r.data),
      `RenderJobs.list(${tenantId}, ${projectId})`
    )
    return requireValidated(result)
  },

  async get(tenantId: string, projectId: string, jobId: string): Promise<RenderJobSummary | null> {
    const result = await safeApiCall(
      RenderJobSummary,
      () => api.get(`/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}`).then(r => r.data),
      `RenderJobs.get(${tenantId}, ${projectId}, ${jobId})`
    )
    return requireValidated(result)
  },

}

// --- Hooks ---

export function useRenderWorkspaceScope() {
  return useQuery({
    queryKey: ['render-workspace-scope'],
    queryFn: () => RenderJobsAPI.getWorkspaceScope(),
    staleTime: 60_000,
  })
}

export function useRenderJobs(tenantId: string | null, projectId: string | null) {
  return useQuery({
    queryKey: ['render-jobs', tenantId, projectId],
    queryFn: () => RenderJobsAPI.list(tenantId!, projectId!),
    enabled: Boolean(tenantId && projectId),
    refetchInterval: 10000,
  })
}

export function useRenderJob(tenantId: string | null, projectId: string | null, jobId: string | null) {
  return useQuery({
    queryKey: ['render-job', tenantId, projectId, jobId],
    queryFn: () => RenderJobsAPI.get(tenantId!, projectId!, jobId!),
    enabled: Boolean(tenantId && projectId && jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED' || status === 'REJECTED'
        ? false
        : 5000
    },
  })
}
