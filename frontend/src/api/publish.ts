import { z } from 'zod'
import api from './index'

export const PUBLICATION_ENDPOINT_ACCESS = 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ' as const
export const PUBLICATION_GLOBAL_ACCESS = 'UNKNOWN_FAIL_CLOSED' as const

const requiredId = z.string().trim().min(1).max(180)
function contractError(message: string): Error {
  const error = new Error(message)
  error.name = 'PublicationContractError'
  return error
}
const absoluteInstant = z.string().refine(value => {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/.exec(value)
  if (!match) return false
  const [, year, month, day, hour, minute, second] = match
  const parsed = Date.parse(value)
  if (!Number.isFinite(parsed) || +year < 1 || +month < 1 || +month > 12 || +day < 1 || +hour > 23 || +minute > 59 || +second > 59) return false
  const utc = new Date(Date.UTC(+year, +month - 1, +day))
  return utc.getUTCFullYear() === +year && utc.getUTCMonth() === +month - 1 && utc.getUTCDate() === +day
})

const PublicationAccountSchema = z.object({
  id: requiredId,
  displayName: z.string().max(4000).optional(),
  displayNameAvailability: z.enum(['AVAILABLE', 'NOT_PROVIDED']),
  platformType: z.string().trim().min(1).max(120),
  projectId: requiredId,
  bindingVersion: z.number().int().positive(),
  endpointAccess: z.literal(PUBLICATION_ENDPOINT_ACCESS),
  globalEffectiveAccess: z.literal(PUBLICATION_GLOBAL_ACCESS),
}).strict().superRefine((value, context) => {
  const coherent = value.displayNameAvailability === 'AVAILABLE'
    ? value.displayName !== undefined && value.displayName.trim().length > 0
    : value.displayName === undefined
  if (!coherent) context.addIssue({ code: 'custom', message: 'displayName availability is incoherent' })
})

const PublicationPostSchema = z.object({
  id: requiredId,
  projectId: requiredId,
  connectedAccountId: requiredId,
  bindingVersion: z.number().int().positive(),
  contentText: z.string().max(100_000).optional(),
  contentAvailability: z.enum(['AVAILABLE', 'NOT_PROVIDED', 'RESTRICTED']),
  contentVersionRelationState: z.enum(['NOT_PROVIDED', 'RESTRICTED']),
  artifactId: requiredId.optional(),
  artifactRelationState: z.enum(['AVAILABLE', 'NOT_PROVIDED', 'RESTRICTED']),
  platformType: z.string().trim().min(1).max(120),
  scheduledAt: absoluteInstant.optional(),
  timeMeaning: z.enum(['PLANNED_PUBLISH_TIME', 'NOT_PROVIDED']),
  timePrecision: z.enum(['EXACT_INSTANT', 'UNKNOWN']),
  sourceVerification: z.literal('VERIFIED_LOCAL_RECORD'),
  endpointAccess: z.literal(PUBLICATION_ENDPOINT_ACCESS),
  globalEffectiveAccess: z.literal(PUBLICATION_GLOBAL_ACCESS),
}).strict().superRefine((value, context) => {
  const contentCoherent = value.contentAvailability === 'AVAILABLE' ? value.contentText !== undefined : value.contentText === undefined
  const artifactCoherent = value.artifactRelationState === 'AVAILABLE' ? value.artifactId !== undefined : value.artifactId === undefined
  const contentRelationCoherent = value.contentAvailability === 'RESTRICTED'
    ? value.contentVersionRelationState === 'RESTRICTED'
    : value.contentVersionRelationState === 'NOT_PROVIDED'
  const timeCoherent = value.timeMeaning === 'PLANNED_PUBLISH_TIME'
    ? value.timePrecision === 'EXACT_INSTANT' && value.scheduledAt !== undefined
    : value.timePrecision === 'UNKNOWN' && value.scheduledAt === undefined
  if (!contentCoherent) context.addIssue({ code: 'custom', message: 'content availability is incoherent' })
  if (!artifactCoherent) context.addIssue({ code: 'custom', message: 'Artifact relationship state is incoherent' })
  if (!contentRelationCoherent) context.addIssue({ code: 'custom', message: 'content-version relationship state is incoherent' })
  if (!timeCoherent) context.addIssue({ code: 'custom', message: 'planned-time availability is incoherent' })
})

