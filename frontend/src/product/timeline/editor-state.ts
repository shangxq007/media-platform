import type {
  AcceptedOperationResult,
  AddMediaClipCommandDraft,
  CanonicalHeadReference,
  GatewayFailure,
  OperationPreview,
  RevisionComparison,
  RevisionDetail,
  RevisionListEntry,
} from './gateways'
import type { ApplyCommandId } from './types'

export interface ServerRevisionState {
  readonly head: CanonicalHeadReference | null
  readonly revisions: readonly RevisionListEntry[]
  readonly selected: RevisionDetail | null
  readonly comparison: RevisionComparison | null
}

export interface DerivedTimelineProjection {
  readonly selectedRevisionId: string | null
  readonly acceptedRevisionId: string | null
}

export interface OperationDraftState {
  readonly command: AddMediaClipCommandDraft | null
  readonly preview: OperationPreview | null
  readonly previewRequired: boolean
}

export interface TimelinePresentationState {
  readonly playheadStep: number
  readonly selectedTrackId: string | null
  readonly playing: boolean
  readonly comparisonActionFilter: string
}

export type EditorPhase =
  | { readonly kind: 'Unavailable'; readonly reason: string }
  | { readonly kind: 'CanonicalHead'; readonly head: CanonicalHeadReference }
  | { readonly kind: 'LoadedRevision'; readonly revision: RevisionDetail }
  | { readonly kind: 'EditingBase'; readonly head: CanonicalHeadReference; readonly preview: OperationPreview | null }
  | { readonly kind: 'PendingPreview'; readonly head: CanonicalHeadReference }
  | {
      readonly kind: 'PendingApply'
      readonly head: CanonicalHeadReference
      readonly preview: OperationPreview
      readonly commandId: ApplyCommandId
    }
  | {
      readonly kind: 'AppliedRevision'
      readonly accepted: AcceptedOperationResult
      readonly readback: 'PENDING' | 'VERIFIED' | GatewayFailure
    }
  | { readonly kind: 'StaleBase'; readonly failure: GatewayFailure }
  | { readonly kind: 'Conflict'; readonly failure: GatewayFailure; readonly requiresPreview: boolean }
  | { readonly kind: 'Unauthorized'; readonly failure: GatewayFailure }

export interface TimelineEditorState {
  readonly server: ServerRevisionState
  readonly projection: DerivedTimelineProjection
  readonly draft: OperationDraftState
  readonly presentation: TimelinePresentationState
  readonly revisionSelectionGeneration: number
  readonly phase: EditorPhase
}

export const initialTimelineEditorState: TimelineEditorState = {
  server: { head: null, revisions: [], selected: null, comparison: null },
  projection: { selectedRevisionId: null, acceptedRevisionId: null },
  draft: { command: null, preview: null, previewRequired: true },
  presentation: { playheadStep: 0, selectedTrackId: null, playing: false, comparisonActionFilter: 'ALL' },
  revisionSelectionGeneration: 0,
  phase: { kind: 'Unavailable', reason: 'Canonical HEAD has not been loaded.' },
}

export interface UserRevisionSelectionRequest {
  readonly purpose: 'USER_SELECTION'
  readonly generation: number
}

