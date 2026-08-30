import { z } from 'zod'
import { IdString, DateTimeString, UrlString } from '../shared/primitives'

export const ArtifactSummary = z.object({
  id: IdString,
  type: z.string(),
  kind: z.string(),
  contentDigest: z.string(),
  byteLength: z.number().int().nonnegative(),
  state: z.string(),
  integrityState: z.string(),
  createdAt: DateTimeString,
}).strict()

export const ArtifactListResponse = z.object({
  items: z.array(ArtifactSummary),
  total: z.number(),
})

export const AccessDescriptor = z.object({
  artifactId: IdString,
  accessUrl: UrlString,
  expiresAt: DateTimeString,
}).strict()

export const ArtifactAccessResponse = z.object({
  access: AccessDescriptor,
})

export type ArtifactSummary = z.infer<typeof ArtifactSummary>
export type ArtifactListResponse = z.infer<typeof ArtifactListResponse>
export type AccessDescriptor = z.infer<typeof AccessDescriptor>
export type ArtifactAccessResponse = z.infer<typeof ArtifactAccessResponse>
