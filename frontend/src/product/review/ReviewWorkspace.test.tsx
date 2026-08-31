import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { platformClient } from '../../foundation/platformClient'
import { ProjectContextProvider } from '../../foundation/projectContext'
import type { GatewayResult, RevisionComparison, RevisionDetail, RevisionListEntry } from '../timeline/gateways'
import { ScriptedTimelineQueryGateway } from '../timeline/testing/mocks'
import { contentHash, projectId, revisionId, timelineId } from '../timeline/types'
import { ReviewWorkspace } from './ReviewWorkspace'

const head = {
  projectId: projectId('project-1'), timelineId: timelineId('project-1'),
  revisionId: revisionId('revision-R1'), contentHash: contentHash('a'.repeat(64)),
}
const summary = {
  supported: true, tracksAdded: 0, tracksRemoved: 0, tracksModified: 0,
  clipsAdded: 0, clipsRemoved: 0, clipsModified: 0, assetsAdded: 0, assetsRemoved: 0,
}
const revision0: RevisionListEntry = {
  id: revisionId('revision-R0'), revisionNumber: 1, parentRevisionId: null, source: 'OPERATION',
  message: 'Base', labels: [], authorUserId: null, createdAt: '2026-01-01T00:00:00Z', isMerge: false,
}
const revision1: RevisionListEntry = {
  ...revision0, id: revisionId('revision-R1'), revisionNumber: 2, parentRevisionId: revision0.id, message: 'Current',
}
const detail: RevisionDetail = { revision: revision1, changeSummary: summary, changeCount: 0 }

function comparison(fromRevision: RevisionListEntry, toRevision: RevisionListEntry, entityId: string): RevisionComparison {
  return { fromRevision, toRevision, summary, entityChanges: [{ kind: 'CLIP', entityId, action: 'MODIFIED' }] }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(accept => { resolve = accept })
  return { promise, resolve }
}

describe('Review comparison request ownership', () => {
  afterEach(() => vi.restoreAllMocks())

  it('keeps the newest ordered comparison when an older request resolves last', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    const queries = new ScriptedTimelineQueryGateway({
      head: { ok: true, value: head },
      history: { ok: true, value: [revision0, revision1] },
      detail: { ok: true, value: detail },
      comparison: { ok: true, value: comparison(revision0, revision1, 'unused') },
    })
    const older = deferred<GatewayResult<RevisionComparison>>()
    const newer = deferred<GatewayResult<RevisionComparison>>()
    vi.spyOn(queries, 'compare')
      .mockImplementationOnce(async () => older.promise)
      .mockImplementationOnce(async () => newer.promise)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><ReviewWorkspace queryGateway={queries} /></ProjectContextProvider></QueryClientProvider>)

    expect((await screen.findAllByRole('option', { name: /revision-R0/ })).length).toBe(2)
    fireEvent.change(screen.getByLabelText('From revision'), { target: { value: 'revision-R0' } })
    fireEvent.change(screen.getByLabelText('To revision'), { target: { value: 'revision-R1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Compare on server' }))
    fireEvent.change(screen.getByLabelText('From revision'), { target: { value: 'revision-R1' } })
    fireEvent.change(screen.getByLabelText('To revision'), { target: { value: 'revision-R0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Compare on server' }))

    await act(async () => newer.resolve({ ok: true, value: comparison(revision1, revision0, 'clip-newest') }))
    expect(await screen.findByText('clip-newest')).toBeTruthy()
    await act(async () => older.resolve({ ok: true, value: comparison(revision0, revision1, 'clip-stale') }))
    expect(screen.getByText('clip-newest')).toBeTruthy()
    expect(screen.queryByText('clip-stale')).toBeNull()
  })

  it('moves active review tabs and focus with the complete ARIA keyboard pattern', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    const queries = new ScriptedTimelineQueryGateway({
      head: { ok: true, value: head }, history: { ok: true, value: [revision0, revision1] },
      detail: { ok: true, value: detail }, comparison: { ok: true, value: comparison(revision0, revision1, 'clip-1') },
    })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><ProjectContextProvider workspaceId="workspace-1" projectId="project-1"><ReviewWorkspace queryGateway={queries} /></ProjectContextProvider></QueryClientProvider>)

    expect((await screen.findAllByRole('option', { name: /revision-R0/ })).length).toBe(2)
    const semantic = screen.getByRole('tab', { name: 'Semantic Changes' })
    semantic.focus()
    fireEvent.keyDown(semantic, { key: 'ArrowRight' })
    const conversation = screen.getByRole('tab', { name: 'Conversation' })
    expect(conversation.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(conversation)

    fireEvent.keyDown(conversation, { key: 'End' })
    const checks = screen.getByRole('tab', { name: 'Checks' })
    expect(checks.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(checks)

    fireEvent.keyDown(checks, { key: 'Home' })
    const overview = screen.getByRole('tab', { name: 'Overview' })
    expect(overview.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(overview)

    fireEvent.keyDown(overview, { key: 'ArrowLeft' })
    expect(screen.getByRole('tab', { name: 'Checks' }).getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(checks)
  })
})
