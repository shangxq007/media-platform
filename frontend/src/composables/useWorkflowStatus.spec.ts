import { describe, it, expect, beforeEach } from 'vitest'
import { useWorkflowStatus } from '@/composables/useWorkflowStatus'

describe('useWorkflowStatus', () => {
  let workflow: ReturnType<typeof useWorkflowStatus>

  beforeEach(() => {
    workflow = useWorkflowStatus()
  })

  it('starts in idle state', () => {
    expect(workflow.status.value).toBe('idle')
    expect(workflow.progress.value).toBe(0)
  })

  it('starts workflow with steps', () => {
    workflow.startWorkflow(['Render', 'Store', 'Notify'])
    expect(workflow.status.value).toBe('running')
    expect(workflow.steps.value).toHaveLength(3)
    expect(workflow.steps.value[0].status).toBe('running')
    expect(workflow.steps.value[1].status).toBe('pending')
  })

  it('updates step status', () => {
    workflow.startWorkflow(['Render', 'Store'])
    workflow.updateStep('step_0', { status: 'completed' })
    expect(workflow.steps.value[0].status).toBe('completed')
    expect(workflow.progress.value).toBe(50)
  })

  it('completes workflow', () => {
    workflow.startWorkflow(['Render', 'Store'])
    workflow.updateStep('step_0', { status: 'completed' })
    workflow.updateStep('step_1', { status: 'completed' })
    workflow.completeWorkflow()
    expect(workflow.status.value).toBe('completed')
    expect(workflow.progress.value).toBe(100)
  })

  it('fails workflow with error', () => {
    workflow.startWorkflow(['Render', 'Store'])
    workflow.failWorkflow({
      errorCode: 'RENDER-500-001',
      message: 'Render failed',
      details: { jobId: 'test-123' },
      timestamp: new Date().toISOString()
    })
    expect(workflow.status.value).toBe('failed')
    expect(workflow.error.value).not.toBeNull()
    expect(workflow.error.value?.errorCode).toBe('RENDER-500-001')
  })

  it('cancels workflow', () => {
    workflow.startWorkflow(['Render', 'Store', 'Notify'])
    workflow.cancelWorkflow()
    expect(workflow.status.value).toBe('cancelled')
    expect(workflow.steps.value[1].status).toBe('skipped')
    expect(workflow.steps.value[2].status).toBe('skipped')
  })

  it('resets workflow', () => {
    workflow.startWorkflow(['Render'])
    workflow.resetWorkflow()
    expect(workflow.status.value).toBe('idle')
    expect(workflow.steps.value).toHaveLength(0)
    expect(workflow.error.value).toBeNull()
  })

  it('shows correct status text', () => {
    expect(workflow.statusText.value).toBe('Ready')
    workflow.startWorkflow(['Step1'])
    expect(workflow.statusText.value).toBe('Processing...')
    workflow.completeWorkflow()
    expect(workflow.statusText.value).toBe('Completed')
  })
})
