import { useState, type ComponentProps } from 'react'
import { WorkspaceSessionProvider } from '../../foundation/workspaceSession'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import * as editingRefs from '../../api/app/timeline-query.gateway'
import { assetGateway } from '../../api/app/asset.gateway'
import { versionlessApi } from '../../api/app/versionless-api'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import { platformClient } from '../../foundation/platformClient'
import { ProjectContextProvider as ProjectProvider } from '../../foundation/projectContext'
import { ClipEditor } from './ClipEditor'
import { loadExplicitRevisionSelection, NleWorkspace } from './NleWorkspace'
import { ScriptedOperationGateway, ScriptedTimelineQueryGateway, mockFailure } from './testing/mocks'
import type { AcceptedOperationResult, CanonicalHeadReference, GatewayResult, OperationPreview, RevisionComparison, RevisionDetail, RevisionListEntry, TimelineQueryGateway } from './gateways'
import {
  ADD_MEDIA_CLIP_DEFINITION,
  ADD_MEDIA_CLIP_PRESENTATION_VERSION,
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  artifactId,
  exactMediaTime,
  mediaAssetId,
  mediaStreamId,
  sourceContentDigest,
  contentHash,
  planDigest,
  projectId,
  revisionId,
  timelineId,
} from './types'

const HASH_A = 'A'.repeat(43) + '='
const HASH_B = 'B'.repeat(42) + 'E='
const HASH_C = 'c'.repeat(64)
const TIMELINE_C = 'C'.repeat(42) + 'I='
const SOURCE_DIGEST = 'b'.repeat(64)
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
    'Artifact ID': 'artifact-1', 'Source content digest': SOURCE_DIGEST, 'Source start (exact rational)': '0/1',
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

function renderBranchWorkspace(initial: string, clipEnd = '6', withSecondClip = false, initialHead = head) {
  const queries = new ScriptedTimelineQueryGateway({
    head: { ok: true, value: head }, history: { ok: true, value: [revision, olderRevision] },
    detail: { ok: true, value: olderDetail }, comparison: { ok: true, value: comparison },
  })
  const commandGateway = new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied })
  vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
    workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
    recentProjects: [{ id: 'project-1', name: 'Launch film' }],
  })
  vi.spyOn(editingRefs, 'listEditingBranches').mockResolvedValue(['main', 'alt'])
  const readRef = vi.spyOn(editingRefs, 'readEditingBranch')
    .mockResolvedValue({ ok: true, value: { head: initialHead, history: [{ ...revision, id: initialHead.revisionId }, olderRevision] } })
  vi.spyOn(FormalRenderAPI, 'snapshot').mockImplementation(async (_project, selected) => ({
    revisionId: selected,
    schemaVersion: 'timeline-1.0',
    canonicalTimelineJson: JSON.stringify({ schemaVersion: 'timeline-1.0', tracks: [
      { trackId: 'track-1', name: 'Video', type: 'video', clips: [{
        clipId: 'clip-0', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream',
        contentDigest: 'a'.repeat(64), startTime: '0', endTime: clipEnd, trimStart: '0', trimEnd: clipEnd,
        temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
      }, ...(withSecondClip ? [{
        clipId: 'clip-1', sourceKind: 'MEDIA_STREAM', artifactId: 'other', mediaStreamId: 'stream',
        contentDigest: 'b'.repeat(64), startTime: '7', endTime: '8', trimStart: '0', trimEnd: '1',
        temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
      }] : [])] },
    ] }),
  }))
  function Workspace() {
    const [branch, switchBranch] = useState(initial)
    return <NleWorkspace key={branch} editingBranch={branch} onSwitchBranch={switchBranch}
      queryGateway={queries} commandGateway={commandGateway} />
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><Workspace /></ProjectContextProvider></QueryClientProvider>)
  return { queries, readRef }
}

async function prepareNamedTrim() {
  const handle = await screen.findByLabelText('Trim start edge clip-0')
  fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
  fireEvent.pointerMove(handle, { clientX: 180, pointerId: 1 })
  fireEvent.pointerUp(handle, { clientX: 180, pointerId: 1 })
  const apply = screen.getByRole<HTMLButtonElement>('button', { name: 'Apply trim' })
  await waitFor(() => expect(apply.disabled).toBe(false))
  return apply
}

const previewTrim = () => vi.spyOn(FormalRenderAPI, 'previewOperation').mockResolvedValue({
  batchDigest: HASH_C, steps: [{ changes: [{ identity: 'clip-0', primary: true }] }],
})
const previewRestore = () => vi.spyOn(FormalRenderAPI, 'previewRestore').mockImplementation(async (_tenant, _project, body) => ({
  batchId: body.batchId, baseRevisionId: body.baseRevisionId, baseContentHash: body.baseContentHash,
  finalCandidateHash: HASH_B, noOp: false, batchDigest: HASH_C, steps: [],
}))
const olderButton = async () => (await screen.findByText('Older')).closest('button')!
const branchPicker = () => screen.getByRole<HTMLSelectElement>('combobox', { name: 'Editing branch' })

