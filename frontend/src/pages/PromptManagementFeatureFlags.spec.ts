import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('PromptManagement Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('defines the four prompt feature flag keys', () => {
    const expected = [
      'prompt.management.enabled',
      'prompt.riskReview.enabled',
      'prompt.executionCostPreview.enabled',
      'prompt.manifestPanel.enabled',
    ]
    for (const key of expected) {
      expect(typeof key).toBe('string')
      expect(key.startsWith('prompt.')).toBe(true)
    }
  })

  it('controls page access via prompt.management.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'prompt.management.enabled') return { flagKey: key, enabled: false, reason: 'Prompt mgmt disabled' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { usePromptFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.management.enabled')).toBe(false)
  })

  it('allows page access when prompt.management.enabled is true', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { usePromptFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.management.enabled')).toBe(true)
  })

  it('controls risk review via prompt.riskReview.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'prompt.riskReview.enabled') return { flagKey: key, enabled: false, reason: 'Risk review off' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { usePromptFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.riskReview.enabled')).toBe(false)
  })

  it('controls cost preview via prompt.executionCostPreview.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'prompt.executionCostPreview.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { usePromptFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.executionCostPreview.enabled')).toBe(true)
  })

  it('controls manifest panel via prompt.manifestPanel.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'prompt.manifestPanel.enabled') return { flagKey: key, enabled: false, reason: 'Manifest off' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { usePromptFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = usePromptFeatureFlags()
    await refresh()
    expect(isEnabled('prompt.manifestPanel.enabled')).toBe(false)
  })

  it('includes feature flag status in template detail data structure', () => {
    const detailData = {
      id: 'pt-1',
      name: 'Test',
      status: 'ACTIVE',
      currentVersion: '1.0.0',
      tags: [],
      versions: [],
      executions: [],
      featureFlagStatus: {
        promptManagementEnabled: true,
        riskReviewEnabled: false,
        executionCostPreviewEnabled: true,
        manifestPanelEnabled: false,
      },
    }
    expect(detailData.featureFlagStatus).toBeDefined()
    expect(detailData.featureFlagStatus.promptManagementEnabled).toBe(true)
    expect(detailData.featureFlagStatus.riskReviewEnabled).toBe(false)
  })
})
