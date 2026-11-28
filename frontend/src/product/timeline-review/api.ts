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
export const FrameAnchor = z.object({
  side: z.enum(['A', 'B']),
  branch: z.string().regex(/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/),
  position: z.string().regex(/^[0-9]{1,19}\/[1-9][0-9]{0,18}$/),
}).strict()
export type FrameAnchor = z.infer<typeof FrameAnchor>
const ReviewComment = z.object({ commentId: id, reviewId: id, threadId: text, revisionId: text, entityRef: text,
  authorUserId: text, content: z.string(), createdAt: text, frameAnchor: FrameAnchor.nullish(),
  frameStatus: z.enum(['OPEN', 'RESOLVED']).optional(),
  frameStatusVersion: z.string().regex(/^(0|[1-9][0-9]{0,18})$/).optional() }).strict()
export type ReviewComment = z.infer<typeof ReviewComment>
export type FrameCommentDraft = { revisionId: string; frameAnchor: FrameAnchor }
export type SavedFrameComment = ReviewComment & FrameCommentDraft & {
  frameStatus: 'OPEN' | 'RESOLVED'
  frameStatusVersion: string
}
export type FrameCommentWrite = FrameCommentDraft & { content: string; requestId: string }
export type FrameStatusAction = { action: 'resolve' | 'reopen'; expectedVersion: string }

export const ReviewDetail = z.object({ review: Review,
  comments: z.array(ReviewComment),
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

function sameFrameComment(row: ReviewComment, requested: ReviewComment) {
  return row.commentId === requested.commentId && row.reviewId === requested.reviewId
    && row.revisionId === requested.revisionId && row.content === requested.content
    && row.frameAnchor && requested.frameAnchor && row.frameAnchor.side === requested.frameAnchor.side
    && row.frameAnchor.branch === requested.frameAnchor.branch
    && row.frameAnchor.position === requested.frameAnchor.position
    && row.frameStatus && row.frameStatusVersion !== undefined
}

export const timelineCommentSource = {
  async list(project: string, signal: AbortSignal) {
    const rows = ReviewComment.array().parse(await get(`${path(project)}/reviews/comments`, signal))
    unique(rows.map(row => row.commentId))
    return rows
  },
  async readFrame(project: string, commentId: string, signal: AbortSignal): Promise<SavedFrameComment> {
    const row = ReviewComment.parse(await get(`${path(project)}/reviews/comments/${part(commentId)}/frame`, signal))
    if (row.commentId !== commentId || !row.revisionId || !row.frameAnchor || !row.frameStatus
      || row.frameStatusVersion === undefined) throw new Error('Saved frame comment identity mismatch')
    return { ...row, revisionId: row.revisionId, frameAnchor: row.frameAnchor,
      frameStatus: row.frameStatus, frameStatusVersion: row.frameStatusVersion }
  },
  async submitFrame(project: string, body: FrameCommentWrite, signal: AbortSignal) {
    const { data } = await api.post(`${path(project)}/reviews/comments`, body, { baseURL: '', signal })
    signal.throwIfAborted()
    const row = ReviewComment.parse(data)
    if (row.revisionId !== body.revisionId || row.content !== body.content
      || row.frameAnchor?.side !== body.frameAnchor.side || row.frameAnchor.branch !== body.frameAnchor.branch
      || row.frameAnchor.position !== body.frameAnchor.position) throw new Error('Comment target mismatch')
    return row
  },
  async transitionFrame(project: string, comment: ReviewComment, command: FrameStatusAction, signal: AbortSignal) {
    const { action, expectedVersion } = command
    const { data, status } = await api.post(`${path(project)}/reviews/comments/${part(comment.commentId)}/${action}`,
      { expectedVersion }, { baseURL: '', signal, validateStatus: value => value === 200 || value === 409 })
    signal.throwIfAborted()
    const row = ReviewComment.parse(data)
    if (!sameFrameComment(row, comment)
      || (status === 200 && row.frameStatus !== (action === 'resolve' ? 'RESOLVED' : 'OPEN'))) {
      throw new Error('Comment action target mismatch')
    }
    return { row, stale: status === 409 }
  },
  async refreshFrame(project: string, comment: ReviewComment, signal: AbortSignal) {
    const rows = await this.list(project, signal)
    const row = rows.find(row => row.commentId === comment.commentId)
    if (!row || !sameFrameComment(row, comment)) throw new Error('Saved frame comment could not be confirmed')
    return row
  },
}