describe('N07 F1 revision-bound Ripple Delete', () => {
  afterEach(() => vi.restoreAllMocks())
  const r1 = appliedHead
  const r2 = { ...head, revisionId: revisionId('revision-R2'), contentHash: contentHash(TIMELINE_C) }
  const r3 = { ...head, revisionId: revisionId('revision-R3') }
  const previewButton = () => screen.getByRole<HTMLButtonElement>('button', { name: 'Preview ripple delete' })
  const confirmButton = () => screen.queryByRole<HTMLButtonElement>('button', { name: 'Confirm ripple delete' })
  const picker = () => screen.getByRole<HTMLSelectElement>('combobox', { name: /^Clip$/ })
  const planned = { batchDigest: HASH_C, steps: [{ changes: [
    { identity: 'remove(clip-0)', primary: true }, { identity: 'replace(clip-1,6/1)', primary: true },
  ] }] }
  type Snapshot = Awaited<ReturnType<typeof FormalRenderAPI.snapshot>>
  const ref = (next: CanonicalHeadReference) => ({ ok: true as const, value: {
    head: next, history: [{ ...appliedRevision, id: next.revisionId }, olderRevision],
  } })

  it('blocks preview until the new HEAD geometry is installed, then applies only the exact new plan', async () => {
    const { readRef } = renderBranchWorkspace('main', '3/1', true, r1)
    const delayed = deferred<Snapshot>()
    const snapshot = vi.mocked(FormalRenderAPI.snapshot)
    const readSnapshot = snapshot.getMockImplementation()!
    snapshot.mockImplementation((project, id) => id === r2.revisionId ? delayed.promise : readSnapshot(project, id))
    const endpoint = '/tenants/tenant-1/projects/project-1/timeline-operations/batch/v2'
    const previews: SemanticBatch[] = []
    const writes: { body: SemanticBatch; ifMatch: unknown }[] = []
    vi.spyOn(versionlessApi, 'post').mockImplementation(async (path, data, config) => {
      const body = data as SemanticBatch
      if (path === endpoint + '/preview') {
        previews.push(body)
        return { data: planned }
      }
      expect(path).toBe(endpoint + '/apply')
      writes.push({ body, ifMatch: config?.headers?.['If-Match'] })
      readRef.mockResolvedValue(ref(r3))
      return { data: { status: 'APPLIED', newRevisionId: r3.revisionId, newContentHash: r3.contentHash } }
    })
    await screen.findByLabelText('Timeline clip clip-0')
    expect(picker().value).toBe('clip-0')
    readRef.mockResolvedValue(ref(r2))
    fireEvent.click(screen.getByRole('button', { name: /^Reload HEAD$/ }))
    await waitFor(() => expect(snapshot.mock.calls.some(([, id]) => id === r2.revisionId)).toBe(true))
    const disabled = previewButton().disabled
    await act(async () => { fireEvent.click(previewButton()) })
    expect({
      disabled, bases: previews.map(body => body.baseRevisionId), applyAttempts: writes.length,
      confirmable: confirmButton()?.disabled === false,
      shownShift: screen.queryByText(/Shift clip-1 left by/)?.textContent,
    }).toEqual({ disabled: true, bases: [], applyAttempts: 0, confirmable: false, shownShift: undefined })

    const next = await readSnapshot('project-1', r2.revisionId)
    const document = JSON.parse(next.canonicalTimelineJson)
    document.tracks[0].clips[0].endTime = '2/1'
    document.tracks[0].clips[0].trimEnd = '2/1'
    await act(async () => delayed.resolve({ ...next, canonicalTimelineJson: JSON.stringify(document) }))
    expect(previewButton().disabled).toBe(false)
    fireEvent.click(previewButton())
    await screen.findByText('Remove clip-0. Shift clip-1 left by 2/1s on this track.')
    expect(previews).toHaveLength(1)
    expect(previews[0]).toMatchObject({
      baseRevisionId: r2.revisionId, baseContentHash: r2.contentHash,
      operations: [{ definitionId: 'timeline.clip.ripple-delete', version: '1.0', target: { clipId: 'clip-0' } }],
    })
    expect(writes).toHaveLength(0)
    fireEvent.click(confirmButton()!)
    await screen.findByText(`Clip clip-0 ripple deleted at revision ${r3.revisionId}.`)
    expect(writes).toEqual([{ body: previews[0], ifMatch: `"${HASH_C}"` }])
  })

  it.each(['base', 'selection round trip'] as const)('ignores a preview completed after a %s change', async (change) => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    vi.spyOn(FormalRenderAPI, 'snapshot').mockImplementation(async (_project, id) => ({
      revisionId: id, schemaVersion: 'timeline-1.0',
      canonicalTimelineJson: JSON.stringify({ schemaVersion: 'timeline-1.0', tracks: [{
        trackId: 'track-1', name: 'Video', type: 'video', clips: ['clip-0', 'clip-1'].map((clipId, index) => ({
          clipId, sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: SOURCE_DIGEST,
          startTime: index ? '7' : '0', endTime: index ? '8' : '3', trimStart: '0', trimEnd: index ? '1' : '3',
          temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
        })),
      }] }),
    }))
    const delayed = deferred<typeof planned>()
    const preview = vi.spyOn(FormalRenderAPI, 'previewOperation').mockReturnValueOnce(delayed.promise).mockResolvedValue(planned)
    const apply = vi.spyOn(FormalRenderAPI, 'applyConfirmedClip')
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = (current: CanonicalHeadReference, focus: string) => <QueryClientProvider client={client}>
      <ProjectContextProvider workspaceId="workspace-1" projectId="project-1">
        <ClipEditor head={current} focusClipId={focus} onRevisionChanged={async () => {}} />
      </ProjectContextProvider>
    </QueryClientProvider>
    const mounted = render(view(r1, 'clip-0'))
    await waitFor(() => expect(previewButton().disabled).toBe(false))
    fireEvent.click(previewButton())
    expect(preview).toHaveBeenCalledTimes(1)
    if (change === 'base') {
      mounted.rerender(view(r2, 'clip-0'))
      await waitFor(() => expect(FormalRenderAPI.snapshot).toHaveBeenCalledWith('project-1', r2.revisionId))
    } else {
      mounted.rerender(view(r1, 'clip-1'))
      expect(picker().value).toBe('clip-1')
      mounted.rerender(view(r1, 'clip-0'))
      expect(picker().value).toBe('clip-0')
    }
    await act(async () => delayed.resolve(planned))
    expect(confirmButton()).toBeNull()
    expect(apply).not.toHaveBeenCalled()
    await waitFor(() => expect(previewButton().disabled).toBe(false))
    fireEvent.click(previewButton())
    await waitFor(() => expect(confirmButton()?.disabled).toBe(false))
    expect(preview).toHaveBeenCalledTimes(2)
    expect(preview.mock.calls[1][2].baseRevisionId).toBe(change === 'base' ? r2.revisionId : r1.revisionId)
    expect(apply).not.toHaveBeenCalled()
  })
})

function defaultTrimWithLostResponse() {
  const { queries, readRef } = renderBranchWorkspace('main')
  const initialHistory = [revision, olderRevision]
  const ledger = [...initialHistory]
  const completed = new Map<string, { status: 'APPLIED'; newRevisionId: string; newContentHash: string }>()
  let current = head
  readRef.mockImplementation(async (_project, ref) => ({ ok: true, value: ref === 'main'
    ? { head: current, history: [...ledger] } : { head, history: initialHistory } }))
  vi.spyOn(FormalRenderAPI, 'head').mockImplementation(async () => current)
  previewTrim()
  const wire: { path: string; body: string; ifMatch: unknown }[] = []
  const send = vi.spyOn(versionlessApi, 'post').mockImplementation(async (path, data, config) => {
    if (path.endsWith('/preview')) return { data: { batchDigest: HASH_C, steps: [] } }
    const body = data as SemanticBatch
    wire.push({ path, body: JSON.stringify(body), ifMatch: config?.headers?.['If-Match'] })
    const received = completed.get(body.batchId)
    if (received) return { data: received }
    if (body.baseRevisionId !== current.revisionId) throw Object.assign(Error('stale'), { response: { status: 409 } })
    const previous = current
    current = completed.size === 0 ? appliedHead : { ...head, revisionId: revisionId('revision-R2') }
    ledger.unshift({ ...appliedRevision, id: current.revisionId, parentRevisionId: previous.revisionId })
    const receipt = { status: 'APPLIED' as const, newRevisionId: current.revisionId, newContentHash: current.contentHash }
    completed.set(body.batchId, receipt)
    if (body.operations[0].definitionId === 'timeline.trim') throw Error('response lost after commit')
    return { data: receipt }
  })
  return { queries, readRef, send, wire, ledger, initialRows: ledger.length }
}

async function verifyDefaultNavigationResumed(queries: ScriptedTimelineQueryGateway,
  readRef: ReturnType<typeof defaultTrimWithLostResponse>['readRef']) {
  const reload = screen.getByRole<HTMLButtonElement>('button', { name: 'Reload HEAD' })
  await waitFor(() => expect(reload.disabled).toBe(false))
  const before = readRef.mock.calls.length
  fireEvent.click(reload)
  await waitFor(() => expect(readRef.mock.calls.length).toBe(before + 1))
  await waitFor(() => expect(reload.disabled).toBe(false))
  const history = await olderButton()
  const detail = vi.spyOn(queries, 'getRevision')
  expect(history.disabled).toBe(false)
  fireEvent.click(history)
  await screen.findByText('Read-only historical revision')
  expect(detail).toHaveBeenCalledWith(head.projectId, olderRevision.id)
  fireEvent.click(screen.getByRole('button', { name: 'Return to editing head' }))
  await screen.findByText('Viewing editing head · main')
  expect(branchPicker().disabled).toBe(false)
  fireEvent.change(branchPicker(), { target: { value: 'alt' } })
  await screen.findByText('Viewing editing head · alt')
  fireEvent.change(branchPicker(), { target: { value: 'main' } })
  await screen.findByText('Viewing editing head · main')
}

