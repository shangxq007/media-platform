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

export const RenderWorkspaceScope = z.object({
  tenantId: IdString.nullable(),
  recentProjects: z.array(z.object({
    id: IdString,
    name: z.string(),
  })),
})

export type RenderJobStatus = z.infer<typeof RenderJobStatusSchema>
export type RenderJobSummary = z.infer<typeof RenderJobSummary>
export type RenderWorkspaceScope = z.infer<typeof RenderWorkspaceScope>
