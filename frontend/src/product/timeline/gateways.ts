import type {
  ApplyCommandId,
  ArtifactId,
  ClipId,
  ContentHash,
  ExactMediaTime,
  MediaAssetId,
  MediaStreamId,
  PlanDigest,
  ProjectId,
  RevisionId,
  TimelineId,
  TrackId,
} from './types'
import { ADD_MEDIA_CLIP_PREVIEW_OPERATION } from './types'

export type GatewayFailureCode =
  | 'STALE_BASE_REVISION'
  | 'STALE_TARGET_REF'
  | 'PLAN_CHANGED'
  | 'AUTHORIZATION_DENIED'
  | 'AUTHORIZATION_CONTEXT_MISMATCH'
  | 'TENANT_CONTEXT_MISMATCH'
  | 'UNAUTHENTICATED'
  | 'VALIDATION'
  | 'UNSUPPORTED'
  | 'CONFLICT'
  | 'NOT_FOUND'
  | 'UNAVAILABLE'
  | 'NETWORK'
  | 'UNKNOWN'

export interface GatewayFailure {
  readonly ok: false
  readonly code: GatewayFailureCode
  readonly message: string
  readonly details: readonly string[]
  readonly retryable: boolean
}

export type GatewayResult<T> = { readonly ok: true; readonly value: T } | GatewayFailure

export interface CanonicalHeadReference {
  readonly projectId: ProjectId
  readonly timelineId: TimelineId
  readonly revisionId: RevisionId
  readonly contentHash: ContentHash
}

export interface RevisionListEntry {
  readonly id: RevisionId
  readonly revisionNumber: number
  readonly parentRevisionId: RevisionId | null
  readonly source: string
  readonly message: string | null
  readonly labels: readonly string[]
  readonly authorUserId: string | null
  readonly createdAt: string
  readonly isMerge: boolean
}

export interface RevisionChangeSummary {
  readonly supported: boolean
  readonly tracksAdded: number
  readonly tracksRemoved: number
  readonly tracksModified: number
  readonly clipsAdded: number
  readonly clipsRemoved: number
  readonly clipsModified: number
  readonly assetsAdded: number
  readonly assetsRemoved: number
}

export interface RevisionDetail {
  readonly revision: RevisionListEntry
  readonly changeSummary: RevisionChangeSummary
  readonly changeCount: number
}

export interface SemanticEntityChange {
  readonly kind: string
  readonly entityId: string
  readonly action: string
}

export interface RevisionComparison {
  readonly fromRevision: RevisionListEntry
  readonly toRevision: RevisionListEntry
  readonly summary: RevisionChangeSummary
  readonly entityChanges: readonly SemanticEntityChange[]
}

export interface TimelineQueryGateway {
  getHead(projectId: ProjectId): Promise<GatewayResult<CanonicalHeadReference>>
  listRevisions(projectId: ProjectId): Promise<GatewayResult<readonly RevisionListEntry[]>>
  getRevision(projectId: ProjectId, revisionId: RevisionId): Promise<GatewayResult<RevisionDetail>>
  compare(projectId: ProjectId, from: RevisionId, to: RevisionId): Promise<GatewayResult<RevisionComparison>>
}

export interface AddMediaClipCommandDraft {
  readonly baseRevisionId: RevisionId
  readonly baseContentHash: ContentHash
  readonly trackId: TrackId
  readonly clipId: ClipId
  readonly mediaAssetId: MediaAssetId
  readonly mediaStreamId: MediaStreamId
  readonly artifactId: ArtifactId
  readonly contentDigest: ContentHash
  readonly sourceStart: ExactMediaTime
  readonly sourceEnd: ExactMediaTime
  readonly timelineStart: ExactMediaTime
  readonly timelineEnd: ExactMediaTime
  readonly rateNumerator: number
  readonly rateDenominator: number
  readonly direction: 'FORWARD' | 'REVERSE'
}

export interface OperationPreview {
  readonly operation: typeof ADD_MEDIA_CLIP_PREVIEW_OPERATION
  readonly planDigest: PlanDigest
  readonly targetTimelineId: TimelineId
  readonly baseRevisionId: RevisionId
  readonly baseContentHash: ContentHash
  readonly expectedChanges: readonly string[]
  readonly validation: readonly string[]
  readonly capabilityRequirements: readonly string[]
  readonly warnings: readonly string[]
  readonly failures: readonly string[]
  readonly candidateContentHash: ContentHash
}

export interface AcceptedOperationResult {
  readonly status: 'APPLIED' | 'NO_OP'
  readonly planDigest: PlanDigest
  readonly baseRevisionId: RevisionId
  readonly newRevisionId: RevisionId | null
  readonly newContentHash: ContentHash
  readonly parentRevisionId: RevisionId | null
  readonly semanticChanges: readonly string[]
}

export interface OperationGateway {
  previewAddMediaClip(tenantId: string, projectId: ProjectId, draft: AddMediaClipCommandDraft): Promise<GatewayResult<OperationPreview>>
  applyAddMediaClip(
    tenantId: string,
    projectId: ProjectId,
    draft: AddMediaClipCommandDraft,
    confirmedPreview: OperationPreview,
    commandId: ApplyCommandId,
  ): Promise<GatewayResult<AcceptedOperationResult>>
}

export interface AssetSourcePin {
  readonly mediaAssetId: MediaAssetId
  readonly mediaStreamId: MediaStreamId
  readonly artifactId: ArtifactId
  readonly contentDigest: ContentHash
}

export interface AssetGateway {
  listSourcePins(projectId: ProjectId): Promise<GatewayResult<readonly AssetSourcePin[]>>
}

export type CapabilityState = 'AVAILABLE' | 'UNKNOWN' | 'UNAVAILABLE'
export interface CapabilityProjection { readonly state: CapabilityState; readonly reason: string }
export interface CapabilityGateway {
  getOperationCapability(projectId: ProjectId): Promise<GatewayResult<CapabilityProjection>>
}
