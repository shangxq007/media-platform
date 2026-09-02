import { describe, expect, it } from 'vitest'
import { initialTimelineEditorState, timelineEditorReducer, type TimelineEditorState } from './editor-state'
import type {
  AcceptedOperationResult,
  AddMediaClipCommandDraft,
  GatewayFailure,
  OperationPreview,
  RevisionComparison,
  RevisionDetail,
  RevisionListEntry,
} from './gateways'
import {
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  applyCommandId,
  artifactId,
  clipId,
  contentHash,
  exactMediaTime,
  mediaAssetId,
  mediaStreamId,
  planDigest,
  projectId,
  revisionId,
  timelineId,
  trackId,
} from './types'

const HASH_A = 'a'.repeat(64)
const HASH_B = 'b'.repeat(64)
const commandId = applyCommandId('apply-command-1')
const wrongCommandId = applyCommandId('apply-command-2')
const head = {
  projectId: projectId('project-1'), timelineId: timelineId('project-1'),
  revisionId: revisionId('revision-R0'), contentHash: contentHash(HASH_A),
}
const command: AddMediaClipCommandDraft = {
  baseRevisionId: head.revisionId, baseContentHash: head.contentHash, trackId: trackId('video-1'),
  clipId: clipId('clip-1'), mediaAssetId: mediaAssetId('media-1'), mediaStreamId: mediaStreamId('stream-1'),
  artifactId: artifactId('artifact-1'), contentDigest: contentHash(HASH_B), sourceStart: exactMediaTime('0/1'),
  sourceEnd: exactMediaTime('10/1'), timelineStart: exactMediaTime('0/1'), timelineEnd: exactMediaTime('10/1'),
  rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD',
}
const preview: OperationPreview = {
  operation: ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  planDigest: planDigest('c'.repeat(64)), targetTimelineId: timelineId('project-1'), baseRevisionId: head.revisionId,
  baseContentHash: head.contentHash, expectedChanges: ['clip:clip-1'], validation: [], capabilityRequirements: [],
  warnings: [], failures: [], candidateContentHash: contentHash(HASH_B),
}
const applied: AcceptedOperationResult = {
  status: 'APPLIED', planDigest: preview.planDigest, baseRevisionId: head.revisionId,
  newRevisionId: revisionId('revision-R1'), newContentHash: preview.candidateContentHash,
  parentRevisionId: head.revisionId, semanticChanges: ['clip:clip-1'],
}
const noOp: AcceptedOperationResult = {
  status: 'NO_OP', planDigest: preview.planDigest, baseRevisionId: head.revisionId,
  newRevisionId: null, newContentHash: head.contentHash, parentRevisionId: null, semanticChanges: [],
}
const summary = {
  supported: true, tracksAdded: 0, tracksRemoved: 0, tracksModified: 0,
  clipsAdded: 0, clipsRemoved: 0, clipsModified: 0, assetsAdded: 0, assetsRemoved: 0,
}
const baseRevision: RevisionListEntry = {
  id: head.revisionId, revisionNumber: 1, parentRevisionId: null, source: 'OPERATION',
  message: 'Base', labels: [], authorUserId: null, createdAt: '2026-01-01T00:00:00Z', isMerge: false,
}
const appliedRevision: RevisionListEntry = {
  ...baseRevision, id: revisionId('revision-R1'), revisionNumber: 2,
  parentRevisionId: head.revisionId, message: 'Applied',
}
const olderRevision: RevisionListEntry = {
  ...baseRevision, id: revisionId('revision-older'), revisionNumber: 0, message: 'Older',
}
const olderDetail: RevisionDetail = { revision: olderRevision, changeSummary: summary, changeCount: 1 }
const headDetail: RevisionDetail = { revision: baseRevision, changeSummary: summary, changeCount: 0 }
const comparison: RevisionComparison = {
  fromRevision: olderRevision, toRevision: baseRevision, summary, entityChanges: [],
}

function loaded(): TimelineEditorState {
  return timelineEditorReducer(initialTimelineEditorState, { type: 'HEAD_LOADED', head, revisions: [baseRevision, olderRevision] })
}