describe('default branch clipboard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('keeps copy and preview read-only and retries exactly the issued paste while navigation is locked', async () => {
    const { readRef } = renderBranchWorkspace('main')
    const previewBatch = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: HASH_C, steps: [] })
    const commit = vi.spyOn(FormalRenderAPI, 'commitBatch')
      .mockRejectedValueOnce(new Error('copy transport lost'))
      .mockResolvedValueOnce(appliedHead)
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Copy clip clip-0' }))
    fireEvent.click(screen.getByRole('button', { name: 'Copy selected clips' }))
    expect(previewBatch).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Preview paste at playhead' }))
    const confirm = await screen.findByRole('button', { name: 'Confirm paste' })
    expect(commit).not.toHaveBeenCalled()
    expect(previewBatch.mock.calls[0][2]).toMatchObject({
      baseRevisionId: head.revisionId, baseContentHash: HASH_A,
      operations: [{ definitionId: 'timeline.clip.paste', parameters: {
        trackId: 'track-1', sourceClipIds: ['clip-0'], newClipIds: [expect.any(String)], timelineStart: '0/1',
      } }],
    })
    fireEvent.click(confirm)
    const retry = await screen.findByRole('button', { name: 'Retry confirmed paste' })
    expect(branchPicker().disabled).toBe(true)
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Delete selected clip' }).disabled).toBe(true)
    readRef.mockResolvedValue({ ok: true, value: { head: appliedHead, history: [appliedRevision, revision] } })
    fireEvent.click(retry)
    await screen.findByText('Paste accepted at revision revision-R1.')
    expect(commit).toHaveBeenCalledTimes(2)
    expect(commit.mock.calls[1].slice(0, 5)).toEqual(commit.mock.calls[0].slice(0, 5))
    expect(commit.mock.calls[1][2]).toBe(previewBatch.mock.calls[0][2])
    await waitFor(() => expect(branchPicker().disabled).toBe(false))
    fireEvent.change(branchPicker(), { target: { value: 'alt' } })
    await screen.findByText('Viewing editing head · alt')
    expect(screen.queryByRole('region', { name: 'Clip clipboard' })).toBeNull()
  })
})

describe('N08 ripple insertion', () => {
  afterEach(() => vi.restoreAllMocks())

  it('discards a delayed preview after the insertion time changes and returns to its original value', async () => {
    renderBranchWorkspace('main', '61/30', true)
    const result = { batchDigest: HASH_C, steps: [{ changes: [{ identity: 'replace(clip-1,301/30)', primary: true }] }] }
    const held = deferred<typeof result>()
    const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockReturnValueOnce(held.promise).mockResolvedValue(result)
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Copy clip clip-0' }))
    fireEvent.click(screen.getByRole('button', { name: 'Copy selected clips' }))
    const time = screen.getByLabelText('Ripple insertion time (seconds)')
    const prepare = screen.getByRole('button', { name: 'Preview ripple insert' })
    fireEvent.change(time, { target: { value: '61/30' } })
    fireEvent.click(prepare)
    expect(preview).toHaveBeenCalledOnce()
    fireEvent.change(time, { target: { value: '7' } })
    fireEvent.change(time, { target: { value: '61/30' } })
    await act(async () => held.resolve(result))
    expect(screen.queryByRole('button', { name: 'Confirm ripple insert' })).toBeNull()
    fireEvent.click(prepare)
    await screen.findByText(/Shift right by 61\/30s. Shifted clips: clip-1/)
    expect(preview.mock.lastCall?.[2].operations[0]).toMatchObject({ definitionId: 'timeline.clip.ripple-insert',
      parameters: { sourceClipIds: ['clip-0'], trackId: 'track-1', timelineStart: '61/30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel ripple insert' }))
    expect(screen.queryByRole('button', { name: 'Confirm ripple insert' })).toBeNull()
    expect(apply).not.toHaveBeenCalled()
  })
})

describe('clipboard playhead precision at the editor entry point', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it.each([
    ['201/100', 2.01, '201/100'],
    ['61/30', 61 / 30, '61/30'],
    ['3/1', 2.01, '201/100'],
  ])('previews a paste at the intended playhead for a %s timeline', async (clipEnd, seek, timelineStart) => {
    const duration = exactMediaTime('6/1')
    vi.spyOn(assetGateway, 'listSourcePins').mockResolvedValue({ ok: true, value: [{
      name: 'Fixture', artifactId: artifactId('artifact'), mediaAssetId: mediaAssetId('artifact'),
      mediaStreamId: mediaStreamId('stream'), contentDigest: sourceContentDigest('a'.repeat(64)),
      duration, byteLength: 1, streams: [{ streamId: mediaStreamId('stream'), kind: 'VIDEO', duration }],
    }] })
    vi.spyOn(assetGateway, 'readSourceContent').mockResolvedValue({ ok: true, value: new Blob(['fixture']) })
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL() { return 'blob:preview-fixture' }
      static revokeObjectURL() {}
    })
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(function (this: HTMLMediaElement) {
      this.dispatchEvent(new Event('loadeddata'))
    })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    renderBranchWorkspace('main', clipEnd)
    const previewBatch = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: HASH_C, steps: [] })
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
    await screen.findByText('Preview ready.')
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Copy clip clip-0' }))
    fireEvent.click(screen.getByRole('button', { name: 'Copy selected clips' }))
    fireEvent.change(screen.getByLabelText('Timeline preview seek'), { target: { value: String(seek) } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview paste at playhead' }))
    await screen.findByRole('button', { name: 'Confirm paste' })
    expect(previewBatch).toHaveBeenCalledOnce()
    const step = previewBatch.mock.calls[0][2].operations[0]
    if (step.definitionId !== 'timeline.clip.paste') throw new Error('Expected a paste operation')
    expect(step.parameters.timelineStart).toBe(timelineStart)
    expect(apply).not.toHaveBeenCalled()
  })
})

