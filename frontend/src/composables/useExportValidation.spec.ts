import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useExportValidation } from './useExportValidation'
import type { TimelineState, ExportSettings } from '@/types'

vi.mock('@/api', () => ({
  EntitlementAPI: {
    validateExport: vi.fn().mockResolvedValue({
      allowed: true,
      reasonCode: '',
      currentTier: 'FREE',
      requestedPreset: 'default_720p',
      recommendedPreset: 'free_720p_watermarked',
      providerCandidates: ['stub'],
      estimatedCost: 0.01,
      currency: 'USD',
      budgetStatus: {
        allowed: true,
        warning: false,
        currentSpend: 0.5,
        budgetLimit: 10,
        remainingBudget: 9.5,
        message: null,
      },
      upgradeOptions: [],
      userFriendlyMessage: '',
      violations: [],
      recommendations: [],
    }),
  },
}))

describe('useExportValidation', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('initializes with null state', () => {
    const { validationResult, isValidating, validationError } = useExportValidation()
    expect(validationResult.value).toBeNull()
    expect(isValidating.value).toBe(false)
    expect(validationError.value).toBeNull()
  })

  it('validates export', async () => {
    const { validationResult, validateExport } = useExportValidation()
    const timeline: TimelineState = { tracks: [], duration: 60, currentTime: 0, zoom: 1, playing: false }
    const settings: ExportSettings = { format: 'mp4', resolution: '720p', profile: 'default', frameRate: 30, encoder: 'h264', audioTrack: 'all' }
    const result = await validateExport(timeline, settings, 'default_720p')
    expect(result).not.toBeNull()
    expect(validationResult.value).not.toBeNull()
    expect(validationResult.value!.allowed).toBe(true)
  })

  it('debounces validation calls', async () => {
    const { validationResult, validateExportDebounced } = useExportValidation()
    const timeline: TimelineState = { tracks: [], duration: 60, currentTime: 0, zoom: 1, playing: false }
    const settings: ExportSettings = { format: 'mp4', resolution: '720p', profile: 'default', frameRate: 30, encoder: 'h264', audioTrack: 'all' }
    validateExportDebounced(timeline, settings, 'default_720p', 500)
    vi.advanceTimersByTime(500)
    await vi.runAllTimersAsync()
    expect(validationResult.value).toBeDefined()
  })

  it('clears validation', () => {
    const { validationResult, validationError, clearValidation } = useExportValidation()
    clearValidation()
    expect(validationResult.value).toBeNull()
    expect(validationError.value).toBeNull()
  })
})
