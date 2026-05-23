import type { RenderLocation } from './types'

export interface ExportPathInput {
  allowed: boolean
  recommendedRenderLocation?: RenderLocation | string | null
  clientExportSupported?: boolean
  currentTier?: string
  preset?: string
  clientFeatureEnabled?: boolean
}

export function resolveExportPath(input: ExportPathInput): RenderLocation {
  if (!input.allowed) {
    return 'SERVER'
  }
  if (input.recommendedRenderLocation === 'CLIENT' || input.recommendedRenderLocation === 'SERVER') {
    return input.recommendedRenderLocation
  }
  if (input.clientExportSupported && input.clientFeatureEnabled !== false) {
    return 'CLIENT'
  }
  const tier = (input.currentTier ?? 'FREE').toUpperCase()
  const preset = (input.preset ?? '').toLowerCase()
  if (
    tier === 'FREE' &&
    (preset.includes('client') || preset.includes('free_720p')) &&
    input.clientFeatureEnabled !== false
  ) {
    return 'CLIENT'
  }
  return 'SERVER'
}