const PublicationPostListSchema = z.object({
  items: z.array(PublicationPostSchema).max(200),
  coverage: z.literal('BOUNDED_PARTIAL'),
}).strict().superRefine((value, context) => {
  if (value.items.some(item => item.timeMeaning !== 'PLANNED_PUBLISH_TIME' || item.timePrecision !== 'EXACT_INSTANT' || item.scheduledAt === undefined)) {
    context.addIssue({ code: 'custom', message: 'ranged publication lists contain planned instants only' })
  }
})

export type PublicationAccountDto = z.infer<typeof PublicationAccountSchema>
export type PublicationPostDto = z.infer<typeof PublicationPostSchema>
export type PublicationPostListDto = z.infer<typeof PublicationPostListSchema>
export interface PublicationListRequest {
  readonly projectId: string
  readonly connectedAccountId: string
  readonly bindingVersion: number
  readonly start: string
  readonly end: string
  readonly limit: number
}
export interface PublicationDetailRequest {
  readonly id: string
  readonly projectId: string
  readonly connectedAccountId: string
  readonly bindingVersion: number
}

const PublicationListRequestSchema = z.object({
  projectId: requiredId,
  connectedAccountId: requiredId,
  bindingVersion: z.number().int().positive(),
  start: absoluteInstant,
  end: absoluteInstant,
  limit: z.number().int().min(1).max(200),
}).strict().superRefine((value, context) => {
  if (Date.parse(value.start) >= Date.parse(value.end)) context.addIssue({ code: 'custom', message: 'publication window must be half-open and increasing' })
})
const PublicationDetailRequestSchema = z.object({
  id: requiredId,
  projectId: requiredId,
  connectedAccountId: requiredId,
  bindingVersion: z.number().int().positive(),
}).strict()

/** Canonical read-only contract. It reuses the established authenticated Axios instance. */
export const PublicationReadAPI = {
  async getAccounts(projectId: string, signal: AbortSignal): Promise<PublicationAccountDto[]> {
    const validProjectId = requiredId.parse(projectId)
    const { data } = await api.get('/api/social/platforms', { baseURL: '', params: { projectId: validProjectId }, signal })
    const accounts = z.array(PublicationAccountSchema).max(200).parse(data)
    if (accounts.some(account => account.projectId !== validProjectId)) throw contractError('Publication account Project scope mismatch.')
    return accounts
  },
  async getPosts(request: PublicationListRequest, signal: AbortSignal): Promise<PublicationPostListDto> {
    const valid = PublicationListRequestSchema.parse(request)
    const { data } = await api.get('/api/social/posts', { baseURL: '', params: valid, signal })
    const result = PublicationPostListSchema.parse(data)
    if (result.items.some(post => post.projectId !== valid.projectId
      || post.connectedAccountId !== valid.connectedAccountId
      || post.bindingVersion !== valid.bindingVersion)) {
      throw contractError('Publication list current-binding tuple mismatch.')
    }
    return result
  },
  async getPost(request: PublicationDetailRequest, signal: AbortSignal): Promise<PublicationPostDto> {
    const valid = PublicationDetailRequestSchema.parse(request)
    const { data } = await api.get(`/api/social/posts/${encodeURIComponent(valid.id)}`, {
      baseURL: '', params: {
        projectId: valid.projectId,
        connectedAccountId: valid.connectedAccountId,
        bindingVersion: valid.bindingVersion,
      }, signal,
    })
    const post = PublicationPostSchema.parse(data)
    if (post.id !== valid.id
      || post.projectId !== valid.projectId
      || post.connectedAccountId !== valid.connectedAccountId
      || post.bindingVersion !== valid.bindingVersion) {
      throw contractError('Publication detail identity or current-binding tuple mismatch.')
    }
    return post
  },
}
