import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FeedbackButton from './FeedbackButton.vue'

vi.mock('@/utils/openreplay', () => ({
  submitOpenReplayFeedback: vi.fn().mockResolvedValue(true),
  getOpenReplaySessionId: vi.fn().mockReturnValue('or-session-123'),
  isOpenReplayInitialized: () => ({ value: true })
}))

vi.mock('@/utils/sentry', () => ({
  captureSentryException: vi.fn(),
  getSentryReplayId: vi.fn().mockReturnValue('sentry-replay-456'),
  isSentryInitialized: () => ({ value: true })
}))

describe('FeedbackButton', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders feedback button', () => {
    const wrapper = mount(FeedbackButton)
    expect(wrapper.text()).toContain('Feedback')
  })

  it('opens modal on button click', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const modals = document.querySelectorAll('.c-dialog')
    expect(modals.length).toBeGreaterThan(0)
  })

  it('renders type selection buttons in modal', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const html = document.body.innerHTML
    expect(html).toContain('Bug')
    expect(html).toContain('Feature')
    expect(html).toContain('Other')
  })

  it('renders severity selection buttons in modal', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const html = document.body.innerHTML
    expect(html).toContain('Low')
    expect(html).toContain('Medium')
    expect(html).toContain('High')
    expect(html).toContain('Critical')
  })

  it('renders context input fields in modal', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    expect(document.body.innerHTML).toContain('Context')
  })

  it('shows monitoring status in modal', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const html = document.body.innerHTML
    expect(html).toContain('Sentry active')
    expect(html).toContain('OpenReplay recording')
  })

  it('submit button is disabled when title is empty', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const allBtns = [...document.querySelectorAll('button')]
    const submitBtn = allBtns.filter(b => b.textContent?.includes('Submit Feedback')).pop()
    expect(submitBtn?.attributes.getNamedItem('disabled')).not.toBeNull()
  })

  it('closes modal on backdrop click', async () => {
    const wrapper = mount(FeedbackButton)
    await wrapper.find('button').trigger('click')
    await wrapper.vm.$nextTick()
    const overlay = document.querySelector('.c-dialog-overlay')
    expect(overlay).not.toBeNull()
  })
})
