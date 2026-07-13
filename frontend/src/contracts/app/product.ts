import { z } from 'zod'
import { IdString, DateTimeString, TenantId, ProjectId } from '../shared/primitives'

export const ProductType = z.enum(['RAW_MEDIA', 'FINAL_RENDER', 'INTERMEDIATE'])
export const ProductStatus = z.enum(['ACTIVE', 'ARCHIVED', 'DELETED'])

export const ProductSummary = z.object({
  id: IdString,
  tenantId: TenantId,
  projectId: ProjectId,
  type: ProductType,
  status: ProductStatus,
  createdAt: DateTimeString,
  updatedAt: DateTimeString,
})

export const ProductListResponse = z.object({
  items: z.array(ProductSummary),
  total: z.number(),
})

export type ProductSummary = z.infer<typeof ProductSummary>
export type ProductListResponse = z.infer<typeof ProductListResponse>
