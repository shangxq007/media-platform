import type { SemanticBatch, SemanticStep } from '../contracts/semantic-batch.generated'
import { decodeCompositionAspect, type CompositionAspectRatio } from '../contracts/app/composition-aspect'
import { z } from 'zod'
import { versionlessApi } from './app/versionless-api'
import { sessionBoundRequest as request } from './oidc-transport'
import { RENDER_JOB_STATUSES } from '../contracts/app/render-job'

const id = z.string().min(1)
const part = (value: string) => encodeURIComponent(id.parse(value))
export interface RenderIntent { revisionId: string; extent: { startMillis: number; endMillis: number; frameRateNumerator: number; frameRateDenominator: number; width?: number; height?: number }; outputFormat: 'MP4'; profile: string }
export interface IncrementalIntent extends Omit<RenderIntent, 'revisionId'> { targetRevisionId: string; referenceRevisionId: string; referenceRenderJobId: string }
export interface ClipEdit { refId?: string; type: 'TRIM_CLIP' | 'MOVE_CLIP' | 'DELETE_CLIP' | 'SPLIT_CLIP'; baseRevisionId: string; baseContentHash: string; clipId: string; delta?: string; absolute?: boolean; edge?: 'START' | 'END'; splitTime?: string; rightClipId?: string; destinationTrackId?: string; commandId: string }
export interface VideoTransformCommand { baseRevisionId: string; baseContentHash: string; clipId: string;
  parameters: Extract<SemanticStep, { definitionId: 'timeline.clip.transform.set' }>['parameters']; commandId: string }
export interface VideoEffectCommand { baseRevisionId: string; baseContentHash: string; clipId: string;
  brightness: number; contrast: number; saturation: number; grayscale: boolean;
  fadeInSeconds: number; fadeOutSeconds: number; enabled: boolean; commandId: string }
export interface TransitionCommand { baseRevisionId: string; baseContentHash: string; outgoingClipId: string;
  transitionId: string; incomingClipId?: string; duration?: string; commandId: string }
export interface AudioMixCommand { baseRevisionId: string; baseContentHash: string; trackId: string; clipId: string; gain: number; mute: boolean; pan: number; commandId: string }
const Submission = z.object({ renderJobId: id, requestId: id, executionPlanId: id, status: z.literal('PLANNED'), workflowId: id, workflowStarted: z.boolean() })
const Result = z.object({ renderJobId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable(), failureCode: z.string().nullable() })
export type RenderResult = z.infer<typeof Result>
const Revision = z.object({ revisionId: id, productId: id, contentDigest: id, timelineContentHash: id })
const Head = z.object({ revisionId: id, revision: z.object({ revisionId: id, productId: id, semanticContext: z.object({ timelineContentDigest: id }) }) })
const Lifecycle = z.object({ renderJobId: id, timelineRevisionId: id, projectId: id, status: z.enum(RENDER_JOB_STATUSES), finalArtifactId: id.nullable() })

const jobPath = (tenant: string, project: string, job: string) => `/tenants/${part(tenant)}/projects/${part(project)}/render-jobs/${part(job)}`
const RasterProfile = z.object({
  minWidth: z.number().int().positive(), minHeight: z.number().int().positive(),
  maxWidth: z.number().int().positive(), maxHeight: z.number().int().positive(),
  maxPixels: z.number().int().positive(), alignment: z.number().int().positive(),
})
export type RasterProfile = z.infer<typeof RasterProfile>

function wireValue<T>(value: T | undefined): T {
  if (value === undefined) throw new Error('Incomplete clip operation')
  return value
}

function clipBatch(command: ClipEdit): SemanticBatch {
  const target = { clipId: command.clipId }
  let step: SemanticStep
  switch (command.type) {
    case 'TRIM_CLIP': step = { definitionId: 'timeline.trim', target, parameters: { edge: wireValue(command.edge), delta: wireValue(command.delta) } }; break
    case 'SPLIT_CLIP': step = { definitionId: 'timeline.clip.split', target,
      parameters: { rightClipId: wireValue(command.rightClipId), splitTimelineTime: wireValue(command.splitTime) } }; break
    case 'MOVE_CLIP': {
      const parameters = { delta: wireValue(command.delta), absolute: command.absolute ?? false }
      step = command.destinationTrackId
        ? { definitionId: 'timeline.clip.relocate', target, parameters: { ...parameters, destinationTrackId: command.destinationTrackId } }
        : { definitionId: 'timeline.move', target, parameters }
      break
    }
    case 'DELETE_CLIP': step = { definitionId: 'timeline.delete', target }; break
  }
  return { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
    baseContentHash: command.baseContentHash, refId: command.refId, operations: [step] }
}