describe('same-track multi-clip move', () => {
  afterEach(() => vi.restoreAllMocks())

  it('withdraws a valid confirmation when the offset changes to a double sign', async () => {
    renderBranchWorkspace('main','6',true)
    const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: HASH_C, steps: [] })
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Select for move clip-0' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select for move clip-1' }))
    const snap = screen.getByRole('button', { name: /^Snap$/ })
    fireEvent.click(snap)
    expect(snap.getAttribute('aria-pressed')).toBe('true')
    const input = screen.getByLabelText('Move offset (seconds)')
    const button = screen.getByRole('button', { name: 'Preview selected move' })
    for (const [inputText, exact] of [['+2/60','1/30'],['-2/60','-1/30'],['0','0/1']]) {
      fireEvent.change(input, { target: { value: inputText } })
      fireEvent.click(button)
      await screen.findByRole('button', { name: 'Confirm selected move' })
      expect(preview.mock.lastCall?.[2].operations[0].parameters).toEqual({ delta: exact, absolute: false })
    }
    for (const value of ['+-2/60','-+2/60','++1','--1']) {
      fireEvent.change(input, { target: { value } })
      expect(screen.queryByRole('button', { name: 'Confirm selected move' })).toBeNull()
      expect(input.getAttribute('aria-invalid')).toBe('true')
      expect(screen.getByText(/at most one sign/)).toBeTruthy()
      fireEvent.click(button)
    }
    expect(preview).toHaveBeenCalledTimes(3)
    expect(apply).not.toHaveBeenCalled()
  })

  it('projects a selected pair locally and cancels without previewing or writing', async () => {
    renderBranchWorkspace('main','6',true)
    const preview = vi.spyOn(FormalRenderAPI, 'previewBatch')
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Select for move clip-0' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select for move clip-1' }))
    const body = await screen.findByLabelText('Timeline clip clip-0')
    fireEvent.pointerDown(body,{pointerId:1,clientX:100})
    fireEvent.pointerMove(body,{pointerId:1,clientX:180})
    expect(screen.getByText('Moving 2 selected clips by 1/1s.')).toBeTruthy()
    fireEvent.keyDown(body,{key:'Escape'})
    fireEvent.pointerUp(body,{pointerId:1,clientX:180})
    expect(screen.queryByText(/Moving 2 selected clips/)).toBeNull()
    expect(preview).not.toHaveBeenCalled()
    expect(apply).not.toHaveBeenCalled()
  })

  it('previews a canonical signed offset and retains the exact confirmed batch across lost response', async () => {
    renderBranchWorkspace('main', '6', true)
    const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: HASH_C, steps: [] })
    const apply = vi.spyOn(FormalRenderAPI, 'commitBatch').mockRejectedValueOnce(new Error('lost response'))
      .mockResolvedValueOnce({ revisionId: 'moved', contentHash: HASH_B })
    const clip0 = await screen.findByRole('checkbox', { name: 'Select for move clip-0' })
    const clip1 = screen.getByRole('checkbox', { name: 'Select for move clip-1' })
    fireEvent.click(clip1)
    fireEvent.click(clip0)
    expect(screen.getByText('2 selected', { exact: true })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Clear move selection' }))
    expect(screen.getByText('0 selected', { exact: true })).toBeTruthy()
    expect(preview).not.toHaveBeenCalled()
    fireEvent.click(clip0)
    fireEvent.click(clip1)
    fireEvent.change(screen.getByLabelText('Move offset (seconds)'), { target: { value: '-2/60' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview selected move' }))
    const confirm = await screen.findByRole('button', { name: 'Confirm selected move' })
    expect(preview.mock.calls[0][2].operations).toEqual([{ definitionId: 'timeline.move', version: '1.0',
      target: { clipIds: ['clip-0', 'clip-1'] }, parameters: { delta: '-1/30', absolute: false } }])
    expect(apply).not.toHaveBeenCalled()
    fireEvent.click(confirm)
    const retry = await screen.findByRole('button', { name: 'Retry confirmed move' })
    expect(branchPicker().disabled).toBe(true)
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Preview selected move' }).closest('fieldset')?.disabled).toBe(true)
    fireEvent.click(retry)
    await screen.findByText(/Move accepted at revision moved/)
    expect(apply).toHaveBeenCalledTimes(2)
    expect(apply.mock.calls[0].slice(0, 5)).toEqual(apply.mock.calls[1].slice(0, 5))
    expect(apply.mock.calls[0][2]).toBe(preview.mock.calls[0][2])
    expect(apply.mock.calls[1][4]).toBe(HASH_C)
    expect(branchPicker().disabled).toBe(false)
  })
})

