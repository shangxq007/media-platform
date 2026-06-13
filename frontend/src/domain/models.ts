// =============================================================================
// Domain Models
// =============================================================================
// UI components MUST only consume domain models, never raw API types.
// Domain models are the single source of truth for frontend state.
//
// Rule: All domain models are derived from validated contract schemas.
// Rule: Domain models may add computed properties but never remove contract fields.
// =============================================================================

// ---------------------------------------------------------------------------
// Asset Domain Model
// ---------------------------------------------------------------------------
export interface AssetDomain {
  readonly id: string
  readonly tenantId: string
  readonly projectId: string
  readonly storageKey: string
  readonly mediaType: AssetMediaType
  readonly filename: string
  readonly sizeBytes: number | null
  readonly checksum: string | null
  readonly durationMs: number | null
  readonly width: number | null
  readonly height: number | null
  readonly createdAt: string

  // Computed domain properties
  readonly displayName: string
  readonly previewUrl: string
  readonly isVideo: boolean
  readonly isImage: boolean
  readonly isAudio: boolean
  readonly formattedSize: string | null
  readonly formattedDuration: string | null
}

export type AssetMediaType = 'VIDEO' | 'IMAGE' | 'AUDIO' | 'SUBTITLE' | 'UNKNOWN'

// ---------------------------------------------------------------------------
// RenderJob Domain Model
// ---------------------------------------------------------------------------
export interface RenderJobDomain {
  readonly id: string
  readonly projectId: string
  readonly status: RenderJobStatus
  readonly format: string
  readonly resolution: string
  readonly profile: string
  readonly artifactId?: string
  readonly createdAt: string

  // Computed domain properties
  readonly isTerminal: boolean
  readonly isRunning: boolean
  readonly statusLabel: string
}

export type RenderJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// ---------------------------------------------------------------------------
// RenderJobSummary Domain Model
// ---------------------------------------------------------------------------
export interface RenderJobSummaryDomain {
  readonly id: string
  readonly projectId: string
  readonly timelineSnapshotId: string
  readonly profile: string
  readonly status: string

  // Computed domain properties
  readonly statusLabel: string
}

// ---------------------------------------------------------------------------
// Artifact Domain Model
// ---------------------------------------------------------------------------
export interface ArtifactDomain {
  readonly id: string
  readonly renderJobId: string
  readonly projectId: string
  readonly name: string
  readonly outputFormat: string
  readonly duration: number
  readonly fileSize: number
  readonly width?: number
  readonly height?: number
  readonly provider: string
  readonly outputUrl?: string
  readonly thumbnailUrl?: string
  readonly renderLogsUrl?: string
  readonly catalogId?: string
  readonly createdAt: string

  // Computed domain properties
  readonly formattedSize: string
  readonly formattedDuration: string
  readonly hasPreview: boolean
}

// ---------------------------------------------------------------------------
// EffectPack Domain Model
// ---------------------------------------------------------------------------
export interface EffectPackDomain {
  readonly packId: string
  readonly version: string
  readonly name: string
  readonly description: string
  readonly author: string
  readonly effects: EffectPackEffectDomain[]
  readonly compatibility: string
  readonly allowedTiers: string[]
  readonly builtin: boolean
  readonly tenantId: string | null

  // Computed domain properties
  readonly effectCount: number
  readonly categories: string[]
}

export interface EffectPackEffectDomain {
  readonly effectKey: string
  readonly displayName: string
  readonly category: string
  readonly description: string
  readonly parameterSchema: Record<string, EffectParameterDefDomain>
  readonly defaultValues: Record<string, unknown>
  readonly providerMappings: string[]
  readonly allowedTiers: string[]
  readonly thumbnailUrl?: string
}

export interface EffectParameterDefDomain {
  readonly type: 'int' | 'float' | 'string' | 'boolean' | 'color'
  readonly defaultValue: unknown
  readonly min?: number
  readonly max?: number
  readonly description: string
}

// ---------------------------------------------------------------------------
// Project Domain Model
// ---------------------------------------------------------------------------
export interface ProjectDomain {
  readonly id: string
  readonly tenantId: string
  readonly name: string
  readonly description: string
  readonly status: string
  readonly createdAt: string
}
