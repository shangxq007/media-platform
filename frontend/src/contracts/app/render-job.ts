import { z } from 'zod'
import { IdString } from '../shared/primitives'

export const RENDER_JOB_STATUSES = [
  'QUEUED',
  'SELECTING_PROVIDER',
  'PROVIDER_SELECTED',
  'EXECUTING',
  'COMPLETING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'REJECTED',
] as const

export const RenderJobStatusSchema = z.enum(RENDER_JOB_STATUSES)

export const RenderJobSummary = z.object({
  id: IdString,
  projectId: IdString,
  timelineSnapshotId: IdString,
  profile: z.string(),
  status: RenderJobStatusSchema,
})

// ArtifactInfoResponse includes storageUri. Deliberately omitting it here makes
// Zod strip the storage coordinate at the transport boundary before product UI
// can consume the response.
export const RenderJobArtifactSummary = z.object({
  artifactId: IdString,
  renderJobId: IdString,
  projectId: IdString,
  format: z.string().nullable(),
  resolution: z.string().nullable(),
  duration: z.number(),
  createdAt: z.string(),
})

export const ArtifactAccessType = z.enum([
  'SIGNED_URL',
  'LOCAL_STREAM',
  'UNSUPPORTED',
  'NOT_READY',
  'NOT_FOUND',
  'ACCESS_FAILED',
])

export const ArtifactAccessDescriptor = z.object({
  productId: IdString.nullable(),
  artifactId: IdString.nullable(),
  accessType: ArtifactAccessType,
  method: z.string().nullable(),
  url: z.string().url().nullable(),
  expiresAt: z.string().nullable(),
  ttlSeconds: z.number().int().nullable(),
  mimeType: z.string().nullable(),
  filename: z.string().nullable(),
  sizeBytes: z.number().nullable(),
  status: z.string(),
  message: z.string().nullable(),
  redacted: z.literal(true),
})

export const RenderWorkspaceScope = z.object({
  tenantId: IdString.nullable(),
  recentProjects: z.array(z.object({
    id: IdString,
    name: z.string(),
  })),
})

export type RenderJobStatus = z.infer<typeof RenderJobStatusSchema>
export type RenderJobSummary = z.infer<typeof RenderJobSummary>
export type RenderJobArtifactSummary = z.infer<typeof RenderJobArtifactSummary>
export type ArtifactAccessDescriptor = z.infer<typeof ArtifactAccessDescriptor>
export type RenderWorkspaceScope = z.infer<typeof RenderWorkspaceScope>
