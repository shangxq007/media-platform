import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { platformClient } from '../../foundation/platformClient'
import { ProjectContextProvider } from '../../foundation/projectContext'
import { loadExplicitRevisionSelection, NleWorkspace } from './NleWorkspace'
import { ScriptedOperationGateway, ScriptedTimelineQueryGateway, mockFailure } from './testing/mocks'
import type { AcceptedOperationResult, CanonicalHeadReference, GatewayResult, OperationPreview, RevisionComparison, RevisionDetail, RevisionListEntry, TimelineQueryGateway } from './gateways'
import {
  ADD_MEDIA_CLIP_DEFINITION,
  ADD_MEDIA_CLIP_PRESENTATION_VERSION,
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  contentHash,
  planDigest,
  projectId,
  revisionId,
  timelineId,
} from './types'

const HASH_A = 'a'.repeat(64)
const HASH_B = 'b'.repeat(64)
const HASH_C = 'c'.repeat(64)
const head = { projectId: projectId('project-1'), timelineId: timelineId('project-1'), revisionId: revisionId('revision-R0'), contentHash: contentHash(HASH_A) }
const revision: RevisionListEntry = { id: head.revisionId, revisionNumber: 1, parentRevisionId: null, source: 'OPERATION', message: 'Base', labels: [], authorUserId: null, createdAt: '2026-01-01T00:00:00Z', isMerge: false }
const detail: RevisionDetail = { revision, changeSummary: { supported: true, tracksAdded: 0, tracksRemoved: 0, tracksModified: 0, clipsAdded: 0, clipsRemoved: 0, clipsModified: 0, assetsAdded: 0, assetsRemoved: 0 }, changeCount: 0 }
const comparison: RevisionComparison = { fromRevision: revision, toRevision: revision, summary: detail.changeSummary, entityChanges: [] }
const preview: OperationPreview = { operation: ADD_MEDIA_CLIP_PREVIEW_OPERATION, planDigest: planDigest(HASH_C), targetTimelineId: head.timelineId, baseRevisionId: head.revisionId, baseContentHash: head.contentHash, expectedChanges: ['clip:clip-1'], validation: ['VALIDATION_OK'], capabilityRequirements: ['TIMELINE_WRITE'], warnings: ['Review exact placement'], failures: [], candidateContentHash: contentHash(HASH_B) }
const applied: AcceptedOperationResult = { status: 'APPLIED', planDigest: preview.planDigest, baseRevisionId: head.revisionId, newRevisionId: revisionId('revision-R1'), newContentHash: contentHash(HASH_B), parentRevisionId: head.revisionId, semanticChanges: ['clip:clip-1'] }
const appliedHead = { ...head, revisionId: revisionId('revision-R1'), contentHash: contentHash(HASH_B) }
const appliedRevision: RevisionListEntry = { ...revision, id: appliedHead.revisionId, revisionNumber: 2, parentRevisionId: head.revisionId, message: 'Accepted clip' }
const noOp: AcceptedOperationResult = { status: 'NO_OP', planDigest: preview.planDigest, baseRevisionId: head.revisionId, newRevisionId: null, newContentHash: head.contentHash, parentRevisionId: null, semanticChanges: [] }
const olderRevision: RevisionListEntry = { ...revision, id: revisionId('revision-older'), revisionNumber: 0, message: 'Older' }
const olderDetail: RevisionDetail = { ...detail, revision: olderRevision, changeCount: 1 }

function queryGateway() {
  return new ScriptedTimelineQueryGateway({ head: { ok: true, value: head }, history: { ok: true, value: [revision] }, detail: { ok: true, value: detail }, comparison: { ok: true, value: comparison } })
}

