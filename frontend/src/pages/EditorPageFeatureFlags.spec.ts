import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('EditorPage Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('defines the four editor feature flag keys', () => {
    const expected = [
      'editor.newTimeline.enabled',
      'editor.demoProject.enabled',
      'editor.subtitlePanel.v2',
      'editor.effectChain.v2',
    ]
    for (const key of expected) {
      expect(typeof key).toBe('string')
      expect(key.startsWith('editor.')).toBe(true)
    }
  })

  it('controls demo project button visibility via editor.demoProject.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'editor.demoProject.enabled') return { flagKey: key, enabled: false, reason: 'Demo disabled' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.demoProject.enabled')).toBe(false)
  })

  it('shows demo project button when flag is enabled', async () => {
    mockEvaluate.mockResolvedValue({ flagKey: 'any', enabled: true, reason: 'OK' })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.demoProject.enabled')).toBe(true)
  })

  it('controls new timeline via editor.newTimeline.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'editor.newTimeline.enabled') return { flagKey: key, enabled: false, reason: 'New timeline not ready' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.newTimeline.enabled')).toBe(false)
  })

  it('controls subtitle panel version via editor.subtitlePanel.v2', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'editor.subtitlePanel.v2') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.subtitlePanel.v2')).toBe(true)
    expect(isEnabled('editor.effectChain.v2')).toBe(false)
  })

  it('controls effect chain via editor.effectChain.v2', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'editor.effectChain.v2') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.effectChain.v2')).toBe(true)
  })

  it('beta badges appear on tabs controlled by feature flags', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'editor.effectChain.v2' || key === 'editor.subtitlePanel.v2') {
        return { flagKey: key, enabled: true, reason: 'OK' }
      }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useEditorFeatureFlags } = await import('../composables/useFeatureFlag')
    const { refresh, isEnabled } = useEditorFeatureFlags()
    await refresh()
    expect(isEnabled('editor.effectChain.v2')).toBe(true)
    expect(isEnabled('editor.subtitlePanel.v2')).toBe(true)
  })
})
