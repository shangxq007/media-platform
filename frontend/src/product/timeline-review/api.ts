import { z } from 'zod'
import api from '../../api'
import { renderReadSource, renderHttpStatus } from '../../api/render-jobs'

const id = z.string().min(1)
const text = z.string().nullable()
const count = z.number().int()
const summary = z.object({ supported: z.boolean(), tracksAdded: count, tracksRemoved: count, tracksModified: count,
  clipsAdded: count, clipsRemoved: count, clipsModified: count, assetsAdded: count, assetsRemoved: count,
  parentInternalRevision: count, currentInternalRevision: count }).strict()
export const Revision = z.object({ id, revisionNumber: count, parentRevisionId: text, snapshotId: id,
  internalRevision: count, source: z.string(), message: text, labels: z.array(z.string()), authorUserId: text,
  editSessionId: text, patchOpCount: count, createdAt: text, changeSummary: summary, isMerge: z.boolean(),
  mergeParentRevisionIds: text, mergeBaseRevisionId: text }).strict()
export type Revision = z.infer<typeof Revision>
export const Review = z.object({ reviewId: id, projectId: id, revisionId: id, authorUserId: text,
  title: text, description: text, status: id, createdAt: text, updatedAt: text }).strict()
export type Review = z.infer<typeof Review>
export const ReviewDetail = z.object({ review: Review,
  comments: z.array(z.object({ commentId: id, reviewId: id, threadId: text, revisionId: text, entityRef: text, authorUserId: text, content: z.string(), createdAt: text }).strict()),
  threads: z.array(z.object({ threadId: id, reviewId: id, entityRef: text, diffId: text, status: id, createdAt: text }).strict()),
  decisions: z.array(z.object({ decisionId: id, reviewId: id, reviewerUserId: text, decision: id, createdAt: text }).strict()),
  mergeGuard: z.object({ canMerge: z.boolean(), reason: text }).strict(),
}).strict()
export type ReviewDetail = z.infer<typeof ReviewDetail>
export const httpStatus = renderHttpStatus
const part = (value: string) => encodeURIComponent(id.parse(value))
const path = (project: string) => `/api/render/projects/${part(project)}/timeline`
export const READ_LIMIT = 30
async function get(url: string, signal: AbortSignal) {
  const response = await api.get(url, { baseURL: '', signal })
  signal.throwIfAborted()
  return response.data
}
function unique(values: string[]) { if (new Set(values).size !== values.length) throw new Error('Duplicate resource identity') }
export const timelineReviewSource = {
  projects: renderReadSource.projects,
  project: renderReadSource.project,
  async revisions(project: string, signal: AbortSignal) {
    const rows = Revision.array().parse(await get(`${path(project)}/revisions?limit=${READ_LIMIT}`, signal))
    unique(rows.map(row => row.id)); return rows
  },
  async head(project: string, signal: AbortSignal) {
    try { return Revision.parse(await get(`${path(project)}/revisions/head`, signal)) }
    catch (error) { if (httpStatus(error) === 404) return null; throw error }
  },
  async reviews(project: string, signal: AbortSignal) {
    const rows = Review.array().parse(await get(`${path(project)}/reviews?limit=${READ_LIMIT}`, signal))
    if (rows.some(row => row.projectId !== project)) throw new Error('Review scope mismatch')
    unique(rows.map(row => row.reviewId)); return rows
  },
  async detail(project: string, reviewId: string, signal: AbortSignal) {
    const result = ReviewDetail.parse(await get(`${path(project)}/reviews/${part(reviewId)}`, signal))
    if (result.review.projectId !== project || result.review.reviewId !== reviewId
      || [...result.comments, ...result.threads, ...result.decisions].some(row => row.reviewId !== reviewId)) throw new Error('Review detail scope mismatch')
    unique(result.comments.map(row => row.commentId)); unique(result.threads.map(row => row.threadId)); unique(result.decisions.map(row => row.decisionId))
    return result
  },
}
export type TimelineReviewSource = typeof timelineReviewSource
