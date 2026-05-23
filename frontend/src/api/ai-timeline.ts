import api from './index'

export interface AiProposalDto {
  id: string
  status: string
  summary: string
  createdAt: string
  operationCount: number
}

export interface AiTimelineEditRequest {
  tenantId: string
  projectId: string
  baseJobId?: string
  baseTimelineJson?: string
  instruction: string
  editSessionId?: string
  intent?: string
  conversationId?: string
  humanInTheLoop?: boolean
}

export interface AiTimelineEditResponse {
  timelineJson: string
  provider: string
  model: string
  appliedPatch: boolean
  proposals: AiProposalDto[]
  pendingProposalId?: string | null
}

export interface TimelineInternalPreviewResponse {
  internalTimelineJson: string
  sourceSchema: string
  alreadyInternal: boolean
  sourceTrackOrLayerCount: number
  internalTrackOrLayerCount: number
  sourceClipCount: number
  internalClipCount: number
  targetRevision: number
  jsonByteDelta: number
}

export const AiTimelineAPI = {
  async edit(
    tenantId: string,
    projectId: string,
    body: Omit<AiTimelineEditRequest, 'tenantId' | 'projectId'>
  ): Promise<AiTimelineEditResponse> {
    const { data } = await api.post<AiTimelineEditResponse>(
      `/tenants/${tenantId}/projects/${projectId}/timeline/ai-edit`,
      { tenantId, projectId, ...body }
    )
    return data
  },

  async previewInternal(
    tenantId: string,
    projectId: string,
    timelineJson: string
  ): Promise<TimelineInternalPreviewResponse> {
    const { data } = await api.post<TimelineInternalPreviewResponse>(
      `/tenants/${tenantId}/projects/${projectId}/timeline/preview-internal`,
      { timelineJson }
    )
    return data
  },

  async adoptProposal(
    tenantId: string,
    projectId: string,
    proposalId: string,
    timelineJson: string,
    options?: { editSessionId?: string; persistRevision?: boolean }
  ): Promise<AiTimelineEditResponse> {
    const { data } = await api.post<AiTimelineEditResponse>(
      `/tenants/${tenantId}/projects/${projectId}/timeline/ai-proposals/${proposalId}/adopt`,
      {
        timelineJson,
        editSessionId: options?.editSessionId,
        persistRevision: options?.persistRevision,
      }
    )
    return data
  },

  async rejectProposal(
    tenantId: string,
    projectId: string,
    proposalId: string,
    timelineJson: string
  ): Promise<AiTimelineEditResponse> {
    const { data } = await api.post<AiTimelineEditResponse>(
      `/tenants/${tenantId}/projects/${projectId}/timeline/ai-proposals/${proposalId}/reject`,
      { timelineJson }
    )
    return data
  },
}

export default AiTimelineAPI
