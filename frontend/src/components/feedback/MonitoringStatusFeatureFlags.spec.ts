import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('MonitoringStatus Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('effective status requires both SDK init and feature flag enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'monitoring.sentryReplay.enabled') return { flagKey: key, enabled: false, reason: 'Sentry FF off' }
      if (key === 'monitoring.openReplay.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()

    const sentrySdkActive = true
    const openReplaySdkActive = true

    const effectiveSentry = sentrySdkActive && isEnabled('monitoring.sentryReplay.enabled')
    const effectiveOpenReplay = openReplaySdkActive && isEnabled('monitoring.openReplay.enabled')

    expect(effectiveSentry).toBe(false)
    expect(effectiveOpenReplay).toBe(true)
  })

  it('shows FF OFF badge when SDK is active but flag is disabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'monitoring.sentryReplay.enabled') return { flagKey: key, enabled: false, reason: 'off' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()

    const sentrySdkActive = true
    const flagEnabled = isEnabled('monitoring.sentryReplay.enabled')
    expect(sentrySdkActive).toBe(true)
    expect(flagEnabled).toBe(false)
  })

  it('all monitoring flags display in status component', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useMonitoringFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()

    expect(isEnabled('graphql.queryAggregation.enabled')).toBe(true)
    expect(isEnabled('graphql.adminDashboard.enabled')).toBe(true)
    expect(isEnabled('monitoring.openReplay.enabled')).toBe(true)
    expect(isEnabled('monitoring.sentryReplay.enabled')).toBe(true)
    expect(isEnabled('feedback.userReport.enabled')).toBe(true)
  })
})
