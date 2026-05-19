import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('useFeatureFlag', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('returns false for isEnabled when flag not evaluated', async () => {
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { isEnabled } = useFeatureFlag({ flagKeys: ['test.flag'], immediate: false })
    expect(isEnabled('test.flag')).toBe(false)
  })

  it('evaluates flags and populates flagMap on refresh', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'test.flag', enabled: true, reason: 'test' })
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { flagMap, refresh, isEnabled } = useFeatureFlag({ flagKeys: ['test.flag'], immediate: false })
    await refresh()
    expect(flagMap.value['test.flag']).toBe(true)
    expect(isEnabled('test.flag')).toBe(true)
  })

  it('handles evaluation failures gracefully', async () => {
    mockEvaluate.mockRejectedValue(new Error('Network error'))
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { flagMap, refresh, isEnabled } = useFeatureFlag({ flagKeys: ['failing.flag'], immediate: false })
    await refresh()
    expect(flagMap.value['failing.flag']).toBe(false)
    expect(isEnabled('failing.flag')).toBe(false)
  })

  it('returns disabled reason from flag detail', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'test.flag', enabled: false, reason: 'Feature is in beta' })
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { refresh, getDisabledReason } = useFeatureFlag({ flagKeys: ['test.flag'], immediate: false })
    await refresh()
    expect(getDisabledReason('test.flag')).toBe('Feature is in beta')
  })

  it('returns default message for unevaluated flag', async () => {
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { getDisabledReason } = useFeatureFlag({ immediate: false })
    expect(getDisabledReason('unknown.flag')).toBe('Feature flag not evaluated')
  })

  it('returns empty string for enabled flag', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'test.flag', enabled: true, reason: 'OK' })
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { refresh, getDisabledReason } = useFeatureFlag({ flagKeys: ['test.flag'], immediate: false })
    await refresh()
    expect(getDisabledReason('test.flag')).toBe('')
  })

  it('stores flag details with variant info', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'test.flag', enabled: true, reason: 'OK', variant: 'v2' })
    const { useFeatureFlag } = await import('./useFeatureFlag')
    const { refresh, getDetail } = useFeatureFlag({ flagKeys: ['test.flag'], immediate: false })
    await refresh()
    const detail = getDetail('test.flag')
    expect(detail?.enabled).toBe(true)
    expect(detail?.variant).toBe('v2')
  })

  it('useExportFeatureFlags initializes with export flag keys', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useExportFeatureFlags } = await import('./useFeatureFlag')
    const { refresh, isEnabled } = useExportFeatureFlags()
    await refresh()
    expect(isEnabled('export.gpu.v2')).toBe(true)
    expect(isEnabled('export.remoteWorker.enabled')).toBe(true)
    expect(isEnabled('export.providerRouting.v2')).toBe(true)
    expect(isEnabled('export.newPresetSelector.enabled')).toBe(true)
  })

  it('useEditorFeatureFlags initializes with editor flag keys', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useEditorFeatureFlags } = await import('./useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.newTimeline.enabled')).toBe(true)
    expect(isEnabled('editor.demoProject.enabled')).toBe(true)
    expect(isEnabled('editor.subtitlePanel.v2')).toBe(true)
    expect(isEnabled('editor.effectChain.v2')).toBe(true)
  })

  it('usePromptFeatureFlags initializes with prompt flag keys', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { usePromptFeatureFlags } = await import('./useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.management.enabled')).toBe(true)
    expect(isEnabled('prompt.riskReview.enabled')).toBe(true)
    expect(isEnabled('prompt.executionCostPreview.enabled')).toBe(true)
    expect(isEnabled('prompt.manifestPanel.enabled')).toBe(true)
  })

  it('useExtensionFeatureFlags initializes with extension flag keys', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useExtensionFeatureFlags } = await import('./useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.platform.enabled')).toBe(true)
    expect(isEnabled('extension.wasmRuntime.enabled')).toBe(true)
    expect(isEnabled('extension.jsRuntime.enabled')).toBe(true)
    expect(isEnabled('extension.pythonRuntime.enabled')).toBe(true)
    expect(isEnabled('extension.grayRelease.enabled')).toBe(true)
  })

  it('useMonitoringFeatureFlags initializes with monitoring flag keys', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useMonitoringFeatureFlags } = await import('./useFeatureFlag')
    const { refresh, isEnabled } = useMonitoringFeatureFlags()
    await refresh()
    expect(isEnabled('graphql.queryAggregation.enabled')).toBe(true)
    expect(isEnabled('graphql.adminDashboard.enabled')).toBe(true)
    expect(isEnabled('monitoring.openReplay.enabled')).toBe(true)
    expect(isEnabled('monitoring.sentryReplay.enabled')).toBe(true)
    expect(isEnabled('feedback.userReport.enabled')).toBe(true)
  })
})
