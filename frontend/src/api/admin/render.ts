import api from '../index'

export interface AdminRenderJob {
  id?: string
  projectId?: string
  tenantId?: string
  status?: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  format?: string
  resolution?: string
  profile?: string
  artifactId?: string
  createdAt?: string
  updatedAt?: string
}

export interface RenderWorker {
  workerId?: string
  status?: string
  lastHeartbeat?: string
  capabilities?: string[]
}

export const RenderAdminAPI = {
  async listJobs(tenantId?: string): Promise<AdminRenderJob[]> {
    const params = tenantId ? { tenantId } : {}
    const { data } = await api.get('/render/jobs', { params })
    return data
  },
  async getJob(jobId: string): Promise<AdminRenderJob> {
    const { data } = await api.get(`/render/jobs/${jobId}`)
    return data
  },
  async cancelJob(jobId: string): Promise<void> {
    await api.post(`/render/jobs/${jobId}/cancel`)
  },
  async retryJob(jobId: string): Promise<void> {
    await api.post(`/render/jobs/${jobId}/retry`)
  },
  async getStatusHistory(jobId: string): Promise<{ status: string; timestamp: string }[]> {
    const { data } = await api.get(`/render/jobs/${jobId}/status-history`)
    return data
  },

  // Remote workers
  async listWorkers(): Promise<RenderWorker[]> {
    const { data } = await api.get('/remote-worker/workers')
    return data
  },
  async getWorkerStatus(workerId: string): Promise<RenderWorker> {
    const { data } = await api.get(`/remote-worker/workers/${workerId}`)
    return data
  },
  async deregisterWorker(workerId: string): Promise<void> {
    await api.post(`/remote-worker/deregister/${workerId}`)
  },
}
