import { describe, expect, it } from 'vitest'
import { createTimelineQueryGateway } from './timeline-query.gateway'
import type { VersionlessTransport } from './versionless-api'
import { projectId, revisionId } from '../../product/timeline/types'

const revisionResponse = (id: string) => ({
  id,
  revisionNumber: 1,
  parentRevisionId: null,
  source: 'OPERATION',
  message: null,
  labels: [],
  authorUserId: null,
  createdAt: '2026-01-01T00:00:00Z',
  isMerge: false,
})

const summary = {
  supported: true,
  tracksAdded: 0,
  tracksRemoved: 0,
  tracksModified: 0,
  clipsAdded: 0,
  clipsRemoved: 0,
  clipsModified: 0,
  assetsAdded: 0,
  assetsRemoved: 0,
}

describe('canonical Timeline query mapping', () => {
  it('uses the nested Timeline semantic digest as the editing-base hash instead of the revision digest', async () => {
    const calls: string[] = []
    const transport: VersionlessTransport = {
      get: async path => {
        calls.push(path)
        return { data: { revisionId: 'revision-R1', revision: {
          revisionId: 'revision-R1', productId: 'project-1', contentDigest: 'a'.repeat(64),
          semanticContext: { timelineContentDigest: 'b'.repeat(64) },
        } } }
      },
      post: async () => ({ data: {} }),
    }
    const result = await createTimelineQueryGateway(transport).getHead(projectId('project-1'))
    expect(result).toEqual({ ok: true, value: {
      projectId: 'project-1', timelineId: 'project-1', revisionId: 'revision-R1', contentHash: 'b'.repeat(64),
    } })
    expect(calls).toEqual(['/timeline-git/products/project-1/revisions/current'])
  })

  it('fails closed when the Timeline semantic digest is absent', async () => {
    const transport: VersionlessTransport = {
      get: async () => ({ data: { revisionId: 'revision-R1', revision: {
        revisionId: 'revision-R1', productId: 'project-1', contentDigest: 'a'.repeat(64),
      } } }),
      post: async () => ({ data: {} }),
    }
    const result = await createTimelineQueryGateway(transport).getHead(projectId('project-1'))
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.code).toBe('VALIDATION')
  })

  it('fails closed when response identities disagree', async () => {
    const transport: VersionlessTransport = {
      get: async () => ({ data: { revisionId: 'revision-R1', revision: {
        revisionId: 'revision-R2', productId: 'project-1',
        semanticContext: { timelineContentDigest: 'a'.repeat(64) },
      } } }),
      post: async () => ({ data: {} }),
    }
    const result = await createTimelineQueryGateway(transport).getHead(projectId('project-1'))
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.code).toBe('VALIDATION')
  })

  it('rejects detail for a revision other than the exact requested ID', async () => {
    const transport: VersionlessTransport = {
      get: async () => ({ data: { revision: revisionResponse('revision-R2'), changeSummary: summary, patchOpCount: 0 } }),
      post: async () => ({ data: {} }),
    }
    const result = await createTimelineQueryGateway(transport).getRevision(projectId('project-1'), revisionId('revision-R1'))
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.code).toBe('VALIDATION')
  })

  it.each([
    ['swapped', 'revision-R2', 'revision-R1'],
    ['mismatched from', 'revision-R9', 'revision-R2'],
    ['mismatched to', 'revision-R1', 'revision-R9'],
  ])('rejects %s comparison identities', async (_label, responseFrom, responseTo) => {
    const transport: VersionlessTransport = {
      get: async () => ({ data: {
        fromRevision: revisionResponse(responseFrom),
        toRevision: revisionResponse(responseTo),
        summary,
        entityChanges: [],
      } }),
      post: async () => ({ data: {} }),
    }
    const result = await createTimelineQueryGateway(transport).compare(
      projectId('project-1'),
      revisionId('revision-R1'),
      revisionId('revision-R2'),
    )
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.code).toBe('VALIDATION')
  })
})
