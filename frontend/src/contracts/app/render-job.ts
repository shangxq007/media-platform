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
}).strict()

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

/** Wire identity syntax only. The authenticated backend catalog owns availability and artwork. */
export function subtitleDecorationId(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 64
    && /^(?:[a-z0-9]+-)+v[1-9][0-9]*$(?![\s\S])/.test(value)
}
