import { z } from 'zod'
import type { AcceptedOperationResult, AddMediaClipCommandDraft, OperationGateway, OperationPreview } from '../../product/timeline/gateways'
import {
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  contentHash,
  planDigest,
  revisionId,
  timelineId,
  type ProjectId,
} from '../../product/timeline/types'
import { gatewayFailure, responseValidationFailure } from './gateway-error'
import { versionlessTransport, type VersionlessTransport } from './versionless-api'

const IdSchema = z.string().min(1).max(64).regex(/^[A-Za-z0-9._:-]+$/)
const HashSchema = z.string().regex(/^[0-9a-fA-F]{64}$/)

const PreviewSchema = z.object({
  operation: z.literal(ADD_MEDIA_CLIP_PREVIEW_OPERATION),
  planDigest: HashSchema, targetTimelineId: IdSchema, baseRevisionId: IdSchema, baseContentHash: HashSchema,
  expectedChangedCanonicalObjects: z.array(z.string()), validation: z.array(z.string()),
  capabilityRequirements: z.array(z.string()), warnings: z.array(z.string()), failures: z.array(z.string()),
  candidateContentHash: HashSchema,
})

const ApplySchema = z.object({
  status: z.enum(['APPLIED', 'NO_OP']), planDigest: HashSchema, baseRevisionId: IdSchema,
  newRevisionId: IdSchema.nullable(), newTimelineContentHash: HashSchema, parentRevisionId: IdSchema.nullable(),
  semanticDiff: z.array(z.string()),
  renderHandoff: z.object({
    projectId: IdSchema,
    timelineRevisionId: IdSchema.nullable(),
    timelineContentHash: HashSchema,
  }),
})

function requestBody(draft: AddMediaClipCommandDraft) {
  return {
    baseRevisionId: draft.baseRevisionId, baseContentHash: draft.baseContentHash,
    trackId: draft.trackId, clipId: draft.clipId, mediaAssetId: draft.mediaAssetId,
    mediaStreamId: draft.mediaStreamId, artifactId: draft.artifactId, contentDigest: draft.contentDigest,
    sourceStart: draft.sourceStart, sourceEnd: draft.sourceEnd, timelineStart: draft.timelineStart,
    timelineEnd: draft.timelineEnd, rateNumerator: draft.rateNumerator,
    rateDenominator: draft.rateDenominator, direction: draft.direction,
  }
}

function operationPath(tenantId: string, project: ProjectId, suffix: 'preview' | 'apply') {
  return `/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(project)}/timeline-operations/add-media-clip/${suffix}`
}

export function createOperationGateway(transport: VersionlessTransport = versionlessTransport): OperationGateway {
  return {
    async previewAddMediaClip(tenantId, project, draft) {
      try {
        const response = await transport.post(operationPath(tenantId, project, 'preview'), requestBody(draft))
        const parsed = PreviewSchema.safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Operation preview response did not match the required contract.')
        const preview: OperationPreview = {
          operation: parsed.data.operation,
          planDigest: planDigest(parsed.data.planDigest), targetTimelineId: timelineId(parsed.data.targetTimelineId),
          baseRevisionId: revisionId(parsed.data.baseRevisionId), baseContentHash: contentHash(parsed.data.baseContentHash),
          expectedChanges: parsed.data.expectedChangedCanonicalObjects, validation: parsed.data.validation,
          capabilityRequirements: parsed.data.capabilityRequirements, warnings: parsed.data.warnings,
          failures: parsed.data.failures, candidateContentHash: contentHash(parsed.data.candidateContentHash),
        }
        if (
          String(preview.targetTimelineId) !== String(project)
          || preview.baseRevisionId !== draft.baseRevisionId
          || preview.baseContentHash !== draft.baseContentHash
        ) {
          return responseValidationFailure('Operation preview did not preserve the requested Project and exact editing base.')
        }
        return { ok: true, value: preview }
      } catch (error) { return gatewayFailure(error) }
    },
    async applyAddMediaClip(tenantId, project, draft, confirmedPreview, commandId) {
      try {
        if (
          confirmedPreview.operation !== ADD_MEDIA_CLIP_PREVIEW_OPERATION
          || String(confirmedPreview.targetTimelineId) !== String(project)
          || confirmedPreview.baseRevisionId !== draft.baseRevisionId
          || confirmedPreview.baseContentHash !== draft.baseContentHash
        ) {
          return responseValidationFailure('Confirmed operation preview did not match the requested Project and exact command base.')
        }
        const response = await transport.post(operationPath(tenantId, project, 'apply'), {
          request: requestBody(draft), expectedPlanDigest: confirmedPreview.planDigest, applyCommandId: commandId,
        })
        const parsed = ApplySchema.safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Operation apply response did not match the required contract.')
        const accepted: AcceptedOperationResult = {
          status: parsed.data.status, planDigest: planDigest(parsed.data.planDigest),
          baseRevisionId: revisionId(parsed.data.baseRevisionId),
          newRevisionId: parsed.data.newRevisionId ? revisionId(parsed.data.newRevisionId) : null,
          newContentHash: contentHash(parsed.data.newTimelineContentHash),
          parentRevisionId: parsed.data.parentRevisionId ? revisionId(parsed.data.parentRevisionId) : null,
          semanticChanges: parsed.data.semanticDiff,
        }
        if (accepted.planDigest !== confirmedPreview.planDigest || accepted.baseRevisionId !== draft.baseRevisionId) {
          return responseValidationFailure('Operation result did not preserve the confirmed preview identity.')
        }
        const handoff = parsed.data.renderHandoff
        if (accepted.status === 'APPLIED') {
          if (
            !accepted.newRevisionId
            || accepted.parentRevisionId !== draft.baseRevisionId
            || accepted.newContentHash !== confirmedPreview.candidateContentHash
            || handoff.projectId !== project
            || handoff.timelineRevisionId !== accepted.newRevisionId
            || contentHash(handoff.timelineContentHash) !== accepted.newContentHash
          ) {
            return responseValidationFailure('APPLIED result did not match the confirmed candidate and exact render handoff.')
          }
        } else if (
          accepted.newRevisionId !== null
          || accepted.parentRevisionId !== null
          || accepted.newContentHash !== draft.baseContentHash
          || handoff.projectId !== project
          || handoff.timelineRevisionId !== null
          || contentHash(handoff.timelineContentHash) !== draft.baseContentHash
        ) {
          return responseValidationFailure('NO_OP result did not preserve the exact base and null-revision render handoff.')
        }
        return { ok: true, value: accepted }
      } catch (error) { return gatewayFailure(error) }
    },
  }
}

export const operationGateway = createOperationGateway()