function renderWorkspace(commandGateway: ScriptedOperationGateway, queries = queryGateway()) {
  vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({ workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1', recentProjects: [{ id: 'project-1', name: 'Launch film' }] })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><NleWorkspace queryGateway={queries} commandGateway={commandGateway} /></ProjectContextProvider></QueryClientProvider>)
}

function fillManualPin() {
  const values: Record<string, string> = {
    'Track ID': 'video-1', 'Clip ID': 'clip-1', 'Media Asset ID': 'media-1', 'Media Stream ID': 'stream-1',
    'Artifact ID': 'artifact-1', 'Source content digest': HASH_B, 'Source start (exact rational)': '0/1',
    'Source end (exact rational)': '10/1', 'Timeline start (exact rational)': '0/1', 'Timeline end (exact rational)': '10/1',
    'Rate numerator': '1', 'Rate denominator': '1', Direction: 'FORWARD',
  }
  for (const [label, value] of Object.entries(values)) fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(accept => { resolve = accept })
  return { promise, resolve }
}

async function prepareConfirmedPreview() {
  expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
  fillManualPin()
  fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))
  await screen.findByText(HASH_C)
  fireEvent.click(screen.getByLabelText(/I confirm this exact frozen draft/i))
}

describe('runtime NLE operation workspace', () => {
  afterEach(() => vi.restoreAllMocks())

  it('fails closed before querying when an explicit revision is absent from projected history', async () => {
    const queries = queryGateway()
    const result = await loadExplicitRevisionSelection(queries, head, [revision], 'revision-unknown')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.code).toBe('VALIDATION')
      expect(result.message).toContain('not present in the authoritative history projection')
    }
    expect(queries.detailCalls).toEqual([])
  })

  it('supports keyboard and button timeline controls with accessible semantics', async () => {
    renderWorkspace(new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied }))
    const timeline = await screen.findByRole('application', { name: /Timeline presentation/i })
    fireEvent.keyDown(timeline, { key: 'ArrowRight' })
    expect(screen.getByText('Playhead 1')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Next step' }))
    expect(screen.getByText('Playhead 2')).toBeTruthy()
    fireEvent.keyDown(timeline, { key: ' ' })
    expect(screen.getByRole('button', { name: 'Pause' })).toBeTruthy()
  })

  it('leaves Space on a focused track button to native activation without toggling playback', async () => {
    renderWorkspace(new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied }))
    await screen.findByRole('application', { name: /Timeline presentation/i })
    const track = screen.getByText('V1').closest('button')
    expect(track).toBeTruthy()
    expect(fireEvent.keyDown(track as HTMLButtonElement, { key: ' ' })).toBe(true)
    expect(screen.getByRole('button', { name: 'Play' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Pause' })).toBeNull()
  })

  it('discards a slower HEAD response after Project scope changes', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'First' }, { id: 'project-2', name: 'Second' }],
    })
    const first = deferred<GatewayResult<CanonicalHeadReference>>()
    const second = deferred<GatewayResult<CanonicalHeadReference>>()
    const project2Head: CanonicalHeadReference = {
      projectId: projectId('project-2'), timelineId: timelineId('project-2'),
      revisionId: revisionId('revision-P2'), contentHash: contentHash('d'.repeat(64)),
    }
    const headCalls: string[] = []
    const queries: TimelineQueryGateway = {
      getHead: async requestedProject => {
        headCalls.push(requestedProject)
        return requestedProject === 'project-1' ? first.promise : second.promise
      },
      listRevisions: async () => ({ ok: true, value: [] }),
      getRevision: async () => ({ ok: true, value: detail }),
      compare: async () => ({ ok: true, value: comparison }),
    }
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><NleWorkspace queryGateway={queries} commandGateway={commands} /></ProjectContextProvider></QueryClientProvider>)
    await waitFor(() => expect(headCalls).toContain('project-1'))
    view.rerender(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-2"><NleWorkspace queryGateway={queries} commandGateway={commands} /></ProjectContextProvider></QueryClientProvider>)
    await waitFor(() => expect(headCalls).toContain('project-2'))
    await act(async () => second.resolve({ ok: true, value: project2Head }))
    expect(await screen.findByText('revision-P2')).toBeTruthy()
    await act(async () => first.resolve({ ok: true, value: head }))
    expect(screen.getByText('revision-P2')).toBeTruthy()
    expect(screen.queryByText('revision-R0')).toBeNull()
  })

  it.each([
    ['APPLIED', applied, appliedHead, [appliedRevision, revision]],
    ['NO_OP', noOp, head, [revision]],
  ] as const)('keeps revision selection visibly disabled through %s apply and authoritative readback', async (_status, accepted, readbackHead, readbackHistory) => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: accepted })
    const queries = queryGateway()
    const applyResponse = deferred<GatewayResult<AcceptedOperationResult>>()
    const headReadback = deferred<GatewayResult<CanonicalHeadReference>>()
    const historyReadback = deferred<GatewayResult<readonly RevisionListEntry[]>>()
    renderWorkspace(commands, queries)
    await prepareConfirmedPreview()
    vi.spyOn(commands, 'applyAddMediaClip').mockImplementation(() => applyResponse.promise)
    vi.spyOn(queries, 'getHead').mockImplementationOnce(() => headReadback.promise)
    vi.spyOn(queries, 'listRevisions').mockImplementationOnce(() => historyReadback.promise)

    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await waitFor(() => expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(true))
    await act(async () => applyResponse.resolve({ ok: true, value: accepted }))
    await screen.findByText('READBACK')
    expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(true)

    await act(async () => {
      headReadback.resolve({ ok: true, value: readbackHead })
      historyReadback.resolve({ ok: true, value: readbackHistory })
    })
    await waitFor(() => expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(false))
  })

  it('defers a Project HEAD load until the original apply response and authoritative readback complete', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'First' }, { id: 'project-2', name: 'Second' }],
    })
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
    const queries = queryGateway()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><NleWorkspace queryGateway={queries} commandGateway={commands} /></ProjectContextProvider></QueryClientProvider>)
    await prepareConfirmedPreview()

    const applyResponse = deferred<GatewayResult<AcceptedOperationResult>>()
    const headReadback = deferred<GatewayResult<CanonicalHeadReference>>()
    const historyReadback = deferred<GatewayResult<readonly RevisionListEntry[]>>()
    const deferredProjectHead = deferred<GatewayResult<CanonicalHeadReference>>()
    const deferredProjectHistory = deferred<GatewayResult<readonly RevisionListEntry[]>>()
    const project2Head: CanonicalHeadReference = {
      projectId: projectId('project-2'), timelineId: timelineId('project-2'),
      revisionId: revisionId('revision-P2'), contentHash: contentHash('d'.repeat(64)),
    }
    const project2Revision: RevisionListEntry = { ...revision, id: project2Head.revisionId, message: 'Project 2 HEAD' }
    const authorityCalls: string[] = []
    vi.spyOn(commands, 'applyAddMediaClip').mockImplementation(() => applyResponse.promise)
    vi.spyOn(queries, 'getHead').mockImplementation(requestedProject => {
      authorityCalls.push(`head:${requestedProject}`)
      return requestedProject === 'project-1' ? headReadback.promise : deferredProjectHead.promise
    })
    vi.spyOn(queries, 'listRevisions').mockImplementation(requestedProject => {
      authorityCalls.push(`history:${requestedProject}`)
      return requestedProject === 'project-1' ? historyReadback.promise : deferredProjectHistory.promise
    })

    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await screen.findByText('PendingApply')
    view.rerender(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-2"><NleWorkspace queryGateway={queries} commandGateway={commands} /></ProjectContextProvider></QueryClientProvider>)
    await act(async () => {})
    expect(authorityCalls).toEqual([])

    await act(async () => applyResponse.resolve({ ok: true, value: applied }))
    await screen.findByText('READBACK')
    expect(authorityCalls).toEqual(['head:project-1', 'history:project-1'])

    await act(async () => {
      headReadback.resolve({ ok: true, value: appliedHead })
      historyReadback.resolve({ ok: true, value: [appliedRevision, revision] })
    })
    await waitFor(() => expect(authorityCalls).toEqual([
      'head:project-1', 'history:project-1', 'head:project-2', 'history:project-2',
    ]))
    expect(screen.getByText(/Server accepted revision revision-R1/)).toBeTruthy()

    await act(async () => {
      deferredProjectHead.resolve({ ok: true, value: project2Head })
      deferredProjectHistory.resolve({ ok: true, value: [project2Revision] })
    })
    expect((await screen.findAllByText('revision-P2')).length).toBeGreaterThan(0)
    expect(authorityCalls.filter(call => call === 'head:project-2')).toHaveLength(1)
    expect(authorityCalls.filter(call => call === 'history:project-2')).toHaveLength(1)
  })

  it('invalidates a pre-apply selection load, preserves apply failure, and permits a later current selection', async () => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, mockFailure('NETWORK'))
    const queries = new ScriptedTimelineQueryGateway({
      head: { ok: true, value: head }, history: { ok: true, value: [revision, olderRevision] },
      detail: { ok: true, value: olderDetail }, comparison: { ok: true, value: comparison },
    })
    const staleSelection = deferred<GatewayResult<RevisionDetail>>()
    const applyResponse = deferred<GatewayResult<AcceptedOperationResult>>()
    renderWorkspace(commands, queries)
    await prepareConfirmedPreview()
    const revisionSpy = vi.spyOn(queries, 'getRevision')
      .mockImplementationOnce(() => staleSelection.promise)
      .mockResolvedValue({ ok: true, value: olderDetail })
    vi.spyOn(commands, 'applyAddMediaClip').mockImplementation(() => applyResponse.promise)

    fireEvent.click(screen.getByText('Older').closest('button') as HTMLButtonElement)
    await screen.findByText('Loading explicit revision…')
    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await waitFor(() => expect((screen.getByText('Older').closest('button') as HTMLButtonElement).disabled).toBe(true))
    await act(async () => staleSelection.resolve({ ok: true, value: olderDetail }))
    expect(screen.queryByText('Selected revision detail')).toBeNull()

    await act(async () => applyResponse.resolve(mockFailure('NETWORK')))
    await screen.findByText('NETWORK')
    expect(screen.queryByText('Selected revision detail')).toBeNull()
    fireEvent.click(screen.getByText('Older').closest('button') as HTMLButtonElement)
    await screen.findByText('Selected revision detail')
    expect(revisionSpy).toHaveBeenCalledTimes(2)
  })

  it('previews, explicitly confirms, and changes canonical HEAD only after APPLIED', async () => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
    const queries = queryGateway()
    renderWorkspace(commands, queries)
    expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
    fillManualPin()
    fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))
    await screen.findByText(HASH_C)
    queries.headResult = { ok: true, value: appliedHead }
    queries.historyResult = { ok: true, value: [appliedRevision, revision] }
    expect((screen.getByRole('button', { name: 'Confirm and apply' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/I confirm this exact frozen draft/i))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await screen.findByText(/Server accepted revision revision-R1/)
    await screen.findByText('Accepted clip')
    expect(commands.previewCalls[0].draft.baseRevisionId).toBe('revision-R0')
    expect(commands.previewCalls[0].draft.baseContentHash).toBe(HASH_A)
    expect(commands.applyCalls[0].draft).toBe(commands.previewCalls[0].draft)
    expect(commands.applyCalls[0].preview).toBe(preview)
    expect(commands.applyCalls).toHaveLength(1)
    expect(queries.headCalls).toEqual(['project-1', 'project-1'])
    expect(queries.historyCalls).toEqual(['project-1', 'project-1'])
    expect(screen.getAllByText('revision-R1').length).toBeGreaterThan(0)
  })

  it('shows frontend-known operation metadata, every frozen draft field, and complete preview evidence before confirmation', async () => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
    renderWorkspace(commands)
    expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
    fillManualPin()
    fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))

    const metadata = await screen.findByRole('region', { name: 'Frontend-known canonical operation metadata' })
    expect(within(metadata).getByText(ADD_MEDIA_CLIP_DEFINITION)).toBeTruthy()
    expect(within(metadata).getByText(ADD_MEDIA_CLIP_PRESENTATION_VERSION)).toBeTruthy()
    expect(within(metadata).getByText(ADD_MEDIA_CLIP_PREVIEW_OPERATION)).toBeTruthy()
    expect(within(metadata).getByText(/not echoed response fields/i)).toBeTruthy()

    const frozenDraft = screen.getByRole('region', { name: 'Exact frozen submitted draft' })
    for (const label of [
      'Base revision', 'Base content hash', 'Track ID', 'Clip ID', 'Media Asset ID', 'Media Stream ID',
      'Artifact ID', 'Source content digest', 'Source start', 'Source end', 'Timeline start', 'Timeline end',
      'Rate numerator', 'Rate denominator', 'Direction',
    ]) expect(within(frozenDraft).getByText(label)).toBeTruthy()
    for (const value of ['revision-R0', HASH_A, 'video-1', 'clip-1', 'media-1', 'stream-1', 'artifact-1', HASH_B, '0/1', '10/1', 'FORWARD']) {
      expect(within(frozenDraft).getAllByText(value).length).toBeGreaterThan(0)
    }

    const serverPreview = screen.getByRole('region', { name: 'Server operation preview' })
    expect(within(serverPreview).getByText('VALIDATION_OK')).toBeTruthy()
    expect(within(serverPreview).getByText('TIMELINE_WRITE')).toBeTruthy()
    expect(within(serverPreview).getByText('Review exact placement')).toBeTruthy()
    expect(within(serverPreview).getByText(/server will replan this exact draft at apply/i)).toBeTruthy()
  })

  it('verifies NO_OP readback against the exact prior base without retrying apply', async () => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: noOp })
    const queries = queryGateway()
    renderWorkspace(commands, queries)
    expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
    fillManualPin()
    fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))
    await screen.findByText(HASH_C)
    fireEvent.click(screen.getByLabelText(/I confirm this exact frozen draft/i))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await screen.findByText(/Server accepted a semantic no-op/)
    await waitFor(() => expect(queries.headCalls).toHaveLength(2))
    expect(queries.historyCalls).toHaveLength(2)
    expect(commands.applyCalls).toHaveLength(1)
    expect(screen.queryByText('VALIDATION')).toBeNull()
    expect(screen.queryByText('UNAVAILABLE')).toBeNull()
  })

  it('retries failed authoritative readback without reapplying and unlocks only after an exact retry', async () => {
    const commands = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
    const queries = queryGateway()
    renderWorkspace(commands, queries)
    expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
    fillManualPin()
    fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))
    await screen.findByText(HASH_C)
    queries.headResult = mockFailure('NETWORK')
    fireEvent.click(screen.getByLabelText(/I confirm this exact frozen draft/i))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm and apply' }))
    await screen.findByText(/Server accepted revision revision-R1/)
    const retry = await screen.findByRole('button', { name: 'Retry authoritative readback' })
    expect((screen.getByRole('button', { name: 'Reload HEAD' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getAllByText('revision-R1').length).toBeGreaterThan(0)

    queries.headResult = { ok: true, value: { ...head, revisionId: revisionId('revision-R2'), contentHash: contentHash(HASH_C) } }
    queries.historyResult = { ok: true, value: [{ ...revision, id: revisionId('revision-R2'), revisionNumber: 3 }] }
    fireEvent.click(retry)
    await waitFor(() => expect(queries.headCalls).toHaveLength(3))
    await waitFor(() => expect((screen.getByRole('button', { name: 'Retry authoritative readback' }) as HTMLButtonElement).disabled).toBe(false))
    expect(screen.getByText(/authoritative readback did not match/)).toBeTruthy()
    expect(screen.queryByText('revision-R2')).toBeNull()
    expect((screen.getByRole('button', { name: 'Reload HEAD' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(true)

    queries.headResult = { ok: true, value: appliedHead }
    queries.historyResult = { ok: true, value: [appliedRevision, revision] }
    fireEvent.click(screen.getByRole('button', { name: 'Retry authoritative readback' }))
    await waitFor(() => expect(queries.headCalls).toHaveLength(4))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Retry authoritative readback' })).toBeNull())
    expect((screen.getByRole('button', { name: 'Reload HEAD' }) as HTMLButtonElement).disabled).toBe(false)
    expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(false)
    expect(commands.applyCalls).toHaveLength(1)
  })

  it('shows typed stale UX and does not assume success', async () => {
    renderWorkspace(new ScriptedOperationGateway(mockFailure('STALE_BASE_REVISION'), { ok: true, value: applied }))
    expect((await screen.findAllByText('revision-R0')).length).toBeGreaterThan(0)
    fillManualPin()
    fireEvent.click(screen.getByRole('button', { name: 'Preview operation' }))
    await screen.findByRole('alert')
    expect(screen.getByText('STALE_BASE_REVISION')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Reload HEAD for re-preview' })).toBeTruthy()
    expect(screen.queryByText(/Server accepted revision/)).toBeNull()
  })
})
