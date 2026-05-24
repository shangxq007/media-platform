import { ref } from 'vue'
import { useProjectStore } from '@/stores/project'
import { RenderAPI } from '@/api'
import type { RenderJob } from '@/types'
import { getErrorMessage } from '@/utils/i18n'

export type RenderJobStatus = 'creating' | 'queued' | 'running' | 'completed' | 'failed' | 'cancelled'

export function useRenderJob() {
  const projectStore = useProjectStore()

  const jobId = ref<string | null>(null)
  const status = ref<RenderJobStatus>('creating')
  const progress = ref(0)
  const error = ref<string | null>(null)

  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function submitRenderJob(
    projectId: string,
    settings: Record<string, string>,
  ): Promise<RenderJob | null> {
    jobId.value = null
    status.value = 'creating'
    progress.value = 0
    error.value = null

    try {
      const job = await RenderAPI.createJob(projectId, settings)
      jobId.value = job.id
      status.value = 'queued'
      projectStore.addRenderJob(job)
      startPolling(job.id)
      return job
    } catch (err: unknown) {
      const errorCode = err.response?.data?.errorCode || 'RENDER-500-001'
      const message = getErrorMessage(errorCode)
      error.value = `${errorCode}: ${message}`
      status.value = 'failed'
      return null
    }
  }

  function startPolling(id: string) {
    stopPolling()
    pollTimer = setInterval(async () => {
      try {
        const job = await RenderAPI.getJob(id)
        projectStore.updateRenderJob(id, job)

        switch (job.status) {
          case 'QUEUED':
            status.value = 'queued'
            break
          case 'PROCESSING':
            status.value = 'running'
            break
          case 'COMPLETED':
            status.value = 'completed'
            progress.value = 100
            stopPolling()
            break
          case 'FAILED':
            status.value = 'failed'
            error.value = `RENDER-500-001: ${getErrorMessage('RENDER-500-001')}`
            stopPolling()
            break
        }
      } catch {
        stopPolling()
      }
    }, 3000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function cancelJob() {
    if (!jobId.value) return
    try {
      stopPolling()
      status.value = 'cancelled'
      error.value = null
    } catch (err: unknown) {
      error.value = err.message || 'Failed to cancel job'
    }
  }

  async function retryJob() {
    if (!jobId.value) return
    error.value = null
    progress.value = 0
    status.value = 'queued'
    startPolling(jobId.value)
  }

  function updateProgress(value: number) {
    progress.value = Math.max(0, Math.min(100, value))
  }

  return {
    jobId,
    status,
    progress,
    error,
    submitRenderJob,
    cancelJob,
    retryJob,
    updateProgress,
    startPolling,
    stopPolling,
  }
}
