import { z } from 'zod'
import { IdString, DateTimeString } from '../shared/primitives'

export const UploadRawMediaResponse = z.object({
  productId: IdString,
  status: z.enum(['SUCCESS', 'FAILED']),
  createdAt: DateTimeString,
})

export const UploadErrorResponse = z.object({
  message: z.string(),
  code: z.string().optional(),
})

export type UploadRawMediaResponse = z.infer<typeof UploadRawMediaResponse>
export type UploadErrorResponse = z.infer<typeof UploadErrorResponse>
