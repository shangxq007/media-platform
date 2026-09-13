import type { Review } from './api'
export interface TimelineBrowsing {
  tenant: string; project: string; revision: string; query: string; status: string; scrollTop: number
}
export const browsingDefaults = (tenant = '', project = ''): TimelineBrowsing => ({ tenant, project, revision: '', query: '', status: '', scrollTop: 0 })
export function filterReviews(rows: Review[], query: string, status: string) {
  const needle = query.trim().toLowerCase()
  return rows.filter(row => (!status || row.status === status) && [row.title, row.description, row.reviewId, row.revisionId].some(value => value?.toLowerCase().includes(needle)))
}
// Keep unknown server values verbatim, rather than inventing new state semantics.
export function statusLabel(value: string) {
  const labels: Record<string, string> = { OPEN: 'Open', APPROVED: 'Approved', CHANGES_REQUESTED: 'Changes requested', CLOSED: 'Closed', MERGED: 'Merged', RESOLVED: 'Resolved', APPROVE: 'Approve', REQUEST_CHANGES: 'Request changes', REJECT: 'Reject' }
  return labels[value] ? `${labels[value]} (${value})` : value
}