const batchEndpoint = (tenant: string, project: string) =>
  `/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/batch/v2`
const planSchema = z.object({ batchDigest: z.string().regex(/^[0-9a-f]{64}$/),
  steps: z.array(z.object({ changes: z.array(z.object({ identity: z.string(), primary: z.boolean() })) })) })

export const FormalRenderAPI = {
  async createEditingBranch(project: string, body: { refId: string; sourceRevisionId: string; commandId: string }, signal?: AbortSignal) {
    const schema = z.object({ refId: id, sourceRevisionId: id, replayed: z.boolean() })
    const accepted = schema.parse(await request(c => versionlessApi.post(
      `/render/projects/${part(project)}/timeline/branches`, body, c), signal))
    if (accepted.refId !== body.refId || accepted.sourceRevisionId !== body.sourceRevisionId) {
      throw new Error('BRANCH_CREATE_SCOPE_MISMATCH')
    }
    return accepted
  },
  async outputRasters(project: string, signal?: AbortSignal) {
    const path = `/render/projects/${part(project)}/output-rasters`
    return RasterProfile.array().parse(await request(c => versionlessApi.get(path, c), signal))
  },
  async initializeTimeline(tenant: string, project: string, commandId: string) {
    return request(c => versionlessApi.post(
      `/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/initialize`,
      { trackId: 'track-1', commandId }, c))
  },
  async addTrack(tenant: string, project: string, command: {
    baseRevisionId: string; baseContentHash: string; trackType: 'VIDEO' | 'AUDIO'; trackId: string; commandId: string
  }) {
    return request(c => versionlessApi.post(
      `/tenants/${part(tenant)}/projects/${part(project)}/timeline-operations/tracks`, command, c))
  },
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
    const schema = z.object({ revisionId: id, canonicalTimelineJson: z.string(), schemaVersion: z.string() })
    const snapshot = schema.parse(await request(c => versionlessApi.get(`/render/projects/${part(project)}/timeline/revisions/${part(revision)}/snapshot`, c), signal))
    if (snapshot.revisionId !== revision) throw new Error('REVISION_SCOPE_MISMATCH')
    return snapshot
  },
  async applyOperation(tenant: string, project: string, command: ClipEdit, signal?: AbortSignal) {
    return this.applyBatch(tenant, project, clipBatch(command), command.baseRevisionId, signal)
  },
  async applyConfirmedClip(tenant: string, project: string, command: ClipEdit, digest: string, signal?: AbortSignal) {
    return this.commitBatch(tenant, project, clipBatch(command), command.baseRevisionId, digest, signal)
  },
  async applyVideoTransform(tenant: string, project: string, command: VideoTransformCommand, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Video transform',
      operations: [{ definitionId: 'timeline.clip.transform.set',
        target: { clipId: command.clipId }, parameters: command.parameters }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },
  async sourceDisplayAspect(tenant: string, project: string, artifactId: string, contentDigest: string,
    streamId: string, signal?: AbortSignal): Promise<CompositionAspectRatio> {
    const path = `/tenants/${part(tenant)}/projects/${part(project)}/source-media/${part(artifactId)}/display-aspect`
    return decodeCompositionAspect(await request(config => versionlessApi.get(path, {
      ...config, params: { contentDigest, streamId },
    }), signal))
  },

  async setCompositionAspect(tenant: string, project: string, command: {
    baseRevisionId: string; baseContentHash: string; commandId: string;
    aspectRatio: CompositionAspectRatio
    input?: { artifactId: string; contentDigest: string; streamId: string }
  }, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Set composition aspect ratio',
      operations: [{ definitionId: 'timeline.composition.aspect.set', ...(command.input && { input: command.input }),
        parameters: { aspectRatio: command.aspectRatio } }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },

  async applyVideoEffect(tenant: string, project: string, command: VideoEffectCommand, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Video effect',
      operations: [{ definitionId: 'timeline.clip.effect.set',
        target: { clipId: command.clipId }, parameters: {
          brightness: command.brightness, contrast: command.contrast, saturation: command.saturation,
          grayscale: command.grayscale, fadeInSeconds: command.fadeInSeconds,
          fadeOutSeconds: command.fadeOutSeconds, enabled: command.enabled } }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },

  async removeVideoEffect(tenant: string, project: string, command: Omit<VideoEffectCommand,
      'brightness' | 'contrast' | 'saturation' | 'grayscale' | 'fadeInSeconds' | 'fadeOutSeconds' | 'enabled'>, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Remove video effect',
      operations: [{ definitionId: 'timeline.clip.effect.remove', target: { clipId: command.clipId } }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },

  async createTransition(tenant: string, project: string, command: Required<TransitionCommand>, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Cross dissolve',
      operations: [{ definitionId: 'timeline.transition.cross-dissolve.create',
        target: { clipId: command.outgoingClipId }, parameters: {
          transitionId: command.transitionId, incomingClipId: command.incomingClipId,
          duration: command.duration } }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },

  async deleteTransition(tenant: string, project: string, command: TransitionCommand, signal?: AbortSignal) {
    const body: SemanticBatch = { batchId: command.commandId, baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash, message: 'Delete cross dissolve',
      operations: [{ definitionId: 'timeline.transition.cross-dissolve.delete',
        target: { transitionId: command.transitionId } }] }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },

  async applyBatch(tenant: string, project: string, body: SemanticBatch, fallbackRevision: string, signal?: AbortSignal) {
    const preview = await this.previewBatch(tenant, project, body, signal)
    return this.commitBatch(tenant, project, body, fallbackRevision, preview.batchDigest, signal)
  },
  async previewBatch(tenant: string, project: string, body: SemanticBatch, signal?: AbortSignal) {
    return planSchema.parse(await request(c => versionlessApi.post(`${batchEndpoint(tenant, project)}/preview`, body, c), signal))
  },
  async previewRestore(tenant: string, project: string, body: SemanticBatch, signal?: AbortSignal) {
    const schema = planSchema.extend({ batchId: id, baseRevisionId: id, baseContentHash: id,
      finalCandidateHash: id, noOp: z.boolean() })
    const result = schema.parse(await request(c => versionlessApi.post(`${batchEndpoint(tenant, project)}/preview`, body, c), signal))
    if (result.batchId !== body.batchId || result.baseRevisionId !== body.baseRevisionId
        || result.baseContentHash !== body.baseContentHash) throw new Error('RESTORE_PREVIEW_SCOPE_MISMATCH')
    return result
  },
  async commitBatch(tenant: string, project: string, body: SemanticBatch, fallbackRevision: string, digest: string, signal?: AbortSignal) {
    const endpoint = batchEndpoint(tenant, project)
    const value = z.object({ status: z.enum(['APPLIED', 'NO_OP']), newRevisionId: id.nullable().optional(), newContentHash: id })
      .parse(await request(c => versionlessApi.post(
        `${endpoint}/apply`, body, { ...c, headers: { 'If-Match': `"${digest}"` } }), signal))
    return { revisionId: value.newRevisionId ?? fallbackRevision, contentHash: value.newContentHash }
  },

  async applyAudioMix(tenant: string, project: string, command: AudioMixCommand, signal?: AbortSignal) {
    const body: SemanticBatch = {
      batchId: command.commandId,
      baseRevisionId: command.baseRevisionId,
      baseContentHash: command.baseContentHash,
      message: 'Audio gain, mute and pan',
      operations: [
        { definitionId: 'audio.gain.set', target: { clipId: command.clipId, audioTrackId: command.trackId }, parameters: { linear: command.gain } },
        { definitionId: 'audio.mute.set', target: { clipId: command.clipId, audioTrackId: command.trackId }, parameters: { muted: command.mute } },
        { definitionId: 'audio.balance.set', target: { clipId: command.clipId, audioTrackId: command.trackId }, parameters: { value: command.pan } },
      ],
    }
    return this.applyBatch(tenant, project, body, command.baseRevisionId, signal)
  },
  async previewOperation(tenant: string, project: string, command: ClipEdit, signal?: AbortSignal) {
    return this.previewBatch(tenant, project, clipBatch(command), signal)
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