describe.each([
  ['Delete', 'timeline.delete'],
  ['Move', 'timeline.move'],
] as const)('N06 F1 ordinary %s receipt and HEAD separation', (label, definition) => {
  afterEach(() => vi.restoreAllMocks())

  it.each(['later-head', 'lost-replay', 'read-recovery', 'stale-409', 'stale-412'] as const)(
    'handles %s through the default editing workspace', async (scenario) => {
      const { readRef, queries } = renderBranchWorkspace('main')
      const original = await screen.findByLabelText('Timeline clip clip-0')
      await screen.findByText('Viewing editing head · main')
      const initialReadCount = readRef.mock.calls.length
      const later = { ...head, revisionId: revisionId('revision-R2'), contentHash: contentHash(TIMELINE_C) }
      const alternate = { ...head, revisionId: revisionId('revision-alt') }
      const laterRow: RevisionListEntry = { ...appliedRevision, id: later.revisionId, revisionNumber: 3,
        parentRevisionId: appliedHead.revisionId }
      const laterBinding = { ok: true as const, value: {
        head: later, history: [laterRow, appliedRevision, revision, olderRevision],
      } }
      const alternateRow = { ...revision, id: alternate.revisionId }
      const receipt = { data: { status: 'APPLIED', newRevisionId: appliedHead.revisionId, newContentHash: appliedHead.contentHash } }
      const delivery = deferred<typeof receipt>()
      type RefResponse = Awaited<ReturnType<typeof editingRefs.readEditingBranch>>
      const failedRead = deferred<RefResponse>()
      const recoveryRead = deferred<RefResponse>()
      const endpoint = '/tenants/tenant-1/projects/project-1/timeline-operations/batch/v2'
      const extraHead = vi.spyOn(FormalRenderAPI, 'head')
        .mockRejectedValue(new Error('The workspace must use its single ref read'))
      const writes: { path: string; body: string; ifMatch: unknown }[] = []
      let previews = 0
      let acceptedWrites = 0
      const transport = vi.spyOn(versionlessApi, 'post').mockImplementation(async (path, data, config) => {
        const body = data as SemanticBatch
        expect(body.baseRevisionId).toBe(head.revisionId)
        expect(body.baseContentHash).toBe(head.contentHash)
        expect(body.batchId).toMatch(/^[0-9a-f-]{36}$/)
        expect(body.operations).toHaveLength(1)
        expect(body.operations[0]).toMatchObject({ definitionId: definition, target: { clipId: 'clip-0' } })
        expect(body.refId ?? 'main').toBe('main')
        if (path === endpoint + '/preview') {
          previews += 1
          return { data: { batchDigest: HASH_C, steps: [] } }
        }
        writes.push({ path, body: JSON.stringify(body), ifMatch: config?.headers?.['If-Match'] })
        expect(path).toBe(endpoint + '/apply')
        if (writes.length === 1) {
          readRef.mockImplementation(async (_project, ref) => ref === 'main'
            ? laterBinding : { ok: true, value: { head: alternate, history: [alternateRow] } })
          if (scenario.startsWith('stale-')) throw Object.assign(Error('Apply conflict'), {
            response: { status: Number(scenario.slice(-3)) },
          })
          acceptedWrites += 1
          if (scenario === 'lost-replay') throw Error('committed response lost')
          if (scenario === 'read-recovery') {
            readRef.mockReturnValueOnce(failedRead.promise).mockReturnValueOnce(recoveryRead.promise)
            return delivery.promise
          }
        }
        return receipt
      })
      const clipRegion = screen.getByRole('region', { name: 'Edit existing clip' })
      const deleteButton = () => within(clipRegion).getByRole<HTMLButtonElement>('button', { name: 'Delete selected clip' })
      const reload = () => screen.getByRole<HTMLButtonElement>('button', { name: /^Reload HEAD$/ })
      if (label === 'Delete') fireEvent.click(deleteButton())
      else {
        fireEvent.pointerDown(original, { clientX: 100, pointerId: 1 })
        fireEvent.pointerMove(original, { clientX: 180, pointerId: 1 })
        fireEvent.pointerUp(original, { clientX: 180, pointerId: 1 })
      }

      if (scenario === 'lost-replay') {
        const retry = await screen.findByRole('button', { name: new RegExp(`Retry ${label} with the same command ID`) })
        expect(readRef).toHaveBeenCalledTimes(initialReadCount)
        expect(branchPicker().disabled).toBe(true)
        expect(deleteButton().disabled).toBe(true)
        fireEvent.keyDown(clipRegion, { key: 'Delete' })
        expect(writes).toHaveLength(1)
        fireEvent.click(retry)
      }
      if (scenario === 'read-recovery') {
        const accepted = new RegExp(`${label} accepted at revision revision-R1.*Reload HEAD`)
        const writeRetry = () => screen.queryByRole('button', { name: new RegExp(`Retry ${label}`) })
        await waitFor(() => {
          expect(writes).toHaveLength(1)
          expect(branchPicker().disabled).toBe(true)
          expect(deleteButton().disabled).toBe(true)
        })
        expect(readRef).toHaveBeenCalledTimes(initialReadCount)
        expect(within(clipRegion).queryByText(accepted)).toBeNull()
        expect(reload().disabled).toBe(true)

        await act(async () => delivery.resolve(receipt))
        expect(readRef).toHaveBeenCalledTimes(initialReadCount + 1)
        expect(readRef).toHaveBeenLastCalledWith(head.projectId, 'main')
        expect(branchPicker().disabled).toBe(true)
        expect(deleteButton().disabled).toBe(true)
        expect(clipRegion.getAttribute('data-viewed-revision')).toBe(head.revisionId)
        expect(writeRetry()).toBeNull()

        // ClipEditor can render the accepted notice before the workspace renders
        // the ownership/clip-lock release. Wait for the actual control to unlock.
        failedRead.resolve(mockFailure('UNAVAILABLE'))
        const outcome = await within(clipRegion).findByText(accepted)
        const recovery = () => label === 'Delete'
          ? within(clipRegion).getByRole<HTMLButtonElement>('button', { name: 'Reload HEAD after accepted edit' })
          : reload()
        await waitFor(() => {
          expect(branchPicker().disabled).toBe(false)
          expect(reload().disabled).toBe(false)
          expect(recovery().disabled).toBe(false)
        })
        expect(outcome.getAttribute('role')).toBe('status')
        expect(deleteButton().disabled).toBe(true)
        expect(writeRetry()).toBeNull()
        expect(readRef).toHaveBeenCalledTimes(initialReadCount + 1)
        expect(writes).toHaveLength(1)

        fireEvent.click(recovery())
        await waitFor(() => expect(readRef).toHaveBeenCalledTimes(initialReadCount + 2))
        expect(readRef).toHaveBeenLastCalledWith(head.projectId, 'main')
        expect(within(clipRegion).getByText(accepted)).toBeTruthy()
        expect(deleteButton().disabled).toBe(true)
        expect(writeRetry()).toBeNull()
        expect(transport).toHaveBeenCalledTimes(2)
        await act(async () => recoveryRead.resolve(laterBinding))
      }

      const stale = scenario.startsWith('stale-')
      if (stale) {
        expect((await within(clipRegion).findByRole('alert')).textContent).toMatch(new RegExp(`${label} rejected.*HEAD has changed`))
        expect(within(clipRegion).queryByText(/accepted at revision/)).toBeNull()
      } else {
        const accepted = await within(clipRegion).findByText(`${label} accepted at revision revision-R1. The current HEAD is revision-R2.`)
        expect(accepted.getAttribute('role')).toBe('status')
        expect(within(clipRegion).queryByText(/rejected/)).toBeNull()
      }
      await waitFor(() => {
        expect(clipRegion.getAttribute('data-viewed-revision')).toBe(later.revisionId)
        expect(deleteButton().disabled).toBe(false)
        expect(reload().disabled).toBe(false)
        expect(branchPicker().disabled).toBe(false)
      })
      expect(acceptedWrites).toBe(stale ? 0 : 1)
      expect(previews).toBe(1)
      expect(writes).toHaveLength(scenario === 'lost-replay' ? 2 : 1)
      expect(writes[0].ifMatch).toBe('"' + HASH_C + '"')
      if (scenario === 'lost-replay') expect(writes[1]).toEqual(writes[0])
      expect(readRef).toHaveBeenCalledTimes(initialReadCount + (scenario === 'read-recovery' ? 2 : 1))
      expect(extraHead).not.toHaveBeenCalled()
      expect(screen.queryByRole('button', { name: /Reload HEAD after accepted edit|Retry (Delete|Move)/ })).toBeNull()
      const beforeNavigation = readRef.mock.calls.length
      const detail = vi.spyOn(queries, 'getRevision')
      const compare = vi.spyOn(queries, 'compare')
      const history = await olderButton()
      await waitFor(() => expect(history.disabled).toBe(false))
      fireEvent.click(history)
      const historical = await screen.findByRole('region', { name: 'Historical timeline' })
      expect(historical.getAttribute('data-viewed-revision')).toBe(olderRevision.id)
      expect(detail).toHaveBeenCalledExactlyOnceWith(head.projectId, olderRevision.id)
      await waitFor(() => expect(compare).toHaveBeenCalledExactlyOnceWith(head.projectId, olderRevision.id, later.revisionId))
      expect(readRef).toHaveBeenCalledTimes(beforeNavigation)

      fireEvent.click(screen.getByRole('button', { name: 'Return to editing head' }))
      const expectHead = async (ref: string, expected: string) => {
        await waitFor(() => {
          expect(branchPicker().value).toBe(ref)
          expect(branchPicker().disabled).toBe(false)
          expect(screen.getByRole('region', { name: 'Edit existing clip' })
            .getAttribute('data-viewed-revision')).toBe(expected)
        })
      }
      await expectHead('main', later.revisionId)
      fireEvent.change(branchPicker(), { target: { value: 'alt' } })
      await expectHead('alt', alternate.revisionId)
      fireEvent.change(branchPicker(), { target: { value: 'main' } })
      await expectHead('main', later.revisionId)
      expect(readRef.mock.calls.slice(beforeNavigation)).toEqual([
        [head.projectId, 'main'], [head.projectId, 'alt'], [head.projectId, 'main'],
      ])
      expect(transport).toHaveBeenCalledTimes(1 + writes.length)
    }
  )
})

