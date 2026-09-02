import type {
  GatewayFailureCode,
  GatewayResult,
  OperationGateway,
  OperationPreview,
  AcceptedOperationResult,
  AddMediaClipCommandDraft,
  TimelineQueryGateway,
  CanonicalHeadReference,
  RevisionListEntry,
  RevisionDetail,
  RevisionComparison,
} from '../gateways'
import type { ApplyCommandId, ProjectId, RevisionId } from '../types'

export function mockFailure(code: GatewayFailureCode, message = code): GatewayResult<never> {
  return { ok: false, code, message, details: [], retryable: code === 'NETWORK' || code === 'UNAVAILABLE' }
}

export class ScriptedOperationGateway implements OperationGateway {
  previewResult: GatewayResult<OperationPreview>
  applyResult: GatewayResult<AcceptedOperationResult>
  readonly previewCalls: Array<{ tenantId: string; projectId: ProjectId; draft: AddMediaClipCommandDraft }> = []
  readonly applyCalls: Array<{ tenantId: string; projectId: ProjectId; draft: AddMediaClipCommandDraft; preview: OperationPreview; commandId: ApplyCommandId }> = []

  constructor(previewResult: GatewayResult<OperationPreview>, applyResult: GatewayResult<AcceptedOperationResult>) {
    this.previewResult = previewResult
    this.applyResult = applyResult
  }

  async previewAddMediaClip(tenantId: string, projectId: ProjectId, draft: AddMediaClipCommandDraft) {
    this.previewCalls.push({ tenantId, projectId, draft })
    return this.previewResult
  }

  async applyAddMediaClip(tenantId: string, projectId: ProjectId, draft: AddMediaClipCommandDraft, preview: OperationPreview, commandId: ApplyCommandId) {
    this.applyCalls.push({ tenantId, projectId, draft, preview, commandId })
    return this.applyResult
  }
}

export class ScriptedTimelineQueryGateway implements TimelineQueryGateway {
  headResult: GatewayResult<CanonicalHeadReference>
  historyResult: GatewayResult<readonly RevisionListEntry[]>
  detailResult: GatewayResult<RevisionDetail>
  comparisonResult: GatewayResult<RevisionComparison>
  readonly headCalls: ProjectId[] = []
  readonly historyCalls: ProjectId[] = []
  readonly detailCalls: Array<{ projectId: ProjectId; revisionId: RevisionId }> = []
  readonly comparisonCalls: Array<{ projectId: ProjectId; from: RevisionId; to: RevisionId }> = []

  constructor(results: {
    head: GatewayResult<CanonicalHeadReference>
    history: GatewayResult<readonly RevisionListEntry[]>
    detail: GatewayResult<RevisionDetail>
    comparison: GatewayResult<RevisionComparison>
  }) {
    this.headResult = results.head
    this.historyResult = results.history
    this.detailResult = results.detail
    this.comparisonResult = results.comparison
  }

  async getHead(requestedProjectId: ProjectId) {
    this.headCalls.push(requestedProjectId)
    return this.headResult
  }
  async listRevisions(requestedProjectId: ProjectId) {
    this.historyCalls.push(requestedProjectId)
    return this.historyResult
  }
  async getRevision(requestedProjectId: ProjectId, requestedRevisionId: RevisionId) {
    this.detailCalls.push({ projectId: requestedProjectId, revisionId: requestedRevisionId })
    return this.detailResult
  }
  async compare(requestedProjectId: ProjectId, from: RevisionId, to: RevisionId) {
    this.comparisonCalls.push({ projectId: requestedProjectId, from, to })
    return this.comparisonResult
  }
}
