// =============================================================================
// Domain Mappers
// =============================================================================
// Transform validated contract types into domain models.
// These mappers ONLY accept already-validated data.
// They add computed properties but never modify contract fields.
// =============================================================================

import type {
  Asset,
  RenderJob,
  RenderJobSummary,
  Artifact,
  EffectPack,
  EffectPackEffect,
  Project,
} from '../api/contracts/schema'

import type {
  AssetDomain,
  AssetMediaType,
  RenderJobDomain,
  RenderJobStatus,
  RenderJobSummaryDomain,
  ArtifactDomain,
  EffectPackDomain,
  EffectPackEffectDomain,
  ProjectDomain,
} from './models'

// ---------------------------------------------------------------------------
// Formatting Utilities
// ---------------------------------------------------------------------------

function formatBytes(bytes: number | null | undefined): string | null {
  if (bytes == null || bytes < 0) return null
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function formatDuration(ms: number | null | undefined): string | null {
  if (ms == null || ms < 0) return null
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.floor((ms % 60000) / 1000)
  return `${minutes}m ${seconds}s`
}

// ---------------------------------------------------------------------------
// Asset Domain Mapper
// ---------------------------------------------------------------------------

export function toAssetDomain(asset: Asset): AssetDomain {
  const mediaType = asset.mediaType as AssetMediaType

  return {
    ...asset,
    mediaType,
    displayName: asset.filename || asset.storageKey.split('/').pop() || asset.id,
    previewUrl: `/api/v1/projects/${asset.projectId}/assets/${asset.id}/preview-url`,
    isVideo: mediaType === 'VIDEO',
    isImage: mediaType === 'IMAGE',
    isAudio: mediaType === 'AUDIO',
    formattedSize: formatBytes(asset.sizeBytes),
    formattedDuration: formatDuration(asset.durationMs),
  }
}

// ---------------------------------------------------------------------------
// RenderJob Domain Mapper
// ---------------------------------------------------------------------------

const JOB_STATUS_LABELS: Record<string, string> = {
  QUEUED: 'Queued',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

export function toRenderJobDomain(job: RenderJob): RenderJobDomain {
  const status = job.status as RenderJobStatus

  return {
    ...job,
    status,
    isTerminal: status === 'COMPLETED' || status === 'FAILED',
    isRunning: status === 'PROCESSING',
    statusLabel: JOB_STATUS_LABELS[status] ?? status,
  }
}

// ---------------------------------------------------------------------------
// RenderJobSummary Domain Mapper
// ---------------------------------------------------------------------------

export function toRenderJobSummaryDomain(summary: RenderJobSummary): RenderJobSummaryDomain {
  return {
    ...summary,
    statusLabel: JOB_STATUS_LABELS[summary.status] ?? summary.status,
  }
}

// ---------------------------------------------------------------------------
// Artifact Domain Mapper
// ---------------------------------------------------------------------------

export function toArtifactDomain(artifact: Artifact): ArtifactDomain {
  return {
    ...artifact,
    formattedSize: formatBytes(artifact.fileSize) ?? 'Unknown',
    formattedDuration: formatDuration(artifact.duration) ?? 'Unknown',
    hasPreview: !!artifact.outputUrl || !!artifact.thumbnailUrl,
  }
}

// ---------------------------------------------------------------------------
// EffectPack Domain Mapper
// ---------------------------------------------------------------------------

function toEffectParameterDefDomain(
  def: { type: string; defaultValue: unknown; min?: number; max?: number; description: string }
): EffectPackEffectDomain['parameterSchema'][string] {
  return {
    type: def.type as EffectPackEffectDomain['parameterSchema'][string]['type'],
    defaultValue: def.defaultValue,
    min: def.min,
    max: def.max,
    description: def.description,
  }
}

function toEffectPackEffectDomain(effect: EffectPackEffect): EffectPackEffectDomain {
  const paramSchema: Record<string, EffectPackEffectDomain['parameterSchema'][string]> = {}

  for (const [key, value] of Object.entries(effect.parameterSchema)) {
    paramSchema[key] = toEffectParameterDefDomain(value)
  }

  return {
    effectKey: effect.effectKey,
    displayName: effect.displayName,
    category: effect.category,
    description: effect.description,
    parameterSchema: paramSchema,
    defaultValues: effect.defaultValues,
    providerMappings: effect.providerMappings,
    allowedTiers: effect.allowedTiers,
    thumbnailUrl: effect.thumbnailUrl,
  }
}

export function toEffectPackDomain(pack: EffectPack): EffectPackDomain {
  const effects = pack.effects.map(toEffectPackEffectDomain)
  const categories = [...new Set(effects.map(e => e.category))]

  return {
    ...pack,
    builtin: pack.builtin ?? false,
    tenantId: pack.tenantId ?? null,
    effects,
    effectCount: effects.length,
    categories,
  }
}

// ---------------------------------------------------------------------------
// Project Domain Mapper
// ---------------------------------------------------------------------------

export function toProjectDomain(project: Project): ProjectDomain {
  return { ...project }
}
