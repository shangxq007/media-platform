import { describe, expect, it } from 'vitest'
import { resolveExportPath } from './resolveExportPath'

describe('resolveExportPath', () => {
  it('uses recommended CLIENT from API', () => {
    expect(
      resolveExportPath({
        allowed: true,
        recommendedRenderLocation: 'CLIENT',
        clientExportSupported: true,
      })
    ).toBe('CLIENT')
  })

  it('defaults FREE watermarked preset to CLIENT when supported', () => {
    expect(
      resolveExportPath({
        allowed: true,
        currentTier: 'FREE',
        preset: 'client_720p_watermarked',
        clientExportSupported: true,
        clientFeatureEnabled: true,
      })
    ).toBe('CLIENT')
  })

  it('uses SERVER when not allowed', () => {
    expect(resolveExportPath({ allowed: false })).toBe('SERVER')
  })
})
