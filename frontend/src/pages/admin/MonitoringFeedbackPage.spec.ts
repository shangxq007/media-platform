import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import MonitoringFeedbackPage from './MonitoringFeedbackPage.vue'

vi.mock('@/composables/useGraphQLQuery', () => ({
  useGraphQLQuery: () => ({
    data: ref(null),
    loading: ref(false),
    error: ref(null),
    errorCode: ref(undefined),
    traceId: ref(undefined),
    refetch: vi.fn().mockResolvedValue({
      monitoringStatus: {
        sentryEnabled: true,
        openReplayEnabled: false,
        lastErrorAt: { iso: '2026-01-15T10:00:00Z' },
        lastFeedbackAt: { iso: '2026-01-15T12:00:00Z' },
      },
      feedbackSummary: {
        openIssues: 5,
        criticalIssues: 1,
        linkedRenderJobs: 3,
        linkedPromptExecutions: 2,
        replayLinked: 4,
      },
      problematicDataSummary: {
        total: 12,
        requireReview: 4,
        autoFixed: 6,
        critical: 2,
      },
    }),
  }),
}))

describe('MonitoringFeedbackPage', () => {
  it('renders the page title', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    expect(wrapper.text()).toContain('Monitoring & Feedback')
  })

  it('has a range selector', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
  })

  it('has a refresh button', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    const buttons = wrapper.findAll('button')
    const refreshBtn = buttons.filter(b => b.text().includes('Refresh'))
    expect(refreshBtn.length).toBeGreaterThan(0)
  })

  it('renders monitoring status section after data loads', async () => {
    const wrapper = mount(MonitoringFeedbackPage)
    await nextTick()
    expect(wrapper.text()).toContain('Monitoring Status')
    expect(wrapper.text()).toContain('Sentry')
    expect(wrapper.text()).toContain('OpenReplay')
  })

  it('renders stats grid placeholders when data is null', async () => {
    const wrapper = mount(MonitoringFeedbackPage)
    await nextTick()
    expect(wrapper.text()).toContain('Open Issues')
    expect(wrapper.text()).toContain('Critical Issues')
    expect(wrapper.text()).toContain('Linked Render Jobs')
    expect(wrapper.text()).toContain('Linked Executions')
  })

  it('renders problematic data section when data is null', async () => {
    const wrapper = mount(MonitoringFeedbackPage)
    await nextTick()
    expect(wrapper.text()).toContain('Problematic Data')
    expect(wrapper.text()).toContain('Require Review')
    expect(wrapper.text()).toContain('Auto-Fixed')
  })
})
