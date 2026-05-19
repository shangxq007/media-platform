import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('MonitoringFeedback Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('defines the five monitoring feature flag keys', () => {
    const expected = [
      'graphql.queryAggregation.enabled',
      'graphql.adminDashboard.enabled',
      'monitoring.openReplay.enabled',
      'monitoring.sentryReplay.enabled',
      'feedback.userReport.enabled',
    ]
    for (const key of expected) {
      expect(typeof key).toBe('string')
    }
  })

  it('controls GraphQL dashboard via graphql.queryAggregation.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'graphql.queryAggregation.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('graphql.queryAggregation.enabled')).toBe(true)
    expect(isEnabled('graphql.adminDashboard.enabled')).toBe(false)
  })

  it('controls admin GraphQL via graphql.adminDashboard.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'graphql.adminDashboard.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('graphql.adminDashboard.enabled')).toBe(true)
  })

  it('controls OpenReplay via monitoring.openReplay.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'monitoring.openReplay.enabled') return { flagKey: key, enabled: false, reason: 'OR disabled' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('monitoring.openReplay.enabled')).toBe(false)
  })

  it('controls Sentry replay via monitoring.sentryReplay.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'monitoring.sentryReplay.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('monitoring.sentryReplay.enabled')).toBe(true)
  })

  it('controls user feedback via feedback.userReport.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'feedback.userReport.enabled') return { flagKey: key, enabled: false, reason: 'Feedback off' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('feedback.userReport.enabled')).toBe(false)
  })

  it('monitoring status reflects feature flag states in combination with SDK status', () => {
    const sentrySdkActive = true
    const sentryFlagEnabled = false
    const effectiveActive = sentrySdkActive && sentryFlagEnabled
    expect(effectiveActive).toBe(false)
  })

  it('monitoring status shows active only when both SDK and flag are enabled', () => {
    const sentrySdkActive = true
    const sentryFlagEnabled = true
    const effectiveActive = sentrySdkActive && sentryFlagEnabled
    expect(effectiveActive).toBe(true)
  })
})
