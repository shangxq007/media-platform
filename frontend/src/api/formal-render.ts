import { z } from 'zod'
import { versionlessApi } from './app/versionless-api'
import { isOidcEnabled } from '../auth/oidcConfig'
import { getOidcRequestBinding, subscribeOidcSessionRetirement } from '../auth/oidcClient'
import { expectOidcRequestBinding } from './oidc-transport'
import { RENDER_JOB_STATUSES } from '../contracts/app/render-job'

const id = z.string().min(1)
const part = (value: string) => encodeURIComponent(id.parse(value))
export interface RenderIntent { revisionId: string; extent: { startMillis: number; endMillis: number; frameRateNumerator: number; frameRateDenominator: number }; outputFormat: 'MP4'; profile: string }
export interface IncrementalIntent extends Omit<RenderIntent, 'revisionId'> { targetRevisionId: string; referenceRevisionId: string; referenceRenderJobId: string }
export interface ClipEdit { type: 'TRIM_CLIP' | 'MOVE_CLIP' | 'DELETE_CLIP'; baseRevisionId: string; baseContentHash: string; clipId: string; delta?: string; absolute?: boolean; edge?: 'START' | 'END'; commandId: string }
const Submission = z.object({ renderJobId: id, requestId: id, executionPlanId: id, status: z.literal('PLANNED'), workflowId: id, workflowStarted: z.boolean() })
const Result = z.object({ renderJobId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable(), failureCode: z.string().nullable() })
export type RenderResult = z.infer<typeof Result>
const Revision = z.object({ revisionId: id, productId: id, contentDigest: id })
const Head = z.object({ revisionId: id, revision: z.object({ revisionId: id, productId: id, semanticContext: z.object({ timelineContentDigest: id }) }) })
const Lifecycle = z.object({ renderJobId: id, timelineRevisionId: id, projectId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable() })

/** Reuse the existing auth/session binding; retirement aborts each in-flight product operation. */
async function request<T>(run: (config: { signal: AbortSignal; expectedOidcBinding?: ReturnType<typeof expectOidcRequestBinding> }) => Promise<{ data: T }>, signal?: AbortSignal): Promise<T> {
  const controller = new AbortController()
  const active = signal ? AbortSignal.any([signal, controller.signal]) : controller.signal
  const stop = isOidcEnabled() ? subscribeOidcSessionRetirement(() => controller.abort()) : () => {}
  try {
    const binding = isOidcEnabled() ? await getOidcRequestBinding() : undefined
    active.throwIfAborted()
    const { data } = await run({ signal: active, expectedOidcBinding: binding ? expectOidcRequestBinding(binding) : undefined })
    active.throwIfAborted()
    return data
  } finally { stop() }
}
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
    return { projectId: project, revisionId: revision, contentHash: result.contentDigest }
  },
  async snapshot(project: string, revision: string, signal?: AbortSignal) {
    const schema = z.object({ canonicalTimelineJson: z.string(), schemaVersion: z.string() })
    return schema.parse(await request(c => versionlessApi.get(`/render/projects/${part(project)}/timeline/revisions/${part(revision)}/snapshot`, c), signal))
  },
  async applyOperation(tenant: string, project: string, command: ClipEdit, signal?: AbortSignal) {
    const value = z.object({ newRevisionId: id.optional(), baseRevisionId: id, resultContentHash: id.optional(), unchangedContentHash: id.optional() })
      .parse(await request(c => versionlessApi.post(`/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/clips/apply`, command, c), signal))
    return { revisionId: value.newRevisionId ?? value.baseRevisionId, contentHash: value.resultContentHash ?? value.unchangedContentHash ?? '' }
  },
  async submit(intent: RenderIntent, key: string, signal?: AbortSignal) {
    return Submission.parse(await request(c => versionlessApi.post('/first-render/plans', intent, { ...c, headers: { 'Idempotency-Key': key } }), signal))
  },
  async incremental(intent: IncrementalIntent, key: string, signal?: AbortSignal) {
    return Submission.parse(await request(c => versionlessApi.post('/incremental-render/plans', intent, { ...c, headers: { 'Idempotency-Key': key } }), signal))
  },
  async result(tenant: string, project: string, job: string, signal?: AbortSignal) {
    const value = Result.parse(await request(c => versionlessApi.get(jobPath(tenant, project, job) + '/result', c), signal))
    if (value.renderJobId !== job) throw new Error('JOB_SCOPE_MISMATCH')
    return value
  },
  async lifecycle(project: string, job: string, signal?: AbortSignal) {
    const value = Lifecycle.parse(await request(c => versionlessApi.get(`/first-render/jobs/${part(job)}`, c), signal))
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
