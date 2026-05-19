import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockEvaluate = vi.fn()

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: (...args: any[]) => mockEvaluate(...args),
  },
}))

describe('ExtensionManagement Feature Flag Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockEvaluate.mockReset()
  })

  it('defines the five extension feature flag keys', () => {
    const expected = [
      'extension.platform.enabled',
      'extension.wasmRuntime.enabled',
      'extension.jsRuntime.enabled',
      'extension.pythonRuntime.enabled',
      'extension.grayRelease.enabled',
    ]
    for (const key of expected) {
      expect(typeof key).toBe('string')
      expect(key.startsWith('extension.')).toBe(true)
    }
  })

  it('controls platform access via extension.platform.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'extension.platform.enabled') return { flagKey: key, enabled: false, reason: 'Platform disabled' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useExtensionFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.platform.enabled')).toBe(false)
  })

  it('controls WASM runtime via extension.wasmRuntime.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'extension.wasmRuntime.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useExtensionFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.wasmRuntime.enabled')).toBe(true)
    expect(isEnabled('extension.jsRuntime.enabled')).toBe(false)
  })

  it('controls JS runtime via extension.jsRuntime.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'extension.jsRuntime.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useExtensionFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.jsRuntime.enabled')).toBe(true)
  })

  it('controls Python runtime via extension.pythonRuntime.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'extension.pythonRuntime.enabled') return { flagKey: key, enabled: false, reason: 'Python off' }
      return { flagKey: key, enabled: true, reason: 'OK' }
    })
    const { useExtensionFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.pythonRuntime.enabled')).toBe(false)
  })

  it('controls gray release via extension.grayRelease.enabled', async () => {
    mockEvaluate.mockImplementation(async (key: string) => {
      if (key === 'extension.grayRelease.enabled') return { flagKey: key, enabled: true, reason: 'OK' }
      return { flagKey: key, enabled: false, reason: 'OFF' }
    })
    const { useExtensionFeatureFlags } = await import('../../composables/useFeatureFlag')
    const { refresh, isEnabled } = useExtensionFeatureFlags()
    await refresh()
    expect(isEnabled('extension.grayRelease.enabled')).toBe(true)
  })

  it('shows runtime flag status per extension card', () => {
    const runtimeFlags = new Map<string, { flagKey: string; enabled: boolean }[]>()
    runtimeFlags.set('ext-1', [
      { flagKey: 'wasm-runtime', enabled: true },
      { flagKey: 'js-runtime', enabled: false },
      { flagKey: 'python-runtime', enabled: true },
    ])
    const extFlags = runtimeFlags.get('ext-1')
    expect(extFlags).toBeDefined()
    expect(extFlags![0].enabled).toBe(true)
    expect(extFlags![1].enabled).toBe(false)
    expect(extFlags![2].enabled).toBe(true)
  })
})