export type TimelineEditorEvent =
  | { readonly type: 'HEAD_LOADED'; readonly head: CanonicalHeadReference; readonly revisions: readonly RevisionListEntry[] }
  | { readonly type: 'REVISION_SELECTION_STARTED'; readonly request: UserRevisionSelectionRequest }
  | { readonly type: 'REVISION_LOADED'; readonly request: UserRevisionSelectionRequest; readonly detail: RevisionDetail }
  | { readonly type: 'COMPARISON_LOADED'; readonly request: UserRevisionSelectionRequest; readonly comparison: RevisionComparison }
  | { readonly type: 'DRAFT_CHANGED'; readonly command: AddMediaClipCommandDraft | null }
  | { readonly type: 'PREVIEW_STARTED' }
  | { readonly type: 'PREVIEW_ACCEPTED'; readonly preview: OperationPreview }
  | { readonly type: 'APPLY_STARTED'; readonly commandId: ApplyCommandId; readonly selectionGeneration: number }
  | { readonly type: 'APPLY_ACCEPTED'; readonly commandId: ApplyCommandId; readonly accepted: AcceptedOperationResult }
  | {
      readonly type: 'APPLY_READBACK_VERIFIED'
      readonly head: CanonicalHeadReference
      readonly revisions: readonly RevisionListEntry[]
    }
  | { readonly type: 'APPLY_READBACK_FAILED'; readonly failure: GatewayFailure }
  | { readonly type: 'OPERATION_FAILED'; readonly failure: GatewayFailure }
  | { readonly type: 'ROLLBACK_DRAFT' }
  | { readonly type: 'REPREVIEW_REQUIRED' }
  | { readonly type: 'UNAVAILABLE'; readonly reason: string }
  | { readonly type: 'PLAYHEAD_CHANGED'; readonly step: number }
  | { readonly type: 'PLAYBACK_CHANGED'; readonly playing: boolean }
  | { readonly type: 'TRACK_SELECTED'; readonly trackId: string | null }
  | { readonly type: 'COMPARISON_FILTER_CHANGED'; readonly action: string }

function failurePhase(failure: GatewayFailure): EditorPhase {
  if (failure.code === 'STALE_BASE_REVISION' || failure.code === 'STALE_TARGET_REF') {
    return { kind: 'StaleBase', failure }
  }
  if (failure.code === 'PLAN_CHANGED' || failure.code === 'CONFLICT') {
    return { kind: 'Conflict', failure, requiresPreview: true }
  }
  if (
    failure.code === 'AUTHORIZATION_DENIED'
    || failure.code === 'AUTHORIZATION_CONTEXT_MISMATCH'
    || failure.code === 'TENANT_CONTEXT_MISMATCH'
    || failure.code === 'UNAUTHENTICATED'
  ) {
    return { kind: 'Unauthorized', failure }
  }
  return { kind: 'Unavailable', reason: failure.message }
}

function applyInvariantFailure(state: TimelineEditorState, reason: string): TimelineEditorState {
  const failure: GatewayFailure = {
    ok: false,
    code: 'VALIDATION',
    message: `Rejected unbound apply result: ${reason}`,
    details: [],
    retryable: false,
  }
  return {
    ...state,
    draft: { ...state.draft, previewRequired: true },
    phase: { kind: 'Conflict', failure, requiresPreview: true },
  }
}

function readbackInvariantFailure(state: TimelineEditorState, reason: string): TimelineEditorState {
  if (state.phase.kind !== 'AppliedRevision') return state
  const failure: GatewayFailure = {
    ok: false,
    code: 'VALIDATION',
    message: `Accepted ${state.phase.accepted.status} result was preserved, but authoritative readback ${reason}`,
    details: [],
    retryable: false,
  }
  return { ...state, phase: { ...state.phase, readback: failure } }
}

function sameHead(left: CanonicalHeadReference, right: CanonicalHeadReference): boolean {
  return left.projectId === right.projectId
    && left.timelineId === right.timelineId
    && left.revisionId === right.revisionId
    && left.contentHash === right.contentHash
}

function applyOwnsCanonicalTransition(state: TimelineEditorState): boolean {
  return state.phase.kind === 'PendingApply'
    || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
}

function revisionSelectionLocked(state: TimelineEditorState): boolean {
  return applyOwnsCanonicalTransition(state)
}

