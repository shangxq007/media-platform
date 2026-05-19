import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MonitoringStatus from './MonitoringStatus.vue'

vi.mock('@/utils/sentry', () => ({
  isSentryInitialized: () => ({ value: true }),
  getSentryReplayId: () => 'sentry-replay-abc123'
}))

vi.mock('@/utils/openreplay', () => ({
  isOpenReplayInitialized: () => ({ value: true }),
  getOpenReplaySessionId: () => 'openreplay-session-xyz789',
  getOpenReplaySessionUrl: () => 'https://openreplay.example.com/s/xyz789'
}))

vi.mock('@/composables/useFeatureFlag', () => ({
  useMonitoringFeatureFlags: () => ({
    isEnabled: () => true,
  }),
}))

describe('MonitoringStatus', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders monitoring status title', () => {
    const wrapper = mount(MonitoringStatus)
    expect(wrapper.text()).toContain('Monitoring Status')
  })

  it('shows Sentry and OpenReplay labels', () => {
    const wrapper = mount(MonitoringStatus)
    expect(wrapper.text()).toContain('Sentry')
    expect(wrapper.text()).toContain('OpenReplay')
  })

  it('shows active status badges', async () => {
    const wrapper = mount(MonitoringStatus)
    await new Promise(r => setTimeout(r, 0))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Active')
  })

  it('toggles details on button click', async () => {
    const wrapper = mount(MonitoringStatus)
    expect(wrapper.text()).not.toContain('Sentry Replay:')
    const toggleBtn = wrapper.find('button')
    await toggleBtn.trigger('click')
    expect(wrapper.text()).toContain('Sentry Replay:')
    expect(wrapper.text()).toContain('OR Session:')
  })

  it('shows copy diagnostic button when details visible', async () => {
    const wrapper = mount(MonitoringStatus)
    const toggleBtn = wrapper.find('button')
    await toggleBtn.trigger('click')
    expect(wrapper.text()).toContain('Copy Diagnostic Info')
  })

  it('shows session replay link when details visible', async () => {
    const wrapper = mount(MonitoringStatus)
    const toggleBtn = wrapper.find('button')
    await toggleBtn.trigger('click')
    const link = wrapper.find('a')
    expect(link.text()).toContain('View Session Replay')
    expect(link.attributes('href')).toBe('https://openreplay.example.com/s/xyz789')
  })
})
