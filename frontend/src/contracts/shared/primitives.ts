import { z } from 'zod'

export const IdString = z.string()
export const TenantId = z.string()
export const ProjectId = z.string()
export const ProductId = z.string()
export const RenderJobId = z.string()
export const ArtifactId = z.string()
export const TimelineRevisionId = z.string()
export const DateTimeString = z.string()
export const UrlString = z.string().url()
export const DurationMs = z.number()

export const PaginationRequest = z.object({
  page: z.number().optional(),
  pageSize: z.number().optional(),
  limit: z.number().optional(),
  offset: z.number().optional(),
})

export const PaginationResponse = z.object({
  total: z.number(),
  page: z.number().optional(),
  pageSize: z.number().optional(),
  limit: z.number().optional(),
  offset: z.number().optional(),
})

export const ApiError = z.object({
  message: z.string(),
  code: z.string().optional(),
  status: z.number().optional(),
})

export const TenantProjectScope = z.object({
  tenantId: TenantId,
  projectId: ProjectId,
})

export type IdString = z.infer<typeof IdString>
export type TenantId = z.infer<typeof TenantId>
export type ProjectId = z.infer<typeof ProjectId>
export type DateTimeString = z.infer<typeof DateTimeString>
export type ApiError = z.infer<typeof ApiError>
