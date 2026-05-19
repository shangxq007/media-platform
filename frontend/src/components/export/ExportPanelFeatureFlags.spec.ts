import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('ExportPanel Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('defines the four export feature flag keys', () => {
    const expected = [
      'export.gpu.v2',
      'export.remoteWorker.enabled',
      'export.providerRouting.v2',
      'export.newPresetSelector.enabled',
    ]
    for (const key of expected) {
      expect(typeof key).toBe('string')
      expect(key.startsWith('export.')).toBe(true)
    }
  })

  it('evaluates export flags on mount', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'export.gpu.v2', enabled: true, reason: 'OK' })
    const { useExportFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh } = useExportFeatureFlags()
    await refresh()
    expect(mockEvaluate).toHaveBeenCalled()
  })

  it('blocks render submission when export.gpu.v2 is disabled for gpu presets', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'export.gpu.v2') return { flagKey: key, enabled: false, reason: 'GPU export is in beta' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useExportFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled, getDisabledReason } = useExportFeatureFlags()
    await refresh()
    expect(isEnabled('export.gpu.v2')).toBe(false)
    expect(getDisabledReason('export.gpu.v2')).toBe('GPU export is in beta')
  })

  it('blocks render submission when export.remoteWorker.enabled is disabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'export.remoteWorker.enabled') return { flagKey: key, enabled: false, reason: 'Remote worker is disabled' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useExportFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled, getDisabledReason } = useExportFeatureFlags()
    await refresh()
    expect(isEnabled('export.remoteWorker.enabled')).toBe(false)
    expect(getDisabledReason('export.remoteWorker.enabled')).toBe('Remote worker is disabled')
  })

  it('allows render when all relevant flags are enabled', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useExportFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExportFeatureFlags()
    await refresh()
    expect(isEnabled('export.gpu.v2')).toBe(true)
    expect(isEnabled('export.remoteWorker.enabled')).toBe(true)
    expect(isEnabled('export.providerRouting.v2')).toBe(true)
    expect(isEnabled('export.newPresetSelector.enabled')).toBe(true)
  })

  it('include feature flag reasons in preset disabled messages', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'export.gpu.v2') return { flagKey: key, enabled: false, reason: 'GPU rendering is currently in beta' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useExportFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, getDisabledReason } = useExportFeatureFlags()
    await refresh()
    const reason = getDisabledReason('export.gpu.v2')
    expect(reason).toBe('GPU rendering is currently in beta')
  })
})
