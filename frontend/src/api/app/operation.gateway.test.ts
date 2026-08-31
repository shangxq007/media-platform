import { describe, expect, it } from 'vitest'
import { createOperationGateway } from './operation.gateway'
import type { VersionlessTransport } from './versionless-api'
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
} from '../../product/timeline/types'
import type { AddMediaClipCommandDraft, OperationPreview } from '../../product/timeline/gateways'

const HASH_A = 'a'.repeat(64)
const HASH_B = 'b'.repeat(64)
const HASH_C = 'c'.repeat(64)

const draft: AddMediaClipCommandDraft = {
  baseRevisionId: revisionId('revision-R0'), baseContentHash: contentHash(HASH_A), trackId: trackId('video-1'),
  clipId: clipId('clip-1'), mediaAssetId: mediaAssetId('media-1'), mediaStreamId: mediaStreamId('stream-1'),
  artifactId: artifactId('artifact-1'), contentDigest: contentHash(HASH_B), sourceStart: exactMediaTime('0/1'),
  sourceEnd: exactMediaTime('10/1'), timelineStart: exactMediaTime('0/1'), timelineEnd: exactMediaTime('10/1'),
  rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD',
}

const previewResponse = {
  operation: ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  planDigest: HASH_C,
  targetTimelineId: 'project-1',
  baseRevisionId: 'revision-R0',
  baseContentHash: HASH_A,
  expectedChangedCanonicalObjects: ['clip:clip-1'],
  validation: [],
  capabilityRequirements: [],
  warnings: [],
  failures: [],
  candidateContentHash: HASH_B,
}

const confirmedPreview: OperationPreview = {
  operation: ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  planDigest: planDigest(HASH_C),
  targetTimelineId: timelineId('project-1'),
  baseRevisionId: draft.baseRevisionId,
  baseContentHash: draft.baseContentHash,
  expectedChanges: ['clip:clip-1'],
  validation: [],
  capabilityRequirements: [],
  warnings: [],
  failures: [],
  candidateContentHash: contentHash(HASH_B),
}

const appliedResponse = {
  status: 'APPLIED',
  planDigest: HASH_C,
  baseRevisionId: 'revision-R0',
  newRevisionId: 'revision-R1',
  newTimelineContentHash: HASH_B,
  parentRevisionId: 'revision-R0',
  semanticDiff: ['clip:clip-1'],
  renderHandoff: { projectId: 'project-1', timelineRevisionId: 'revision-R1', timelineContentHash: HASH_B },
}

const noOpResponse = {
  status: 'NO_OP',
  planDigest: HASH_C,
  baseRevisionId: 'revision-R0',
  newRevisionId: null,
  newTimelineContentHash: HASH_A,
  parentRevisionId: null,
  semanticDiff: [],
  renderHandoff: { projectId: 'project-1', timelineRevisionId: null, timelineContentHash: HASH_A },
}

function postTransport(response: unknown, capture?: (path: string, body: unknown) => void): VersionlessTransport {
  return {
    get: async () => ({ data: {} }),
    post: async (path, body) => {
      capture?.(path, body)
      return { data: response }
    },
  }
}

