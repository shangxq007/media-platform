import { z } from 'zod'
import { IdString, DateTimeString } from '../shared/primitives'

export const SafePreflightReportListItem = z.object({
  recordId: IdString,
  rawMediaProductId: z.string(),
  uploadAttemptId: z.string().optional(),
  createdAt: DateTimeString,
  expiresAt: DateTimeString,
  lifecycleState: z.string(),
  overallDecision: z.string(),
  warningCount: z.number(),
  findingCount: z.number(),
  policyDecision: z.string(),
  uploadContinues: z.boolean(),
  blocking: z.boolean(),
})

export const SafePreflightReportListResponse = z.object({
  tenantId: z.string(),
  projectId: z.string(),
  items: z.array(SafePreflightReportListItem),
  totalCount: z.number(),
})

export const RetentionDryRunResponse = z.object({
  tenantId: z.string(),
  projectId: z.string(),
  eligibleExpiredCount: z.number(),
  wouldProcessCount: z.number(),
  wouldDeleteCount: z.number(),
  safetyChecksPassed: z.boolean(),
  outcome: z.string(),
})

export type SafePreflightReportListItem = z.infer<typeof SafePreflightReportListItem>
export type SafePreflightReportListResponse = z.infer<typeof SafePreflightReportListResponse>
export type RetentionDryRunResponse = z.infer<typeof RetentionDryRunResponse>
