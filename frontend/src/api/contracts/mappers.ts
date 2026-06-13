// =============================================================================
// API Contract Mappers
// =============================================================================
// Centralized mapping from raw API responses to validated domain types.
// All mappers enforce strict validation - invalid data is rejected.
// =============================================================================

import { z } from 'zod'
import {
  AssetSchema,
  RenderJobSchema,
  RenderJobSummarySchema,
  ArtifactSchema,
  RenderJobArtifactSchema,
  EffectPackSchema,
} from './schema'

import type {
  Asset,
  RenderJob,
  RenderJobSummary,
  Artifact,
  RenderJobArtifact,
  EffectPack,
} from './schema'

// ---------------------------------------------------------------------------
// Strict Validation Result
// ---------------------------------------------------------------------------

export interface ValidationResult<T> {
  valid: boolean
  data: T | null
  errors: string[]
}

function validateStrict<T>(
  schema: z.ZodType<T>,
  data: unknown,
  context: string
): ValidationResult<T> {
  const result = schema.safeParse(data)
  if (result.success) {
    return { valid: true, data: result.data, errors: [] }
  }
  const errors = result.error.issues.map(
    (i) => `${context}: ${i.path.join('.')} - ${i.message}`
  )
  return { valid: false, data: null, errors }
}

// ---------------------------------------------------------------------------
// Asset Mapper
// ---------------------------------------------------------------------------

export interface AssetSummary {
  id: string
  name: string
  mediaType: string
  storageKey: string
  previewUrl?: string
  durationMs?: number
  width?: number
  height?: number
  sizeBytes?: number
}

export function mapAssetToSummary(asset: Asset): AssetSummary {
  return {
    id: asset.id,
    name: asset.filename || asset.storageKey.split('/').pop() || asset.id,
    mediaType: asset.mediaType,
    storageKey: asset.storageKey,
    previewUrl: `/api/v1/projects/${asset.projectId}/assets/${asset.id}/preview-url`,
    durationMs: asset.durationMs ?? undefined,
    width: asset.width ?? undefined,
    height: asset.height ?? undefined,
    sizeBytes: asset.sizeBytes ?? undefined,
  }
}

export function mapAssetResponse(data: unknown): ValidationResult<Asset> {
  return validateStrict(AssetSchema, data, 'Asset')
}

export function mapAssetListResponse(data: unknown): ValidationResult<Asset[]> {
  if (!Array.isArray(data)) {
    return { valid: false, data: null, errors: ['Asset[]: expected array'] }
  }

  const validated: Asset[] = []
  const errors: string[] = []

  for (let i = 0; i < data.length; i++) {
    const result = validateStrict(AssetSchema, data[i], `Asset[${i}]`)
    if (result.valid && result.data) {
      validated.push(result.data)
    } else {
      errors.push(...result.errors)
    }
  }

  return { valid: true, data: validated, errors }
}

// ---------------------------------------------------------------------------
// RenderJob Mapper
// ---------------------------------------------------------------------------

export function mapRenderJobResponse(data: unknown): ValidationResult<RenderJob> {
  return validateStrict(RenderJobSchema, data, 'RenderJob')
}

export function mapRenderJobSummaryResponse(data: unknown): ValidationResult<RenderJobSummary> {
  return validateStrict(RenderJobSummarySchema, data, 'RenderJobSummary')
}

export function mapRenderJobListResponse(data: unknown): ValidationResult<RenderJobSummary[]> {
  if (!Array.isArray(data)) {
    return { valid: false, data: null, errors: ['RenderJobSummary[]: expected array'] }
  }

  const validated: RenderJobSummary[] = []
  const errors: string[] = []

  for (let i = 0; i < data.length; i++) {
    const result = validateStrict(RenderJobSummarySchema, data[i], `RenderJobSummary[${i}]`)
    if (result.valid && result.data) {
      validated.push(result.data)
    } else {
      errors.push(...result.errors)
    }
  }

  return { valid: true, data: validated, errors }
}

// ---------------------------------------------------------------------------
// Artifact Mapper
// ---------------------------------------------------------------------------

export function mapArtifactResponse(data: unknown): ValidationResult<Artifact> {
  return validateStrict(ArtifactSchema, data, 'Artifact')
}

export function mapArtifactListResponse(data: unknown): ValidationResult<Artifact[]> {
  if (!Array.isArray(data)) {
    return { valid: false, data: null, errors: ['Artifact[]: expected array'] }
  }

  const validated: Artifact[] = []
  const errors: string[] = []

  for (let i = 0; i < data.length; i++) {
    const result = validateStrict(ArtifactSchema, data[i], `Artifact[${i}]`)
    if (result.valid && result.data) {
      validated.push(result.data)
    } else {
      errors.push(...result.errors)
    }
  }

  return { valid: true, data: validated, errors }
}

export function mapRenderJobArtifactResponse(data: unknown): ValidationResult<RenderJobArtifact> {
  return validateStrict(RenderJobArtifactSchema, data, 'RenderJobArtifact')
}

export function mapRenderJobArtifactListResponse(data: unknown): ValidationResult<RenderJobArtifact[]> {
  if (!Array.isArray(data)) {
    return { valid: false, data: null, errors: ['RenderJobArtifact[]: expected array'] }
  }

  const validated: RenderJobArtifact[] = []
  const errors: string[] = []

  for (let i = 0; i < data.length; i++) {
    const result = validateStrict(RenderJobArtifactSchema, data[i], `RenderJobArtifact[${i}]`)
    if (result.valid && result.data) {
      validated.push(result.data)
    } else {
      errors.push(...result.errors)
    }
  }

  return { valid: true, data: validated, errors }
}

// ---------------------------------------------------------------------------
// EffectPack Mapper
// ---------------------------------------------------------------------------

export function mapEffectPackResponse(data: unknown): ValidationResult<EffectPack> {
  return validateStrict(EffectPackSchema, data, 'EffectPack')
}

export function mapEffectPackListResponse(data: unknown): ValidationResult<EffectPack[]> {
  if (!Array.isArray(data)) {
    return { valid: false, data: null, errors: ['EffectPack[]: expected array'] }
  }

  const validated: EffectPack[] = []
  const errors: string[] = []

  for (let i = 0; i < data.length; i++) {
    const result = validateStrict(EffectPackSchema, data[i], `EffectPack[${i}]`)
    if (result.valid && result.data) {
      validated.push(result.data)
    } else {
      errors.push(...result.errors)
    }
  }

  return { valid: true, data: validated, errors }
}
