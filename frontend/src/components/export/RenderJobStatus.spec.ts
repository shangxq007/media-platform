import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import RenderJobStatus from './RenderJobStatus.vue'

vi.mock('@/components/ui/StatusBadge.vue', () => ({
  default: {
    props: ['variant', 'label', 'size', 'dot'],
    template: '<span class="status-badge">{{ label }}</span>',
  },
}))

vi.mock('@/components/ui/ConfirmDialog.vue', () => ({
  default: {
    props: ['open', 'title', 'description', 'confirmLabel', 'variant'],
    emits: ['confirm', 'cancel'],
    template: '<div v-if="open" class="confirm-dialog"><button class="confirm-cancel" @click="$emit(\'cancel\')">Cancel</button><button class="confirm-ok" @click="$emit(\'confirm\')">OK</button></div>',
  },
}))

describe('RenderJobStatus', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
      writable: true,
      configurable: true,
    })
  })

  it('renders job ID', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-abc123', status: 'running', progress: 50, error: null },
    })
    expect(wrapper.text()).toContain('job-abc123')
  })

  it('shows running badge for running status', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'running', progress: 50, error: null },
    })
    expect(wrapper.text()).toContain('running')
  })

  it('shows progress bar when running', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'running', progress: 75, error: null },
    })
    const progressBar = wrapper.find('.bg-info-500')
    expect(progressBar.exists()).toBe(true)
    expect(progressBar.attributes('style')).toContain('75%')
  })

  it('shows progress bar when queued', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'queued', progress: 0, error: null },
    })
    expect(wrapper.text()).toContain('In queue')
  })

  it('shows error info for failed status', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'failed', progress: 0, error: 'Something went wrong', errorCode: 'RENDER-500-001' },
    })
    expect(wrapper.text()).toContain('Render Failed')
    expect(wrapper.text()).toContain('RENDER-500-001')
    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('shows retry button for failed status', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'failed', progress: 0, error: 'Error' },
    })
    expect(wrapper.text()).toContain('Retry')
  })

  it('shows cancel button for running status', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'running', progress: 50, error: null },
    })
    expect(wrapper.text()).toContain('Cancel')
  })

  it('shows cancel button for queued status', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'queued', progress: 0, error: null },
    })
    expect(wrapper.text()).toContain('Cancel')
  })

  it('does not show retry for completed', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'completed', progress: 100, error: null },
    })
    expect(wrapper.text()).not.toContain('Retry')
    expect(wrapper.text()).not.toContain('Cancel')
  })

  it('emits retry event', async () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'failed', progress: 0, error: 'Error' },
    })
    const retryBtn = wrapper.findAll('button').find(b => b.text().includes('Retry'))
    expect(retryBtn).toBeTruthy()
    await retryBtn!.trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })

  it('emits cancel event after confirmation', async () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'running', progress: 50, error: null },
    })
    const cancelBtn = wrapper.findAll('button').find(b => b.text().includes('Cancel'))
    expect(cancelBtn).toBeTruthy()
    await cancelBtn!.trigger('click')
    expect(wrapper.find('.confirm-dialog').exists()).toBe(true)
    await wrapper.find('.confirm-ok').trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('shows copy diagnostic button', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'failed', progress: 0, error: 'Error', diagnosticInfo: 'some info' },
    })
    expect(wrapper.text()).toContain('Copy diagnostic info')
  })

  it('shows neutral badge for cancelled', () => {
    const wrapper = mount(RenderJobStatus, {
      props: { jobId: 'job-1', status: 'cancelled', progress: 50, error: null },
    })
    expect(wrapper.text()).toContain('cancelled')
  })
})
