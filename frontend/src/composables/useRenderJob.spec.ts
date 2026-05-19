import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRenderJob } from './useRenderJob'

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    addRenderJob: vi.fn(),
    updateRenderJob: vi.fn(),
    renderJobs: [],
  }),
}))

vi.mock('@/api', () => ({
  RenderAPI: {
    createJob: vi.fn().mockResolvedValue({ id: 'job-1', projectId: 'p1', status: 'QUEUED', format: 'mp4', resolution: '1080p', profile: 'pro_1080p', createdAt: '2024-01-01' }),
    getJob: vi.fn().mockResolvedValue({ id: 'job-1', projectId: 'p1', status: 'COMPLETED', format: 'mp4', resolution: '1080p', profile: 'pro_1080p', createdAt: '2024-01-01' }),
  },
}))

vi.mock('@/utils/i18n', () => ({
  getErrorMessage: (code: string) => code,
}))

describe('useRenderJob', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  it('initializes with default state', () => {
    const { jobId, status, progress, error } = useRenderJob()
    expect(jobId.value).toBeNull()
    expect(status.value).toBe('creating')
    expect(progress.value).toBe(0)
    expect(error.value).toBeNull()
  })

  it('submits a render job', async () => {
    const { jobId, status, submitRenderJob } = useRenderJob()
    const job = await submitRenderJob('p1', { format: 'mp4', resolution: '1080p' })
    expect(job).not.toBeNull()
    expect(jobId.value).toBe('job-1')
    expect(status.value).toBe('queued')
  })

  it('handles submission failure', async () => {
    const { RenderAPI } = await import('@/api')
    vi.mocked(RenderAPI.createJob).mockRejectedValueOnce({
      response: { data: { errorCode: 'RENDER-500-001', message: 'Failed' } },
    })

    const { status, error, submitRenderJob } = useRenderJob()
    const job = await submitRenderJob('p1', {})
    expect(job).toBeNull()
    expect(status.value).toBe('failed')
    expect(error.value).toContain('RENDER-500-001')
  })

  it('updates progress', () => {
    const { progress, updateProgress } = useRenderJob()
    updateProgress(50)
    expect(progress.value).toBe(50)
  })

  it('clamps progress to 100', () => {
    const { progress, updateProgress } = useRenderJob()
    updateProgress(150)
    expect(progress.value).toBe(100)
  })

  it('clamps progress to 0', () => {
    const { progress, updateProgress } = useRenderJob()
    updateProgress(-10)
    expect(progress.value).toBe(0)
  })

  it('cancels job', () => {
    const { jobId, status, cancelJob } = useRenderJob()
    jobId.value = 'job-1'
    status.value = 'running'
    cancelJob()
    expect(status.value).toBe('cancelled')
  })

  it('retries job', () => {
    const { jobId, status, error, retryJob } = useRenderJob()
    jobId.value = 'job-1'
    status.value = 'failed'
    error.value = 'Some error'
    retryJob()
    expect(status.value).toBe('queued')
    expect(error.value).toBeNull()
  })
})
