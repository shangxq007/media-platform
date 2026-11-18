import { z } from 'zod'
import { versionlessApi } from './app/versionless-api'
import { sessionBoundRequest as request } from './oidc-transport'
import { RENDER_JOB_STATUSES } from '../contracts/app/render-job'

const id = z.string().min(1)
const part = (value: string) => encodeURIComponent(id.parse(value))
export interface RenderIntent { revisionId: string; extent: { startMillis: number; endMillis: number; frameRateNumerator: number; frameRateDenominator: number }; outputFormat: 'MP4'; profile: string }
export interface IncrementalIntent extends Omit<RenderIntent, 'revisionId'> { targetRevisionId: string; referenceRevisionId: string; referenceRenderJobId: string }
export interface ClipEdit { type: 'TRIM_CLIP' | 'MOVE_CLIP' | 'DELETE_CLIP' | 'SPLIT_CLIP'; baseRevisionId: string; baseContentHash: string; clipId: string; delta?: string; absolute?: boolean; edge?: 'START' | 'END'; splitTime?: string; rightClipId?: string; commandId: string }
const Submission = z.object({ renderJobId: id, requestId: id, executionPlanId: id, status: z.literal('PLANNED'), workflowId: id, workflowStarted: z.boolean() })
const Result = z.object({ renderJobId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable(), failureCode: z.string().nullable() })
export type RenderResult = z.infer<typeof Result>
const Revision = z.object({ revisionId: id, productId: id, contentDigest: id, timelineContentHash: id })
const Head = z.object({ revisionId: id, revision: z.object({ revisionId: id, productId: id, semanticContext: z.object({ timelineContentDigest: id }) }) })
const Lifecycle = z.object({ renderJobId: id, timelineRevisionId: id, projectId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable() })

const jobPath = (tenant: string, project: string, job: string) => `/tenants/${part(tenant)}/projects/${part(project)}/render-jobs/${part(job)}`
export const FormalRenderAPI = {
  async head(project: string, signal?: AbortSignal) {
    const result = Head.parse(await request(c => versionlessApi.get(`/timeline-git/products/${part(project)}/revisions/current`, c), signal))
    if (result.revision.productId !== project || result.revisionId !== result.revision.revisionId) throw new Error('REVISION_SCOPE_MISMATCH')
    return { projectId: project, revisionId: result.revisionId, contentHash: result.revision.semanticContext.timelineContentDigest }
  },
  async revision(project: string, revision: string, signal?: AbortSignal) {
    const result = Revision.parse(await request(c => versionlessApi.get(`/timeline-git/products/${part(project)}/revisions/${part(revision)}`, c), signal))
    if (result.productId !== project || result.revisionId !== revision) throw new Error('REVISION_SCOPE_MISMATCH')
    return { projectId: project, revisionId: revision, contentHash: result.timelineContentHash }
  },
  async snapshot(project: string, revision: string, signal?: AbortSignal) {
    const schema = z.object({ canonicalTimelineJson: z.string(), schemaVersion: z.string() })
    return schema.parse(await request(c => versionlessApi.get(`/render/projects/${part(project)}/timeline/revisions/${part(revision)}/snapshot`, c), signal))
  },
  async applyOperation(tenant: string, project: string, command: ClipEdit, signal?: AbortSignal) {
    const value = z.object({ newRevisionId: id.optional(), baseRevisionId: id, resultContentHash: id.optional(), unchangedContentHash: id.optional() })
      .refine(v => (v.newRevisionId !== undefined && v.resultContentHash !== undefined && v.unchangedContentHash === undefined) || (v.newRevisionId === undefined && v.resultContentHash === undefined && v.unchangedContentHash !== undefined), 'Invalid Operation outcome')
      .parse(await request(c => versionlessApi.post(`/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/clips/apply`, command, c), signal))
    return { revisionId: value.newRevisionId ?? value.baseRevisionId, contentHash: value.resultContentHash ?? value.unchangedContentHash ?? '' }
  },
  async previewOperation(tenant: string, project: string, command: ClipEdit, signal?: AbortSignal) {
    return z.object({ status: z.literal('VALID'), definitionId: z.string(), clipId: z.string(),
      type: z.string(), edge: z.string().nullable(), delta: z.string().nullable(),
      splitTime: z.string().nullable().optional(),
      rightClipId: z.string().nullable().optional() })
      .parse(await request(c => versionlessApi.post(
        `/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/clips/preview`, command, c), signal))
  },
  async submit(intent: RenderIntent, key: string, signal?: AbortSignal) {
    return Submission.parse(await request(c => versionlessApi.post('/render-jobs', intent, { ...c, headers: { 'Idempotency-Key': key } }), signal))
  },
  async incremental(intent: IncrementalIntent, key: string, signal?: AbortSignal) {
    return Submission.parse(await request(c => versionlessApi.post('/incremental-render/plans', intent, { ...c, headers: { 'Idempotency-Key': key } }), signal))
  },
  async result(tenant: string, project: string, job: string, signal?: AbortSignal) {
    const value = Result.parse(await request(c => versionlessApi.get(jobPath(tenant, project, job) + '/result', c), signal))
    if (value.renderJobId !== job) throw new Error('JOB_SCOPE_MISMATCH')
    return value
  },
  async lifecycle(tenant: string, project: string, job: string, signal?: AbortSignal) {
    const value = Lifecycle.parse(await request(c => versionlessApi.get(jobPath(tenant, project, job) + '/lifecycle', c), signal))
    if (value.projectId !== project || value.renderJobId !== job) throw new Error('JOB_SCOPE_MISMATCH')
    return value
  },
  async cancel(tenant: string, project: string, job: string, signal?: AbortSignal) {
    return request(c => versionlessApi.post(jobPath(tenant, project, job) + '/cancel', {}, c), signal)
  },
  async download(tenant: string, project: string, job: string, artifact: string, signal?: AbortSignal): Promise<Blob> {
    return request(c => versionlessApi.get<Blob>(jobPath(tenant, project, job) + `/artifacts/${part(artifact)}/content`, { ...c, responseType: 'blob' }), signal)
  },
}
