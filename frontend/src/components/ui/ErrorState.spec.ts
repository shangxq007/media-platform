import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ErrorState from './ErrorState.vue'

describe('ErrorState', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders default error message', () => {
    const wrapper = mount(ErrorState)
    expect(wrapper.text()).toContain('Something went wrong')
    expect(wrapper.text()).toContain('An unexpected error occurred. Please try again.')
  })

  it('renders custom title and description', () => {
    const wrapper = mount(ErrorState, {
      props: { title: 'Load Failed', description: 'Could not load data' }
    })
    expect(wrapper.text()).toContain('Load Failed')
    expect(wrapper.text()).toContain('Could not load data')
  })

  it('renders error code when provided', () => {
    const wrapper = mount(ErrorState, {
      props: { errorCode: 'ERR_404' }
    })
    expect(wrapper.text()).toContain('ERR_404')
  })

  it('renders diagnostic ID with copy button', () => {
    const wrapper = mount(ErrorState, {
      props: { diagnosticId: 'diag-abc-123' }
    })
    expect(wrapper.text()).toContain('diag-abc-123')
    expect(wrapper.text()).toContain('Copy')
  })

  it('renders retry button by default', () => {
    const wrapper = mount(ErrorState)
    expect(wrapper.text()).toContain('Retry')
  })

  it('hides retry button when showRetry is false', () => {
    const wrapper = mount(ErrorState, {
      props: { showRetry: false }
    })
    expect(wrapper.text()).not.toContain('Retry')
  })

  it('hides dismiss button when showDismiss is false', () => {
    const wrapper = mount(ErrorState, {
      props: { showDismiss: false }
    })
    expect(wrapper.text()).not.toContain('Dismiss')
  })

  it('emits retry event on retry button click', async () => {
    const wrapper = mount(ErrorState)
    const retryBtn = wrapper.findAll('button').find(b => b.text().includes('Retry'))
    await retryBtn?.trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })

  it('emits dismiss event on dismiss button click', async () => {
    const wrapper = mount(ErrorState)
    const dismissBtn = wrapper.findAll('button').find(b => b.text().includes('Dismiss'))
    await dismissBtn?.trigger('click')
    expect(wrapper.emitted('dismiss')).toBeTruthy()
  })

  it('shows admin debug toggle when showAdminDebug is true', async () => {
    const wrapper = mount(ErrorState, {
      props: { showAdminDebug: true, errorCode: 'ERR_TEST', diagnosticId: 'diag-1' }
    })
    expect(wrapper.text()).toContain('Show Debug Info')
    const debugBtn = wrapper.findAll('button').find(b => b.text().includes('Show Debug Info'))
    await debugBtn?.trigger('click')
    expect(wrapper.text()).toContain('Hide Debug Info')
    expect(wrapper.text()).toContain('"ERR_TEST"')
    expect(wrapper.text()).toContain('diag-1')
  })

  it('renders error details when provided', () => {
    const wrapper = mount(ErrorState, {
      props: { errorDetails: 'Stack trace: ...' }
    })
    expect(wrapper.text()).toContain('Stack trace: ...')
  })
})