export function timelineEditorReducer(state: TimelineEditorState, event: TimelineEditorEvent): TimelineEditorState {
  switch (event.type) {
    case 'HEAD_LOADED':
      if (applyOwnsCanonicalTransition(state)) return state
      return {
        ...state,
        server: { head: event.head, revisions: event.revisions, selected: null, comparison: null },
        projection: { ...state.projection, selectedRevisionId: null },
        draft: { command: null, preview: null, previewRequired: true },
        phase: { kind: 'CanonicalHead', head: event.head },
      }
    case 'REVISION_SELECTION_STARTED':
      return !revisionSelectionLocked(state)
        && event.request.purpose === 'USER_SELECTION'
        && event.request.generation > state.revisionSelectionGeneration
        ? {
            ...state,
            server: { ...state.server, comparison: null },
            revisionSelectionGeneration: event.request.generation,
          }
        : state
    case 'REVISION_LOADED':
      return !revisionSelectionLocked(state)
        && event.request.purpose === 'USER_SELECTION'
        && event.request.generation === state.revisionSelectionGeneration
        ? {
            ...state,
            server: { ...state.server, selected: event.detail, comparison: null },
            projection: { ...state.projection, selectedRevisionId: event.detail.revision.id },
            phase: { kind: 'LoadedRevision', revision: event.detail },
          }
        : state
    case 'COMPARISON_LOADED':
      return !revisionSelectionLocked(state)
        && event.request.purpose === 'USER_SELECTION'
        && event.request.generation === state.revisionSelectionGeneration
        && state.server.head
        && state.server.selected
        && event.comparison.fromRevision.id === state.server.selected.revision.id
        && event.comparison.toRevision.id === state.server.head.revisionId
        ? { ...state, server: { ...state.server, comparison: event.comparison } }
        : state
    case 'DRAFT_CHANGED':
      return {
        ...state,
        draft: { command: event.command, preview: null, previewRequired: true },
        phase: state.server.head
          ? { kind: 'EditingBase', head: state.server.head, preview: null }
          : state.phase,
      }
    case 'PREVIEW_STARTED':
      return state.server.head ? { ...state, phase: { kind: 'PendingPreview', head: state.server.head } } : state
    case 'PREVIEW_ACCEPTED':
      return state.server.head ? {
        ...state,
        draft: { ...state.draft, preview: event.preview, previewRequired: false },
        phase: { kind: 'EditingBase', head: state.server.head, preview: event.preview },
      } : state
    case 'APPLY_STARTED':
      return state.server.head
        && event.selectionGeneration > state.revisionSelectionGeneration
        && state.draft.command
        && state.draft.preview
        && state.draft.command.baseRevisionId === state.server.head.revisionId
        && state.draft.command.baseContentHash === state.server.head.contentHash
        && state.draft.preview.baseRevisionId === state.server.head.revisionId
        && state.draft.preview.baseContentHash === state.server.head.contentHash
        && state.draft.preview.targetTimelineId === state.server.head.timelineId
        ? {
            ...state,
            revisionSelectionGeneration: event.selectionGeneration,
            phase: {
              kind: 'PendingApply',
              head: state.server.head,
              preview: state.draft.preview,
              commandId: event.commandId,
            },
          }
        : state
    case 'APPLY_ACCEPTED': {
      if (state.phase.kind !== 'PendingApply') return state
      const pendingHead = state.phase.head
      const pendingPreview = state.phase.preview
      const pendingCommand = state.draft.command
      if (
        !state.server.head
        || !pendingCommand
        || event.commandId !== state.phase.commandId
        || state.server.head.revisionId !== pendingHead.revisionId
        || state.server.head.contentHash !== pendingHead.contentHash
        || pendingCommand.baseRevisionId !== pendingHead.revisionId
        || pendingCommand.baseContentHash !== pendingHead.contentHash
        || pendingPreview.baseRevisionId !== pendingHead.revisionId
        || pendingPreview.baseContentHash !== pendingHead.contentHash
        || pendingPreview.targetTimelineId !== pendingHead.timelineId
        || event.accepted.planDigest !== pendingPreview.planDigest
        || event.accepted.baseRevisionId !== pendingHead.revisionId
      ) {
        return applyInvariantFailure(state, 'pending HEAD, command base, preview identity, or digest did not match exactly.')
      }
      if (event.accepted.status === 'APPLIED' && (
        !event.accepted.newRevisionId
        || event.accepted.parentRevisionId !== pendingHead.revisionId
        || event.accepted.newContentHash !== pendingPreview.candidateContentHash
      )) {
        return applyInvariantFailure(state, 'APPLIED revision, parent, or candidate hash invariant failed.')
      }
      if (event.accepted.status === 'NO_OP' && (
        event.accepted.newRevisionId !== null
        || event.accepted.parentRevisionId !== null
        || event.accepted.newContentHash !== pendingHead.contentHash
      )) {
        return applyInvariantFailure(state, 'NO_OP null revision or exact base hash invariant failed.')
      }
      const acceptedHead: CanonicalHeadReference = event.accepted.status === 'APPLIED' && event.accepted.newRevisionId
        ? { ...pendingHead, revisionId: event.accepted.newRevisionId, contentHash: event.accepted.newContentHash }
        : pendingHead
      return {
        ...state,
        server: { ...state.server, head: acceptedHead, selected: null, comparison: null },
        projection: {
          ...state.projection,
          selectedRevisionId: null,
          acceptedRevisionId: event.accepted.newRevisionId ?? pendingHead.revisionId,
        },
        draft: { command: null, preview: null, previewRequired: true },
        phase: { kind: 'AppliedRevision', accepted: event.accepted, readback: 'PENDING' },
      }
    }
    case 'APPLY_READBACK_VERIFIED': {
      if (state.phase.kind !== 'AppliedRevision' || !state.server.head) return state
      const accepted = state.phase.accepted
      if (!sameHead(event.head, state.server.head)) {
        return readbackInvariantFailure(state, 'did not match the exact accepted Project, Timeline, revision, and content hash.')
      }
      if (accepted.status === 'APPLIED') {
        const acceptedHistory = accepted.newRevisionId
          ? event.revisions.find(revision => revision.id === accepted.newRevisionId)
          : null
        if (!acceptedHistory || acceptedHistory.parentRevisionId !== accepted.baseRevisionId) {
          return readbackInvariantFailure(state, 'history was missing the accepted revision with its exact base parent.')
        }
      } else {
        const priorHeadHistory = event.revisions.find(revision => revision.id === accepted.baseRevisionId)
        if (
          event.head.revisionId !== accepted.baseRevisionId
          || event.head.contentHash !== accepted.newContentHash
          || !priorHeadHistory
        ) {
          return readbackInvariantFailure(state, 'did not preserve the exact prior HEAD and its history entry for NO_OP.')
        }
      }
      return {
        ...state,
        server: { ...state.server, head: event.head, revisions: event.revisions },
        phase: { ...state.phase, readback: 'VERIFIED' },
      }
    }
    case 'APPLY_READBACK_FAILED':
      return state.phase.kind === 'AppliedRevision'
        ? { ...state, phase: { ...state.phase, readback: event.failure } }
        : state
    case 'OPERATION_FAILED':
      return {
        ...state,
        draft: { ...state.draft, previewRequired: true },
        phase: failurePhase(event.failure),
      }
    case 'ROLLBACK_DRAFT':
      return {
        ...state,
        draft: { command: null, preview: null, previewRequired: true },
        phase: state.server.head ? { kind: 'CanonicalHead', head: state.server.head } : state.phase,
      }
    case 'REPREVIEW_REQUIRED':
      return {
        ...state,
        draft: { ...state.draft, preview: null, previewRequired: true },
        phase: state.server.head ? { kind: 'EditingBase', head: state.server.head, preview: null } : state.phase,
      }
    case 'UNAVAILABLE':
      return { ...state, phase: { kind: 'Unavailable', reason: event.reason } }
    case 'PLAYHEAD_CHANGED':
      return { ...state, presentation: { ...state.presentation, playheadStep: Math.max(0, event.step) } }
    case 'PLAYBACK_CHANGED':
      return { ...state, presentation: { ...state.presentation, playing: event.playing } }
    case 'TRACK_SELECTED':
      return { ...state, presentation: { ...state.presentation, selectedTrackId: event.trackId } }
    case 'COMPARISON_FILTER_CHANGED':
      return { ...state, presentation: { ...state.presentation, comparisonActionFilter: event.action } }
  }
}