describe('confirmed trim history readback recovery', () => {
  afterEach(() => vi.restoreAllMocks())

  const control = (name: 'Undo' | 'Redo') => screen.getByRole<HTMLButtonElement>('button', { name: name + ' trim' })

  function fixture() {
    const { readRef } = renderBranchWorkspace('main')
    previewTrim()
    const trim = vi.spyOn(FormalRenderAPI, 'applyConfirmedClip').mockResolvedValue(appliedHead)
    const restore = previewRestore()
    const write = vi.spyOn(FormalRenderAPI, 'commitBatch')
    const publish = (next: CanonicalHeadReference) => readRef.mockResolvedValue({
      ok: true, value: { head: next, history: [appliedRevision, revision, olderRevision] },
    })
    const failNextRead = () => readRef.mockResolvedValueOnce(mockFailure('UNAVAILABLE'))

    async function reload(next: CanonicalHeadReference) {
      const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Reload HEAD' })
      await waitFor(() => expect(button.disabled).toBe(false))
      const before = readRef.mock.calls.length
      publish(next)
      fireEvent.click(button)
      await waitFor(() => expect(readRef.mock.calls).toHaveLength(before + 1))
      await waitFor(() => expect(button.disabled).toBe(false))
    }
    return { trim, write, restore, publish, failNextRead, reload }
  }

  it('restores Undo after an accepted trim and a failed HEAD read', async () => {
    const f = fixture()
    const applyTrim = await prepareNamedTrim()
    f.failNextRead()
    fireEvent.click(applyTrim)
    await screen.findByText(/Trim accepted at revision revision-R1/)
    expect(control('Undo').disabled).toBe(true)
    expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
    await f.reload(appliedHead)
    expect(control('Undo').disabled).toBe(false)
    expect(control('Redo').disabled).toBe(true)
    expect(f.trim).toHaveBeenCalledOnce()
    expect(f.write).not.toHaveBeenCalled()
  })

  it.each([
    ['Undo', 'Redo', 1],
    ['Redo', 'Undo', 2],
  ] as const)('recovers history after an accepted %s and unavailable HEAD', async (action, nextAction, writes) => {
    const f = fixture()
    const applyTrim = await prepareNamedTrim()
    f.publish(appliedHead)
    fireEvent.click(applyTrim)
    await waitFor(() => expect(control('Undo').disabled).toBe(false))
    const undone = { ...head, revisionId: revisionId('revision-R2') }
    const redone = { ...appliedHead, revisionId: revisionId('revision-R3') }
    if (action === 'Redo') {
      f.publish(undone)
      f.write.mockResolvedValueOnce(undone)
      fireEvent.click(control('Undo'))
      await waitFor(() => expect(control('Redo').disabled).toBe(false))
    }
    const accepted = action === 'Undo' ? undone : redone
    f.write.mockResolvedValueOnce(accepted)
    f.failNextRead()
    fireEvent.click(control(action))
    await screen.findByText(new RegExp(action + ' accepted at revision ' + accepted.revisionId))
    expect(control(nextAction).disabled).toBe(true)
    expect(screen.queryByRole('button', { name: /Retry (undo|redo) trim/ })).toBeNull()
    await f.reload(accepted)
    expect(control(nextAction).disabled).toBe(false)
    expect(control(action).disabled).toBe(true)
    expect(f.trim).toHaveBeenCalledOnce()
    expect(f.restore).toHaveBeenCalledTimes(writes)
    expect(f.write).toHaveBeenCalledTimes(writes)
  })

  it.each([
    ['different revision at immediate readback', { ...appliedHead, revisionId: revisionId('revision-R2') }, false],
    ['same revision with a wrong hash after reload', { ...appliedHead, contentHash: head.contentHash }, true],
  ] as const)('does not reattach trim history after observing a %s', async (_label, other, unavailable) => {
    const f = fixture()
    const applyTrim = await prepareNamedTrim()
    f.publish(other)
    if (unavailable) f.failNextRead()
    fireEvent.click(applyTrim)
    await screen.findByText(/Trim (applied|accepted) at revision revision-R1/)
    if (unavailable) await f.reload(other)
    expect(control('Undo').disabled).toBe(true)
    // A later read of the receipt must not revive a history already contradicted by an authoritative HEAD.
    await f.reload(appliedHead)
    expect(control('Undo').disabled).toBe(true)
    expect(control('Redo').disabled).toBe(true)
    expect(f.trim).toHaveBeenCalledOnce()
    expect(f.write).not.toHaveBeenCalled()
  })
})

