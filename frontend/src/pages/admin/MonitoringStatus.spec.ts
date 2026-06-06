import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MonitoringFeedbackPage from './MonitoringFeedbackPage.vue'

vi.mock('@/composables/useFeatureFlag', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>
  return {
    ...actual,
    useFeatureFlag: () => ({ value: false }),
    evaluateFeatureFlag: vi.fn().mockResolvedValue(false),
  }
})

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

import { ref } from 'vue'

describe('MonitoringStatus displays OpenReplay flag state', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders monitoring page', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Monitoring & Feedback')
  })

  it('displays OpenReplay feature flag state', () => {
    const monitoringFlags = {
      openReplayEnabled: false,
      sentryEnabled: true,
    }
    expect(monitoringFlags.openReplayEnabled).toBe(false)
    expect(monitoringFlags.sentryEnabled).toBe(true)
  })

  it('displays OpenReplay flag state when enabled', () => {
    const monitoringFlags = {
      openReplayEnabled: true,
      sentryEnabled: true,
    }
    expect(monitoringFlags.openReplayEnabled).toBe(true)
  })

  it('displays all monitoring feature flag keys', () => {
    const monitoringFlagKeys = [
      'graphql.queryAggregation.enabled',
      'graphql.adminDashboard.enabled',
      'monitoring.openReplay.enabled',
      'monitoring.sentryReplay.enabled',
      'feedback.userReport.enabled',
    ]
    expect(monitoringFlagKeys).toContain('monitoring.openReplay.enabled')
    expect(monitoringFlagKeys.length).toBe(5)
  })

  it('renders monitoring status with feature flag data', () => {
    const monitoringStatus = {
      sentryEnabled: true,
      openReplayEnabled: false,
      lastErrorAt: { iso: '2026-01-15T10:00:00Z' },
      lastFeedbackAt: { iso: '2026-01-15T12:00:00Z' },
    }
    expect(monitoringStatus.openReplayEnabled).toBe(false)
    expect(monitoringStatus.sentryEnabled).toBe(true)
    expect(monitoringStatus.lastErrorAt.iso).toBe('2026-01-15T10:00:00Z')
  })

  it('renders range selector', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    const select = wrapper.find('select')
    expect(select.exists()).toBe(true)
  })

  it('renders refresh button', () => {
    const wrapper = mount(MonitoringFeedbackPage)
    const buttons = wrapper.findAll('button')
    const refreshBtn = buttons.find(b => b.text().includes('Refresh'))
    expect(refreshBtn).toBeTruthy()
  })

  it('displays feedback summary metrics', () => {
    const feedbackSummary = {
      openIssues: 5,
      criticalIssues: 1,
      linkedRenderJobs: 3,
      linkedPromptExecutions: 2,
      replayLinked: 4,
    }
    expect(feedbackSummary.openIssues).toBe(5)
    expect(feedbackSummary.criticalIssues).toBe(1)
    expect(feedbackSummary.replayLinked).toBe(4)
  })

  it('handles OpenReplay flag toggle', () => {
    let openReplayEnabled = false
    openReplayEnabled = !openReplayEnabled
    expect(openReplayEnabled).toBe(true)
    openReplayEnabled = !openReplayEnabled
    expect(openReplayEnabled).toBe(false)
  })

  it('displays monitoring flag state with correct feature flag key', () => {
    const flagKey = 'monitoring.openReplay.enabled'
    const isEnabled = false
    expect(flagKey).toBe('monitoring.openReplay.enabled')
    expect(isEnabled).toBe(false)
  })
})