describe('Add Media Clip transport mapping', () => {
  it('binds the preview discriminator, requested Project, exact base, and actor-free request', async () => {
    let capturedPath = ''
    let capturedBody: unknown
    const result = await createOperationGateway(postTransport(previewResponse, (path, body) => {
      capturedPath = path
      capturedBody = body
    })).previewAddMediaClip('tenant-1', projectId('project-1'), draft)
    expect(result.ok).toBe(true)
    expect(capturedPath).toContain('/tenants/tenant-1/projects/project-1/')
    expect(capturedBody).toMatchObject({ baseRevisionId: 'revision-R0', baseContentHash: HASH_A, sourceStart: '0/1' })
    expect(capturedBody).not.toHaveProperty('tenantId')
    expect(capturedBody).not.toHaveProperty('actorId')
    expect(JSON.stringify(capturedBody)).not.toContain('1.5')
  })

  it.each([
    ['wrong operation', { operation: 'ADD_MEDIA_CLIP_V2' }],
    ['wrong target', { targetTimelineId: 'project-2' }],
    ['wrong base revision', { baseRevisionId: 'revision-R9' }],
    ['wrong base hash', { baseContentHash: HASH_B }],
  ])('rejects a preview with %s', async (_label, mutation) => {
    const result = await createOperationGateway(postTransport({ ...previewResponse, ...mutation }))
      .previewAddMediaClip('tenant-1', projectId('project-1'), draft)
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.code).toBe('VALIDATION')
  })

  it.each([
    ['APPLIED', appliedResponse],
    ['NO_OP', noOpResponse],
  ] as const)('accepts fully bound %s and sends the confirmed preview digest', async (status, response) => {
    let capturedBody: unknown
    const result = await createOperationGateway(postTransport(response, (_path, body) => { capturedBody = body }))
      .applyAddMediaClip('tenant-1', projectId('project-1'), draft, confirmedPreview, applyCommandId('apply-1'))
    expect(result.ok && result.value.status).toBe(status)
    expect(capturedBody).toMatchObject({
      request: { baseRevisionId: 'revision-R0', baseContentHash: HASH_A },
      expectedPlanDigest: HASH_C,
      applyCommandId: 'apply-1',
    })
  })

  it.each([
    ['digest', { planDigest: 'd'.repeat(64) }],
    ['base', { baseRevisionId: 'revision-R9' }],
    ['null new revision', { newRevisionId: null }],
    ['parent', { parentRevisionId: 'revision-R9' }],
    ['candidate hash', { newTimelineContentHash: HASH_A }],
    ['handoff project', { renderHandoff: { ...appliedResponse.renderHandoff, projectId: 'project-2' } }],
    ['handoff revision', { renderHandoff: { ...appliedResponse.renderHandoff, timelineRevisionId: 'revision-R9' } }],
    ['handoff hash', { renderHandoff: { ...appliedResponse.renderHandoff, timelineContentHash: HASH_A } }],
  ])('rejects APPLIED response mismatch: %s', async (_label, mutation) => {
    const result = await createOperationGateway(postTransport({ ...appliedResponse, ...mutation }))
      .applyAddMediaClip('tenant-1', projectId('project-1'), draft, confirmedPreview, applyCommandId('apply-1'))
    expect(result.ok).toBe(false)
  })

  it.each([
    ['new revision', { newRevisionId: 'revision-R1' }],
    ['parent revision', { parentRevisionId: 'revision-R0' }],
    ['base hash', { newTimelineContentHash: HASH_B }],
    ['handoff project', { renderHandoff: { ...noOpResponse.renderHandoff, projectId: 'project-2' } }],
    ['handoff revision', { renderHandoff: { ...noOpResponse.renderHandoff, timelineRevisionId: 'revision-R0' } }],
    ['handoff hash', { renderHandoff: { ...noOpResponse.renderHandoff, timelineContentHash: HASH_B } }],
  ])('rejects NO_OP response mismatch: %s', async (_label, mutation) => {
    const result = await createOperationGateway(postTransport({ ...noOpResponse, ...mutation }))
      .applyAddMediaClip('tenant-1', projectId('project-1'), draft, confirmedPreview, applyCommandId('apply-1'))
    expect(result.ok).toBe(false)
  })

  it('rejects an unbound confirmed preview before issuing apply', async () => {
    let calls = 0
    const transport = postTransport(appliedResponse, () => { calls += 1 })
    const result = await createOperationGateway(transport).applyAddMediaClip(
      'tenant-1',
      projectId('project-1'),
      draft,
      { ...confirmedPreview, targetTimelineId: timelineId('project-2') },
      applyCommandId('apply-1'),
    )
    expect(result.ok).toBe(false)
    expect(calls).toBe(0)
  })
})
