import { z } from 'zod'
import type { RevisionChangeSummary, RevisionComparison, RevisionDetail, RevisionListEntry, TimelineQueryGateway } from '../../product/timeline/gateways'
import { contentHash, projectId, revisionId, timelineId, type ProjectId } from '../../product/timeline/types'
import { gatewayFailure, responseValidationFailure } from './gateway-error'
import { versionlessTransport, type VersionlessTransport } from './versionless-api'

const IdSchema = z.string().min(1).max(64).regex(/^[A-Za-z0-9._:-]+$/)
const HashSchema = z.string().regex(/^[0-9a-fA-F]{64}$/)

const RevisionSchema = z.object({
  id: IdSchema,
  revisionNumber: z.number().int(),
  parentRevisionId: IdSchema.nullable().optional(),
  source: z.string().nullish(),
  message: z.string().nullish(),
  labels: z.array(z.string()).nullish(),
  authorUserId: z.string().nullish(),
  createdAt: z.string(),
  isMerge: z.boolean().optional(),
})

const SummarySchema = z.object({
  supported: z.boolean().optional(),
  tracksAdded: z.number().int().optional(),
  tracksRemoved: z.number().int().optional(),
  tracksModified: z.number().int().optional(),
  clipsAdded: z.number().int().optional(),
  clipsRemoved: z.number().int().optional(),
  clipsModified: z.number().int().optional(),
  assetsAdded: z.number().int().optional(),
  assetsRemoved: z.number().int().optional(),
})

const CurrentSchema = z.object({
  revisionId: IdSchema,
  revision: z.object({
    revisionId: IdSchema,
    productId: IdSchema,
    semanticContext: z.object({ timelineContentDigest: HashSchema }),
  }),
})

const DetailSchema = z.object({ revision: RevisionSchema, changeSummary: SummarySchema, patchOpCount: z.number().int() })
const ComparisonSchema = z.object({
  fromRevision: RevisionSchema,
  toRevision: RevisionSchema,
  summary: SummarySchema,
  entityChanges: z.array(z.object({ kind: z.string(), entityId: z.string(), action: z.string() })),
})

function mapRevision(input: z.infer<typeof RevisionSchema>): RevisionListEntry {
  return {
    id: revisionId(input.id),
    revisionNumber: input.revisionNumber,
    parentRevisionId: input.parentRevisionId ? revisionId(input.parentRevisionId) : null,
    source: input.source ?? 'UNKNOWN',
    message: input.message ?? null,
    labels: input.labels ?? [],
    authorUserId: input.authorUserId ?? null,
    createdAt: input.createdAt,
    isMerge: input.isMerge ?? false,
  }
}

function mapSummary(input: z.infer<typeof SummarySchema>): RevisionChangeSummary {
  return {
    supported: input.supported ?? false,
    tracksAdded: input.tracksAdded ?? 0,
    tracksRemoved: input.tracksRemoved ?? 0,
    tracksModified: input.tracksModified ?? 0,
    clipsAdded: input.clipsAdded ?? 0,
    clipsRemoved: input.clipsRemoved ?? 0,
    clipsModified: input.clipsModified ?? 0,
    assetsAdded: input.assetsAdded ?? 0,
    assetsRemoved: input.assetsRemoved ?? 0,
  }
}

const projectPath = (value: ProjectId) => encodeURIComponent(value)

export function createTimelineQueryGateway(transport: VersionlessTransport = versionlessTransport): TimelineQueryGateway {
  return {
    async getHead(requestedProjectId) {
      try {
        const response = await transport.get(`/timeline-git/products/${projectPath(requestedProjectId)}/revisions/current`)
        const parsed = CurrentSchema.safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Canonical HEAD response did not match the required contract.')
        if (parsed.data.revisionId !== parsed.data.revision.revisionId || parsed.data.revision.productId !== requestedProjectId) {
          return responseValidationFailure('Canonical HEAD identity did not match the requested Project.')
        }
        return { ok: true, value: {
          projectId: projectId(parsed.data.revision.productId),
          timelineId: timelineId(parsed.data.revision.productId),
          revisionId: revisionId(parsed.data.revisionId),
          contentHash: contentHash(parsed.data.revision.semanticContext.timelineContentDigest),
        } }
      } catch (error) { return gatewayFailure(error) }
    },
    async listRevisions(requestedProjectId) {
      try {
        const response = await transport.get(`/render/projects/${projectPath(requestedProjectId)}/timeline/revisions`, { params: { limit: 50 } })
        const parsed = z.array(RevisionSchema).safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Revision history response did not match the required contract.')
        return { ok: true, value: parsed.data.map(mapRevision) }
      } catch (error) { return gatewayFailure(error) }
    },
    async getRevision(requestedProjectId, requestedRevisionId) {
      try {
        const response = await transport.get(`/render/projects/${projectPath(requestedProjectId)}/timeline/revisions/${encodeURIComponent(requestedRevisionId)}`)
        const parsed = DetailSchema.safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Revision detail response did not match the required contract.')
        if (parsed.data.revision.id !== requestedRevisionId) {
          return responseValidationFailure('Revision detail identity did not match the exact requested revision.')
        }
        const detail: RevisionDetail = {
          revision: mapRevision(parsed.data.revision),
          changeSummary: mapSummary(parsed.data.changeSummary),
          changeCount: parsed.data.patchOpCount,
        }
        return { ok: true, value: detail }
      } catch (error) { return gatewayFailure(error) }
    },
    async compare(requestedProjectId, from, to) {
      try {
        const response = await transport.get(`/render/projects/${projectPath(requestedProjectId)}/timeline/revisions/compare`, { params: { from, to } })
        const parsed = ComparisonSchema.safeParse(response.data)
        if (!parsed.success) return responseValidationFailure('Revision comparison response did not match the required contract.')
        if (parsed.data.fromRevision.id !== from || parsed.data.toRevision.id !== to) {
          return responseValidationFailure('Revision comparison identities did not match the exact ordered requested pair.')
        }
        const comparison: RevisionComparison = {
          fromRevision: mapRevision(parsed.data.fromRevision),
          toRevision: mapRevision(parsed.data.toRevision),
          summary: mapSummary(parsed.data.summary),
          entityChanges: parsed.data.entityChanges,
        }
        return { ok: true, value: comparison }
      } catch (error) { return gatewayFailure(error) }
    },
  }
}

export const timelineQueryGateway = createTimelineQueryGateway()
