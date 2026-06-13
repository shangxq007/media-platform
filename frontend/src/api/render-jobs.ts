import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from './index'
import { safeApiCall, safeApiCallList } from './safeApiCall'
import {
  RenderJobSummarySchema,
  RenderJobArtifactSchema,
  type RenderJobSummary,
  type RenderJobArtifact,
} from './contracts'

export type { RenderJobSummary, RenderJobArtifact }

// --- API ---

const RenderJobsAPI = {
  async list(): Promise<RenderJobSummary[]> {
    const result = await safeApiCallList(
      RenderJobSummarySchema,
      () => api.get('/render/jobs').then(r => r.data),
      'RenderJobs.list'
    )
    return result.success ? result.data : []
  },

  async get(jobId: string): Promise<RenderJobSummary | null> {
    const result = await safeApiCall(
      RenderJobSummarySchema,
      () => api.get(`/render/jobs/${jobId}`).then(r => r.data),
      `RenderJobs.get(${jobId})`
    )
    return result.success ? result.data : null
  },

  async getArtifacts(jobId: string): Promise<RenderJobArtifact[]> {
    const result = await safeApiCallList(
      RenderJobArtifactSchema,
      () => api.get(`/render/jobs/${jobId}/artifacts`).then(r => r.data),
      `RenderJobs.getArtifacts(${jobId})`
    )
    return result.success ? result.data : []
  },

  async retry(jobId: string): Promise<{ jobId: string; status: string }> {
    const { data } = await api.post(`/render/jobs/${jobId}/retry`)
    return data
  },

  async cancel(jobId: string): Promise<{ jobId: string; status: string }> {
    const { data } = await api.post(`/render/jobs/${jobId}/cancel`)
    return data
  },
}

// --- Hooks ---

export function useRenderJobs() {
  return useQuery({
    queryKey: ['render-jobs'],
    queryFn: () => RenderJobsAPI.list(),
    refetchInterval: 10000,
  })
}

export function useRenderJob(jobId: string | null) {
  return useQuery({
    queryKey: ['render-job', jobId],
    queryFn: () => RenderJobsAPI.get(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED' ? false : 5000
    },
  })
}

export function useRenderJobArtifacts(jobId: string | null) {
  return useQuery({
    queryKey: ['render-job-artifacts', jobId],
    queryFn: () => RenderJobsAPI.getArtifacts(jobId!),
    enabled: !!jobId,
  })
}

export function useRetryRenderJob() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (jobId: string) => RenderJobsAPI.retry(jobId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['render-jobs'] })
    },
  })
}

export function useCancelRenderJob() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (jobId: string) => RenderJobsAPI.cancel(jobId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['render-jobs'] })
    },
  })
}