function pending(): TimelineEditorState {
  let state = loaded()
  state = timelineEditorReducer(state, { type: 'DRAFT_CHANGED', command })
  state = timelineEditorReducer(state, { type: 'PREVIEW_ACCEPTED', preview })
  return timelineEditorReducer(state, { type: 'APPLY_STARTED', commandId, selectionGeneration: 1 })
}

function selectionRequest(generation: number) {
  return { purpose: 'USER_SELECTION' as const, generation }
}

function selectRevision(state: TimelineEditorState, detail: RevisionDetail, generation = 1): TimelineEditorState {
  const request = selectionRequest(generation)
  state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request })
  return timelineEditorReducer(state, { type: 'REVISION_LOADED', request, detail })
}

function acceptedApplied(): TimelineEditorState {
  return timelineEditorReducer(pending(), { type: 'APPLY_ACCEPTED', commandId, accepted: applied })
}

function acceptedNoOp(): TimelineEditorState {
  return timelineEditorReducer(pending(), { type: 'APPLY_ACCEPTED', commandId, accepted: noOp })
}

function failure(code: GatewayFailure['code']): GatewayFailure {
  return { ok: false, code, message: code, details: [], retryable: false }
}

describe('pure Timeline revision and operation state machine', () => {
  it('updates canonical projection only after a fully bound APPLIED server response', () => {
    const before = pending()
    expect(before.phase.kind).toBe('PendingApply')
    expect(before.server.head).toBe(head)
    const state = timelineEditorReducer(before, { type: 'APPLY_ACCEPTED', commandId, accepted: applied })
    expect(state.phase.kind).toBe('AppliedRevision')
    expect(state.server.head?.revisionId).toBe('revision-R1')
    expect(state.server.head?.contentHash).toBe(HASH_B)
    expect(state.projection.acceptedRevisionId).toBe('revision-R1')
  })

  it('accepts a fully bound NO_OP only while PendingApply and keeps exact HEAD', () => {
    const state = timelineEditorReducer(pending(), { type: 'APPLY_ACCEPTED', commandId, accepted: noOp })
    expect(state.server.head).toBe(head)
    expect(state.phase.kind).toBe('AppliedRevision')
    expect(state.projection.acceptedRevisionId).toBe(head.revisionId)
  })

  it('ignores an out-of-phase or delayed accepted result without changing canonical state', () => {
    const before = loaded()
    const state = timelineEditorReducer(before, { type: 'APPLY_ACCEPTED', commandId, accepted: applied })
    expect(state).toBe(before)
    expect(state.server.head).toBe(head)
    expect(state.projection.acceptedRevisionId).toBeNull()
  })

  it('rejects an accepted result owned by a different local command submission', () => {
    const before = pending()
    const state = timelineEditorReducer(before, { type: 'APPLY_ACCEPTED', commandId: wrongCommandId, accepted: applied })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('Conflict')
    expect(state.draft.previewRequired).toBe(true)
  })

  it.each([
    ['digest', { planDigest: planDigest('d'.repeat(64)) }],
    ['base', { baseRevisionId: revisionId('revision-R9') }],
    ['parent', { parentRevisionId: revisionId('revision-R9') }],
    ['candidate hash', { newContentHash: contentHash(HASH_A) }],
    ['required revision', { newRevisionId: null }],
  ])('rejects an unbound PendingApply APPLIED result with a typed conflict: %s', (_label, mutation) => {
    const before = pending()
    const state = timelineEditorReducer(before, { type: 'APPLY_ACCEPTED', commandId, accepted: { ...applied, ...mutation } })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('Conflict')
    expect(state.draft.previewRequired).toBe(true)
  })

  it.each([
    ['created revision', { newRevisionId: revisionId('revision-R1') }],
    ['parent revision', { parentRevisionId: head.revisionId }],
    ['changed hash', { newContentHash: contentHash(HASH_B) }],
  ])('rejects an unsafe PendingApply NO_OP result: %s', (_label, mutation) => {
    const before = pending()
    const state = timelineEditorReducer(before, { type: 'APPLY_ACCEPTED', commandId, accepted: { ...noOp, ...mutation } })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('Conflict')
  })

  it('hostile A: PendingApply rejects an ordinary HEAD replacement and retains apply ownership', () => {
    const newerHead = { ...head, revisionId: revisionId('revision-R2'), contentHash: contentHash('d'.repeat(64)) }
    const reloaded = timelineEditorReducer(pending(), { type: 'HEAD_LOADED', head: newerHead, revisions: [] })
    const state = timelineEditorReducer(reloaded, { type: 'APPLY_ACCEPTED', commandId, accepted: applied })
    expect(reloaded.phase.kind).toBe('PendingApply')
    expect(reloaded.server.head).toBe(head)
    expect(state.phase.kind).toBe('AppliedRevision')
    expect(state.server.head?.revisionId).toBe(applied.newRevisionId)
  })

  it('verifies APPLIED readback only with the exact accepted HEAD and accepted child history', () => {
    const before = acceptedApplied()
    const state = timelineEditorReducer(before, {
      type: 'APPLY_READBACK_VERIFIED',
      head: { ...head, revisionId: appliedRevision.id, contentHash: preview.candidateContentHash },
      revisions: [appliedRevision, baseRevision],
    })
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision') expect(state.phase.readback).toBe('VERIFIED')
    expect(state.server.revisions).toEqual([appliedRevision, baseRevision])
  })

  it.each([
    ['project', { projectId: projectId('project-other') }],
    ['timeline', { timelineId: timelineId('timeline-other') }],
    ['revision', { revisionId: revisionId('revision-other') }],
    ['hash', { contentHash: contentHash('d'.repeat(64)) }],
  ])('rejects APPLIED readback with a mismatched %s without replacing the accepted projection', (_label, mutation) => {
    const before = acceptedApplied()
    const acceptedHead = before.server.head
    const state = timelineEditorReducer(before, {
      type: 'APPLY_READBACK_VERIFIED',
      head: { ...acceptedHead!, ...mutation },
      revisions: [appliedRevision, baseRevision],
    })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision') {
      expect(typeof state.phase.readback).toBe('object')
      if (typeof state.phase.readback === 'object') expect(state.phase.readback.code).toBe('VALIDATION')
    }
  })

  it.each([
    ['missing accepted revision', [baseRevision]],
    ['wrong accepted parent', [{ ...appliedRevision, parentRevisionId: revisionId('revision-other') }, baseRevision]],
  ])('rejects APPLIED readback history when %s', (_label, revisions) => {
    const before = acceptedApplied()
    const state = timelineEditorReducer(before, {
      type: 'APPLY_READBACK_VERIFIED', head: before.server.head!, revisions,
    })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision' && typeof state.phase.readback === 'object') {
      expect(state.phase.readback.code).toBe('VALIDATION')
      expect(state.phase.readback.message).toContain('history')
    }
  })

  it('verifies NO_OP readback only at the exact prior HEAD with its history entry', () => {
    const before = acceptedNoOp()
    const state = timelineEditorReducer(before, {
      type: 'APPLY_READBACK_VERIFIED', head, revisions: [baseRevision, olderRevision],
    })
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision') expect(state.phase.readback).toBe('VERIFIED')
    expect(state.server.head).toBe(head)
  })

  it.each([
    ['changed HEAD', { ...head, revisionId: revisionId('revision-other') }, [baseRevision]],
    ['missing base history', head, [olderRevision]],
  ])('rejects NO_OP readback with %s and preserves the accepted projection', (_label, readbackHead, revisions) => {
    const before = acceptedNoOp()
    const state = timelineEditorReducer(before, {
      type: 'APPLY_READBACK_VERIFIED', head: readbackHead, revisions,
    })
    expect(state.server).toBe(before.server)
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision' && typeof state.phase.readback === 'object') {
      expect(state.phase.readback.code).toBe('VALIDATION')
    }
  })

  it('hostile B: only the current typed user-selection generation may load a revision', () => {
    const staleRequest = selectionRequest(1)
    const currentRequest = selectionRequest(2)
    let state = timelineEditorReducer(loaded(), { type: 'REVISION_SELECTION_STARTED', request: staleRequest })
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: currentRequest })
    const beforeStaleLoad = state
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: staleRequest, detail: olderDetail })
    expect(state).toBe(beforeStaleLoad)
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: currentRequest, detail: headDetail })
    expect(state.server.selected).toBe(headDetail)
  })

  it('hostile C: ordinary user-selection events cannot displace PendingApply', () => {
    const before = pending()
    const request = selectionRequest(2)
    const started = timelineEditorReducer(before, { type: 'REVISION_SELECTION_STARTED', request })
    const loadedRevision = timelineEditorReducer(started, { type: 'REVISION_LOADED', request, detail: olderDetail })
    expect(started).toBe(before)
    expect(loadedRevision).toBe(before)
    expect(loadedRevision.phase.kind).toBe('PendingApply')
  })

  it.each([
    ['APPLIED', acceptedApplied()],
    ['NO_OP', acceptedNoOp()],
  ])('hostile D: %s preserves authoritative readback priority over user selection', (_status, before) => {
    const request = selectionRequest(2)
    const started = timelineEditorReducer(before, { type: 'REVISION_SELECTION_STARTED', request })
    const loadedRevision = timelineEditorReducer(started, { type: 'REVISION_LOADED', request, detail: olderDetail })
    expect(started).toBe(before)
    expect(loadedRevision).toBe(before)
    expect(loadedRevision.phase.kind).toBe('AppliedRevision')
  })

  it('hostile E: apply failure releases ownership without admitting a pre-apply selection result', () => {
    const staleRequest = selectionRequest(1)
    let state = timelineEditorReducer(loaded(), { type: 'REVISION_SELECTION_STARTED', request: staleRequest })
    state = timelineEditorReducer(state, { type: 'DRAFT_CHANGED', command })
    state = timelineEditorReducer(state, { type: 'PREVIEW_ACCEPTED', preview })
    state = timelineEditorReducer(state, { type: 'APPLY_STARTED', commandId, selectionGeneration: 2 })
    state = timelineEditorReducer(state, { type: 'OPERATION_FAILED', failure: failure('NETWORK') })
    const afterFailure = state
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: staleRequest, detail: olderDetail })
    expect(state).toBe(afterFailure)

    const laterRequest = selectionRequest(3)
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: laterRequest })
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: laterRequest, detail: olderDetail })
    expect(state.server.selected).toBe(olderDetail)
    expect(state.phase.kind).toBe('LoadedRevision')
  })

  it('hostile F: a valid user selection succeeds only after authoritative readback verifies', () => {
    let state = acceptedApplied()
    const request = selectionRequest(2)
    state = timelineEditorReducer(state, {
      type: 'APPLY_READBACK_VERIFIED', head: state.server.head!, revisions: [appliedRevision, baseRevision],
    })
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request })
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request, detail: olderDetail })
    expect(state.server.selected).toBe(olderDetail)
    expect(state.phase.kind).toBe('LoadedRevision')
  })

  it('keeps failed readback ownership through hostile reloads and mismatches until an exact retry verifies', () => {
    const staleRequest = selectionRequest(1)
    const currentRequest = selectionRequest(2)
    const hostileHead = { ...head, revisionId: revisionId('revision-hostile'), contentHash: contentHash('d'.repeat(64)) }
    let state = timelineEditorReducer(acceptedApplied(), {
      type: 'APPLY_READBACK_FAILED', failure: failure('UNAVAILABLE'),
    })
    const failedReadback = state

    state = timelineEditorReducer(state, { type: 'HEAD_LOADED', head: hostileHead, revisions: [] })
    expect(state).toBe(failedReadback)
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: staleRequest, detail: olderDetail })
    expect(state).toBe(failedReadback)

    state = timelineEditorReducer(state, {
      type: 'APPLY_READBACK_VERIFIED', head: hostileHead, revisions: [appliedRevision, baseRevision],
    })
    expect(state.server).toBe(failedReadback.server)
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision') expect(typeof state.phase.readback).toBe('object')
    const mismatchedRetry = state
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: currentRequest })
    expect(state).toBe(mismatchedRetry)

    state = timelineEditorReducer(state, {
      type: 'APPLY_READBACK_VERIFIED', head: state.server.head!, revisions: [appliedRevision, baseRevision],
    })
    expect(state.phase.kind).toBe('AppliedRevision')
    if (state.phase.kind === 'AppliedRevision') expect(state.phase.readback).toBe('VERIFIED')
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: currentRequest })
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: currentRequest, detail: olderDetail })
    expect(state.server.selected).toBe(olderDetail)
    expect(state.phase.kind).toBe('LoadedRevision')
  })

  it('clears selected detail and comparison when authoritative HEAD reloads', () => {
    const request = selectionRequest(1)
    let state = selectRevision(loaded(), olderDetail)
    state = timelineEditorReducer(state, { type: 'COMPARISON_LOADED', request, comparison })
    expect(state.server.comparison).toBe(comparison)
    state = timelineEditorReducer(state, { type: 'HEAD_LOADED', head, revisions: [baseRevision, olderRevision] })
    expect(state.server.selected).toBeNull()
    expect(state.server.comparison).toBeNull()
    expect(state.projection.selectedRevisionId).toBeNull()
  })

  it('clears an old comparison at selection start and leaves it null when compare fails', () => {
    const firstRequest = selectionRequest(1)
    const secondRequest = selectionRequest(2)
    let state = selectRevision(loaded(), olderDetail)
    state = timelineEditorReducer(state, { type: 'COMPARISON_LOADED', request: firstRequest, comparison })
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: secondRequest })
    expect(state.server.comparison).toBeNull()
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: secondRequest, detail: olderDetail })
    expect(state.server.comparison).toBeNull()
  })

  it('keeps comparison null when selecting current HEAD and rejects a delayed prior comparison', () => {
    const firstRequest = selectionRequest(1)
    const secondRequest = selectionRequest(2)
    let state = selectRevision(loaded(), olderDetail)
    state = timelineEditorReducer(state, { type: 'REVISION_SELECTION_STARTED', request: secondRequest })
    state = timelineEditorReducer(state, { type: 'REVISION_LOADED', request: secondRequest, detail: headDetail })
    const afterHeadSelection = state
    state = timelineEditorReducer(state, { type: 'COMPARISON_LOADED', request: firstRequest, comparison })
    expect(state).toBe(afterHeadSelection)
    expect(state.server.comparison).toBeNull()
  })

  it.each([
    ['swapped pair', { ...comparison, fromRevision: baseRevision, toRevision: olderRevision }],
    ['wrong from', { ...comparison, fromRevision: appliedRevision }],
    ['wrong HEAD target', { ...comparison, toRevision: appliedRevision }],
  ])('rejects a stale or mismatched ordered comparison: %s', (_label, hostileComparison) => {
    const request = selectionRequest(1)
    let state = selectRevision(loaded(), olderDetail)
    const before = state
    state = timelineEditorReducer(state, { type: 'COMPARISON_LOADED', request, comparison: hostileComparison })
    expect(state).toBe(before)
    expect(state.server.comparison).toBeNull()
  })

  it.each([
    ['STALE_BASE_REVISION', 'StaleBase'], ['STALE_TARGET_REF', 'StaleBase'], ['PLAN_CHANGED', 'Conflict'],
    ['AUTHORIZATION_DENIED', 'Unauthorized'], ['VALIDATION', 'Unavailable'], ['UNSUPPORTED', 'Unavailable'],
    ['UNAVAILABLE', 'Unavailable'], ['NETWORK', 'Unavailable'],
  ] as const)('does not mutate canonical state for %s and requires a new preview', (code, phase) => {
    const before = loaded()
    const state = timelineEditorReducer(before, { type: 'OPERATION_FAILED', failure: failure(code) })
    expect(state.server.head).toBe(head)
    expect(state.phase.kind).toBe(phase)
    expect(state.draft.previewRequired).toBe(true)
  })

  it('provides explicit rollback and re-preview transitions', () => {
    let state = timelineEditorReducer(loaded(), { type: 'DRAFT_CHANGED', command })
    state = timelineEditorReducer(state, { type: 'PREVIEW_ACCEPTED', preview })
    state = timelineEditorReducer(state, { type: 'REPREVIEW_REQUIRED' })
    expect(state.draft.preview).toBeNull()
    expect(state.phase.kind).toBe('EditingBase')
    state = timelineEditorReducer(state, { type: 'ROLLBACK_DRAFT' })
    expect(state.phase.kind).toBe('CanonicalHead')
  })
})
