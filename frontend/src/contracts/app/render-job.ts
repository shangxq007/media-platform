import { z } from 'zod'
import { IdString, DateTimeString, DurationMs } from '../shared/primitives'

export const RenderJobStatus = z.enum([
  'QUEUED', 'EXECUTING', 'COMPLETED', 'FAILED', 'CANCELLED'
])

export const RenderJobSummary = z.object({
  id: IdString,
  status: RenderJobStatus,
  createdAt: DateTimeString,
  completedAt: DateTimeString.optional(),
  durationMs: DurationMs.optional(),
})

export const RenderJobStatusResponse = z.object({
  job: RenderJobSummary,
})

export type RenderJobSummary = z.infer<typeof RenderJobSummary>
export type RenderJobStatusResponse = z.infer<typeof RenderJobStatusResponse>