describe('branch editor command ownership', () => {
  afterEach(() => vi.restoreAllMocks())

  it('blocks direct deletion and other edits until the default-branch trim replays the exact wire command', async () => {
    const { queries, readRef, send, wire, ledger, initialRows } = defaultTrimWithLostResponse()
    fireEvent.click(await prepareNamedTrim())
    const retry = await screen.findByRole('button', { name: 'Retry Trim with the same command ID' })
    expect(ledger[0].id).toBe(appliedHead.revisionId)
    expect(ledger).toHaveLength(initialRows + 1)
    const deleteClip = screen.getByRole('button', { name: 'Delete selected clip' })
    expect(deleteClip.matches(':disabled')).toBe(true)
    fireEvent.click(deleteClip)
    fireEvent.keyDown(screen.getByRole('region', { name: 'Edit existing clip' }), { key: 'Delete' })
    for (const name of ['Add video track', 'Preview trim', 'Apply video transform', 'Apply video effect',
      'Remove video effect', 'Delete cross dissolve', 'Apply audio mix', 'Apply custom aspect']) {
      const control = screen.getByRole('button', { name })
      expect(control.matches(':disabled'), name).toBe(true)
      fireEvent.click(control)
    }
    const operation = screen.getByLabelText('Clip operation')
    const clip = screen.getByLabelText('Clip')
    expect(operation.matches(':disabled')).toBe(true)
    expect(clip.matches(':disabled')).toBe(true)
    fireEvent.change(operation, { target: { value: 'DELETE_CLIP' } })
    fireEvent.change(clip, { target: { value: '' } })
    const handle = screen.getByLabelText('Trim start edge clip-0')
    expect(handle.getAttribute('aria-disabled')).toBe('true')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 2 })
    fireEvent.pointerMove(handle, { clientX: 180, pointerId: 2 })
    fireEvent.pointerUp(handle, { clientX: 180, pointerId: 2 })
    expect(send).toHaveBeenCalledOnce()
    expect(branchPicker().disabled).toBe(true)
    expect((await olderButton()).disabled).toBe(true)
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Reload HEAD' }).disabled).toBe(true)
    expect(screen.getByRole('button', { name: 'Cancel trim' })).toBeTruthy()
    fireEvent.click(retry)
    await screen.findByText('Trim applied at revision revision-R1.')
    expect(wire).toHaveLength(2)
    expect(wire[1]).toEqual(wire[0])
    expect(wire[0].path).toBe('/tenants/tenant-1/projects/project-1/timeline-operations/batch/v2/apply')
    expect(wire[0].ifMatch).toBe(`"${HASH_C}"`)
    expect(JSON.parse(wire[0].body)).toEqual({
      batchId: expect.any(String), baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      operations: [{ definitionId: 'timeline.trim', target: { clipId: 'clip-0' }, parameters: { edge: 'START', delta: '1/1' } }],
    })
    expect(ledger).toHaveLength(initialRows + 1)
    await verifyDefaultNavigationResumed(queries, readRef)
    const restoredDelete = await screen.findByRole('button', { name: 'Delete selected clip' })
    await waitFor(() => expect(restoredDelete.matches(':disabled')).toBe(false))
    fireEvent.click(restoredDelete)
    await screen.findByText(/Clip clip-0 deleted at revision revision-R2/)
    expect(JSON.parse(wire[2].body)).toMatchObject({
      baseRevisionId: appliedHead.revisionId, operations: [{ definitionId: 'timeline.delete' }],
    })
    expect(ledger).toHaveLength(initialRows + 2)
  })

  it('explicitly discards the uncertain default-branch trim, reads back, and re-enables navigation and deletion', async () => {
    const { queries, readRef, wire, ledger, initialRows } = defaultTrimWithLostResponse()
    fireEvent.click(await prepareNamedTrim())
    await screen.findByRole('button', { name: 'Retry Trim with the same command ID' })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel trim' }))
    await screen.findByText(/Trim retry discarded/)
    expect(screen.queryByRole('button', { name: 'Retry Trim with the same command ID' })).toBeNull()
    expect(wire).toHaveLength(1)
    expect(ledger[0].id).toBe(appliedHead.revisionId)
    expect(ledger).toHaveLength(initialRows + 1)
    await verifyDefaultNavigationResumed(queries, readRef)
    const deleteClip = await screen.findByRole('button', { name: 'Delete selected clip' })
    await waitFor(() => expect(deleteClip.matches(':disabled')).toBe(false))
    fireEvent.click(deleteClip)
    await screen.findByText(/Clip clip-0 deleted at revision revision-R2/)
    expect(JSON.parse(wire[1].body).baseRevisionId).toBe(appliedHead.revisionId)
  })

  it('prevents an old trim confirmation from submitting after a historical read starts', async () => {
    const { queries } = renderBranchWorkspace('alt')
    const pendingDetail = deferred<GatewayResult<RevisionDetail>>()
    vi.spyOn(queries, 'getRevision').mockImplementationOnce(() => pendingDetail.promise)
    const plan = previewTrim()
    const write = vi.spyOn(FormalRenderAPI, 'applyConfirmedClip')
    const apply = await prepareNamedTrim()
    fireEvent.click(await olderButton())
    await screen.findByText('Loading explicit revision…')
    expect(apply.disabled).toBe(true)
    fireEvent.click(apply)
    expect(write).not.toHaveBeenCalled()
    await act(async () => pendingDetail.resolve({ ok: true, value: olderDetail }))
    await screen.findByText('Read-only historical revision')
    fireEvent.click(screen.getByRole('button', { name: 'Return to editing head' }))
    const newApply = await screen.findByRole<HTMLButtonElement>('button', { name: 'Apply trim' })
    expect(newApply.disabled).toBe(true)
    await prepareNamedTrim()
    expect(plan).toHaveBeenCalledTimes(2)
    expect(write).not.toHaveBeenCalled()
  })

  it('locks history at trim submission and retains the exact retry when a later head already exists', async () => {
    const { queries, readRef } = renderBranchWorkspace('alt')
    const getRevision = vi.spyOn(queries, 'getRevision')
    const plan = previewTrim()
    const inFlight = deferred<void>()
    const write = vi.spyOn(FormalRenderAPI, 'applyConfirmedClip')
      .mockImplementationOnce(async () => { await inFlight.promise; throw Error('response lost') })
      .mockResolvedValueOnce({ revisionId: 'revision-C', contentHash: HASH_B })
    fireEvent.click(await prepareNamedTrim())
    expect(write).toHaveBeenCalledOnce()
    const history = await olderButton()
    expect(history.disabled).toBe(true)
    fireEvent.click(history)
    expect(getRevision).not.toHaveBeenCalled()
    await act(async () => inFlight.resolve())
    const retry = await screen.findByRole('button', { name: /Retry Trim with the same command ID/ })
    expect(history.disabled).toBe(true)
    expect(branchPicker().disabled).toBe(true)
    readRef.mockResolvedValue({ ok: true, value: {
      head: { ...appliedHead, revisionId: revisionId('revision-D') },
      history: [revision, olderRevision],
    } })
    fireEvent.click(retry)
    await screen.findByText('Trim applied at revision revision-C. The branch is now at revision revision-D.')
    expect(write).toHaveBeenCalledTimes(2)
    expect(write.mock.calls[1]).toEqual(write.mock.calls[0])
    expect(write.mock.calls[1][2]).toBe(write.mock.calls[0][2])
    expect(write.mock.calls[0][2].refId).toBe('alt')
    expect(write.mock.calls[0][3]).toBe(HASH_C)
    expect(plan).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
    await waitFor(() => expect((screen.getByText('Older').closest('button')!).disabled).toBe(false))
  })

  it('treats a real 409 as a rejected trim and requires a new preview', async () => {
    const { readRef } = renderBranchWorkspace('alt')
    const plan = previewTrim()
    const write = vi.spyOn(FormalRenderAPI, 'applyConfirmedClip')
      .mockRejectedValueOnce(Object.assign(new Error('stale'), { response: { status: 409 } }))
    const button = await prepareNamedTrim()
    readRef.mockResolvedValue({ ok: true, value: { head: appliedHead, history: [appliedRevision, revision, olderRevision] } })
    fireEvent.click(button)
    await screen.findByText(/Trim rejected: the authoritative HEAD has changed/)
    expect(button.disabled).toBe(true)
    expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
    await prepareNamedTrim()
    expect(plan.mock.calls[1][2].baseRevisionId).toBe(appliedHead.revisionId)
    expect(write).toHaveBeenCalledOnce()
  })

  it('blocks navigation and a previously previewed restore until uncertain creation is replayed exactly', async () => {
    renderBranchWorkspace('main')
    previewRestore()
    const restore = vi.spyOn(FormalRenderAPI, 'commitBatch')
    const inFlight = deferred<void>()
    const create = vi.spyOn(FormalRenderAPI, 'createEditingBranch')
      .mockImplementationOnce(async () => { await inFlight.promise; throw Error('response lost') })
      .mockImplementationOnce(async (_project, body) => ({ ...body, replayed: true }))
    fireEvent.click(await olderButton())
    fireEvent.click(await screen.findByRole('button', { name: 'Preview restore' }))
    const confirm = await screen.findByRole<HTMLButtonElement>('button', { name: 'Confirm restore' })
    fireEvent.change(screen.getByLabelText('Branch name'), { target: { value: 'alternate-new' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create branch from viewed revision' }))
    expect(confirm.disabled).toBe(true)
    fireEvent.click(confirm)
    await act(async () => inFlight.resolve())
    const retry = await screen.findByRole('button', { name: 'Retry branch creation' })
    expect(branchPicker().disabled).toBe(true)
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Return to editing head' }).disabled).toBe(true)
    expect(restore).not.toHaveBeenCalled()
    expect(create.mock.calls[0][2]?.aborted).toBe(false)
    fireEvent.click(retry)
    const discard = await screen.findByRole('button', { name: 'Discard edits and switch' })
    fireEvent.click(discard)
    await screen.findByText('Viewing editing head · alternate-new')
    expect(create).toHaveBeenCalledTimes(2)
    expect(create.mock.calls[1].slice(0, 2)).toEqual(create.mock.calls[0].slice(0, 2))
    expect(create.mock.calls[1][1]).toBe(create.mock.calls[0][1])
  })

  it('blocks branch creation during a submitted restore and retains restore identity across an uncertain reply', async () => {
    const { queries } = renderBranchWorkspace('main')
    previewRestore()
    const create = vi.spyOn(FormalRenderAPI, 'createEditingBranch')
    const inFlight = deferred<void>()
    const restore = vi.spyOn(FormalRenderAPI, 'commitBatch')
      .mockImplementationOnce(async () => { await inFlight.promise; throw Error('response lost') })
      .mockResolvedValueOnce({ revisionId: appliedHead.revisionId, contentHash: HASH_B })
    fireEvent.click(await olderButton())
    fireEvent.change(await screen.findByLabelText('Branch name'), { target: { value: 'alternate-new' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview restore' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm restore' }))
    const fork = screen.getByRole<HTMLButtonElement>('button', { name: 'Create branch from viewed revision' })
    expect(fork.disabled).toBe(true)
    fireEvent.click(fork)
    await act(async () => inFlight.resolve())
    const retry = await screen.findByRole('button', { name: 'Retry confirmed restore' })
    expect(fork.disabled).toBe(true)
    expect(branchPicker().disabled).toBe(true)
    expect(create).not.toHaveBeenCalled()
    queries.headResult = { ok: true, value: appliedHead }
    queries.historyResult = { ok: true, value: [appliedRevision, revision, olderRevision] }
    fireEvent.click(retry)
    await screen.findByText(/Restore accepted as revision revision-R1/)
    expect(restore).toHaveBeenCalledTimes(2)
    expect(restore.mock.calls[1].slice(0, 5)).toEqual(restore.mock.calls[0].slice(0, 5))
    expect(restore.mock.calls[1][2]).toBe(restore.mock.calls[0][2])
  })
})

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

  it('shows canonical track groups and uses their selection for the editor', async () => {
    vi.spyOn(FormalRenderAPI, 'snapshot').mockResolvedValue({
      revisionId: head.revisionId,
      schemaVersion: 'timeline-1.0',
      canonicalTimelineJson: JSON.stringify({ schemaVersion: 'timeline-1.0', tracks: [
        { trackId: 'base', type: 'video', name: 'Camera', clips: [] },
        { trackId: 'top', type: 'video', name: 'Overlay', clips: [] },
        { trackId: 'audio', type: 'audio', name: 'Voice', clips: [] },
      ] }),
    })
    renderWorkspace(new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied }))
    const video = await screen.findByRole('group', { name: 'Video tracks' })
    const upper = await within(video).findByRole('button', { name: 'V2 Overlay video track' })
    const lower = within(video).getByRole('button', { name: 'V1 Camera video track' })
    expect(upper.compareDocumentPosition(lower) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    fireEvent.click(upper)
    expect(upper.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: 'A1 Voice audio track' })).toBeTruthy()
    expect(screen.getByLabelText('Timeline preview seek')).toBeTruthy()
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
      revisionId: revisionId('revision-P2'), contentHash: contentHash('D'.repeat(42) + 'M='),
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
      revisionId: revisionId('revision-P2'), contentHash: contentHash('D'.repeat(42) + 'M='),
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

  it('invalidates the previous restore immediately when another historical detail is pending', async () => {
    const other = { ...olderRevision, id: revisionId('revision-other'), message: 'Other historical revision' }
    const queries = new ScriptedTimelineQueryGateway({
      head: { ok: true, value: head }, history: { ok: true, value: [revision, olderRevision, other] },
      detail: { ok: true, value: olderDetail }, comparison: { ok: true, value: comparison },
    })
    const detailResponse = deferred<GatewayResult<RevisionDetail>>()
    vi.spyOn(queries, 'getRevision').mockResolvedValueOnce({ ok: true, value: olderDetail })
      .mockImplementationOnce(() => detailResponse.promise)
    vi.spyOn(FormalRenderAPI, 'previewRestore').mockImplementation(async (_tenant, _project, body) => ({
      batchId: body.batchId, baseRevisionId: body.baseRevisionId, baseContentHash: body.baseContentHash,
      finalCandidateHash: HASH_B, noOp: false, batchDigest: HASH_C, steps: [],
    }))
    const commit = vi.spyOn(FormalRenderAPI, 'commitBatch')
    renderWorkspace(new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied }), queries)
    fireEvent.click((await screen.findByText('Older')).closest('button')!)
    fireEvent.click(await screen.findByRole('button', { name: 'Preview restore' }))
    const oldConfirm = await screen.findByRole('button', { name: 'Confirm restore' })
    fireEvent.click(screen.getByText(other.message).closest('button')!)
    expect(screen.queryByRole('button', { name: 'Confirm restore' })).toBeNull()
    fireEvent.click(oldConfirm)
    expect(commit).not.toHaveBeenCalled()
    await act(async () => detailResponse.resolve({ ok: true, value: { ...olderDetail, revision: other } }))
    expect(await screen.findByRole('button', { name: 'Preview restore' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Confirm restore' })).toBeNull()
  })

  it('keeps a submitted historical restore owned through a lost response and exact retry', async () => {
    const queries = new ScriptedTimelineQueryGateway({
      head: { ok: true, value: head }, history: { ok: true, value: [revision, olderRevision] },
      detail: { ok: true, value: olderDetail }, comparison: { ok: true, value: comparison },
    })
    const previewRestore = vi.spyOn(FormalRenderAPI, 'previewRestore').mockImplementation(async (_tenant, _project, body) => ({
      batchId: body.batchId, baseRevisionId: body.baseRevisionId, baseContentHash: body.baseContentHash,
      finalCandidateHash: HASH_B, noOp: false, batchDigest: HASH_C, steps: [],
    }))
    const commit = vi.spyOn(FormalRenderAPI, 'commitBatch')
      .mockRejectedValueOnce(new Error('lost response'))
      .mockResolvedValueOnce({ revisionId: appliedHead.revisionId, contentHash: HASH_B })
    renderWorkspace(new ScriptedOperationGateway({ ok: true, value: preview }, { ok: true, value: applied }), queries)
    fireEvent.click((await screen.findByText('Older')).closest('button')!)
    fireEvent.click(await screen.findByRole('button', { name: 'Preview restore' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm restore' }))
    const retry = await screen.findByRole('button', { name: 'Retry confirmed restore' })
    expect((screen.getByText('Base').closest('button') as HTMLButtonElement).disabled).toBe(true)
    expect(commit.mock.calls[0][5]?.aborted).toBe(false)
    queries.headResult = { ok: true, value: appliedHead }
    queries.historyResult = { ok: true, value: [appliedRevision, revision, olderRevision] }
    fireEvent.click(retry)
    await screen.findByText(/Restore accepted as revision revision-R1/)
    expect(previewRestore).toHaveBeenCalledOnce()
    expect(commit).toHaveBeenCalledTimes(2)
    expect(commit.mock.calls[1].slice(0, 5)).toEqual(commit.mock.calls[0].slice(0, 5))
    expect(commit.mock.calls[1][2]).toBe(commit.mock.calls[0][2])
    expect(commit.mock.calls[1][4]).toBe(HASH_C)
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
    for (const value of ['revision-R0', HASH_A, 'video-1', 'clip-1', 'media-1', 'stream-1', 'artifact-1', SOURCE_DIGEST, '0/1', '10/1', 'FORWARD']) {
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

    queries.headResult = { ok: true, value: { ...head, revisionId: revisionId('revision-R2'), contentHash: contentHash(TIMELINE_C) } }
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
    expect((await screen.findByText('STALE_BASE_REVISION')).closest('[role=alert]')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Reload HEAD for re-preview' })).toBeTruthy()
    expect(screen.queryByText(/Server accepted revision/)).toBeNull()
  })
})

function ProjectContextProvider(props: ComponentProps<typeof ProjectProvider>) {
  return <WorkspaceSessionProvider workspaceId={props.workspaceId} projectId={props.projectId}><ProjectProvider {...props} /></WorkspaceSessionProvider>
}
