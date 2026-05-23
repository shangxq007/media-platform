import api from './index'
import type { RenderJob } from '@/types'

/** Internal Timeline 1.0 增量渲染 REST 客户端 */
export interface IncrementalPlanTask {
  taskId: string
  name: string
  type: string
  backend: string
  dependsOn: string[]
  cacheKey: string
  parameters?: Record<string, string>
}

export interface IncrementalReuseArtifact {
  artifactId: string
  taskId: string
  uri: string
  cacheKey: string
}

export interface IncrementalRenderPlanResponse {
  mode: string
  fullReRenderRequired: boolean
  baseRevision: number
  targetRevision: number
  executeTaskIds: string[]
  reuseTaskIds: string[]
  dirtyScopes: string[]
  changeCount: number
  planId: string
  timelineId: string
  finalComposer: string
  metadata: Record<string, unknown>
  tasks: IncrementalPlanTask[]
  reuse: IncrementalReuseArtifact[]
}

export interface GenerateIncrementalPlanRequest {
  newTimelineJson: string
  oldTimelineJson?: string
  profile?: string
  tier?: string
  outputFormat?: string
  baseJobId?: string
  reuseArtifacts?: IncrementalReuseArtifact[]
}

export interface SubmitIncrementalRenderRequest {
  tenantId: string
  projectId: string
  prompt?: string
  profile?: string
  timelineSnapshotId?: string
  baseJobId?: string
  targetSegmentIds?: string[]
  editSessionId?: string
  aiEditIntent?: string
  aiEditInstruction?: string
}

export interface RenderCacheEntryPresign {
  cacheKey: string
  segmentId?: string | null
  taskId: string
  kind: string
  sourceUri: string
  downloadUrl: string
  expiresInSeconds: number
}

export interface RenderCachePresignResponse {
  jobId: string
  entries: RenderCacheEntryPresign[]
}

export interface RenderCacheCleanupResponse {
  jobsScanned: number
  objectsDeleted: number
  jobsUpdated: number
}

export const IncrementalRenderAPI = {
  async previewPlan(
    tenantId: string,
    projectId: string,
    body: GenerateIncrementalPlanRequest
  ): Promise<IncrementalRenderPlanResponse> {
    const { data } = await api.post<IncrementalRenderPlanResponse>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/incremental/plan`,
      body
    )
    return data
  },

  async submitJob(
    tenantId: string,
    projectId: string,
    body: SubmitIncrementalRenderRequest
  ): Promise<{ jobId: string; status: string }> {
    const { data } = await api.post<{ jobId: string; status: string }>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/incremental/submit`,
      { ...body, tenantId, projectId }
    )
    return data
  },

  async presignCache(
    tenantId: string,
    projectId: string,
    jobId: string,
    cacheKey?: string
  ): Promise<RenderCachePresignResponse | RenderCacheEntryPresign> {
    const params = cacheKey ? { cacheKey } : undefined
    const { data } = await api.get<RenderCachePresignResponse | RenderCacheEntryPresign>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}/cache/presign`,
      { params }
    )
    return data
  },

  async getJobTimeline(
    tenantId: string,
    projectId: string,
    jobId: string
  ): Promise<{ timelineJson: string }> {
    const { data } = await api.get<{ timelineJson: string }>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}/timeline`
    )
    return data
  },

  async cleanupExpiredCache(
    tenantId: string,
    projectId: string
  ): Promise<RenderCacheCleanupResponse> {
    const { data } = await api.post<RenderCacheCleanupResponse>(
      `/tenants/${tenantId}/projects/${projectId}/render/cache/cleanup`
    )
    return data
  },

  async listJobs(tenantId: string, projectId: string): Promise<RenderJob[]> {
    const { data } = await api.get<RenderJob[]>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs`
    )
    return data
  },

  async getJob(tenantId: string, projectId: string, jobId: string): Promise<RenderJob> {
    const { data } = await api.get<RenderJob>(
      `/tenants/${tenantId}/projects/${projectId}/render-jobs/${jobId}`
    )
    return data
  }
}

export default IncrementalRenderAPI
