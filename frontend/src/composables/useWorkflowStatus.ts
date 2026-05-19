import { ref, computed } from 'vue'
import type { ErrorResponse } from '@/types'
import { getErrorMessage } from '@/utils/i18n'

export type WorkflowStatus = 'idle' | 'running' | 'completed' | 'failed' | 'cancelled'

export interface WorkflowStep {
  id: string
  name: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped'
  errorCode?: string
  errorMessage?: string
  startedAt?: string
  completedAt?: string
}

export function useWorkflowStatus() {
  const status = ref<WorkflowStatus>('idle')
  const steps = ref<WorkflowStep[]>([])
  const error = ref<ErrorResponse | null>(null)
  const progress = ref(0)

  const statusText = computed(() => {
    switch (status.value) {
      case 'idle': return 'Ready'
      case 'running': return 'Processing...'
      case 'completed': return 'Completed'
      case 'failed': return 'Failed'
      case 'cancelled': return 'Cancelled'
      default: return 'Unknown'
    }
  })

  const statusColor = computed(() => {
    switch (status.value) {
      case 'idle': return 'text-gray-400'
      case 'running': return 'text-yellow-400'
      case 'completed': return 'text-green-400'
      case 'failed': return 'text-red-400'
      case 'cancelled': return 'text-gray-500'
      default: return 'text-gray-400'
    }
  })

  function startWorkflow(stepNames: string[]) {
    status.value = 'running'
    error.value = null
    progress.value = 0
    steps.value = stepNames.map((name, i) => ({
      id: `step_${i}`,
      name,
      status: i === 0 ? 'running' : 'pending'
    }))
  }

  function updateStep(stepId: string, update: Partial<WorkflowStep>) {
    const step = steps.value.find(s => s.id === stepId)
    if (step) {
      Object.assign(step, update)
      if (update.status === 'failed' && update.errorCode) {
        step.errorMessage = update.errorCode ? getErrorMessage(update.errorCode) : update.errorMessage
      }
    }
    // Update progress
    const completed = steps.value.filter(s => s.status === 'completed').length
    progress.value = Math.round((completed / steps.value.length) * 100)
  }

  function completeWorkflow() {
    status.value = 'completed'
    progress.value = 100
  }

  function failWorkflow(err: ErrorResponse) {
    status.value = 'failed'
    error.value = { ...err, message: getErrorMessage(err.errorCode) }
    // Mark current running step as failed
    const runningStep = steps.value.find(s => s.status === 'running')
    if (runningStep) {
      runningStep.status = 'failed'
      runningStep.errorCode = err.errorCode
      runningStep.errorMessage = err.message
    }
  }

  function cancelWorkflow() {
    status.value = 'cancelled'
    steps.value.forEach(s => {
      if (s.status === 'pending') s.status = 'skipped'
    })
  }

  function resetWorkflow() {
    status.value = 'idle'
    steps.value = []
    error.value = null
    progress.value = 0
  }

  return {
    status, steps, error, progress,
    statusText, statusColor,
    startWorkflow, updateStep, completeWorkflow, failWorkflow, cancelWorkflow, resetWorkflow
  }
}
