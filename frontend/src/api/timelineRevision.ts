import api from './index'

export interface TimelineChangeSummary {
  supported: boolean
  tracksAdded: number
  tracksRemoved: number
  tracksModified: number
  clipsAdded: number
  clipsRemoved: number
  clipsModified: number
  assetsAdded: number
  assetsRemoved: number
  parentInternalRevision: number
  currentInternalRevision: number
}

export interface TimelineRevisionListItem {
  id: string
  revisionNumber: number
  parentRevisionId: string | null
  snapshotId: string
  internalRevision: number
  source: string
  message: string | null
  labels: string[]
  authorUserId: string | null
  editSessionId: string | null
  patchOpCount: number
  createdAt: string | null
  changeSummary: TimelineChangeSummary
}

export interface TimelineEditSessionItem {
  editSessionId: string
  lastAt: string | null
  revisionCount: number
}

export interface TimelineEntityChange {
  kind: string
  entityId: string
  action: 'added' | 'removed' | 'modified' | string
}

export interface TimelinePatchPath {
  op: string
  path: string
}

export interface TimelineRevisionCompareResult {
  fromRevision: TimelineRevisionListItem
  toRevision: TimelineRevisionListItem
  summary: TimelineChangeSummary
  entityChanges: TimelineEntityChange[]
  patchPaths: TimelinePatchPath[]
  patchOpCount: number
}

export interface TimelinePatchPreviewResult {
  revisionId: string
  hasPatchOps: boolean
  success: boolean
  patchPaths: TimelinePatchPath[]
  appliedOps: string[]
  errors: string[]
  contentHashBefore: string | null
  contentHashAfter: string | null
  revisionContentHash: string | null
}

export interface TimelinePatchStep {
  stepIndex: number
  op: string
  path: string
  success: boolean
  appliedOps: string[]
  errors: string[]
  contentHashAfter: string | null
}

export interface TimelinePatchStepsResult {
  revisionId: string
  hasPatchOps: boolean
  allStepsSucceeded: boolean
  steps: TimelinePatchStep[]
}

export interface TimelineRestoreResponse {
  newRevision: TimelineRevisionListItem
  editorTimelineJson: string
  internalTimelineJson: string
}

export interface TimelineRevisionSnapshotResponse {
  revisionId: string
  snapshotId: string
  internalTimelineJson: string
  schemaVersion: string
}

export interface TimelineSyncOptions {
  authorUserId?: string
  editSessionId?: string
  message?: string
  source?: string
}

export interface TimelineRevisionListOptions {
  limit?: number
  editSessionId?: string
  authorUserId?: string
  source?: string
}

export interface TimelineRevisionAuthorFacet {
  authorUserId: string
  revisionCount: number
}

export interface TimelineRevisionFacets {
  sources: string[]
  authors: TimelineRevisionAuthorFacet[]
}

export const TimelineRevisionAPI = {
  async list(
    projectId: string,
    limitOrOptions: number | TimelineRevisionListOptions = 30,
    editSessionId?: string
  ): Promise<TimelineRevisionListItem[]> {
    const opts: TimelineRevisionListOptions =
      typeof limitOrOptions === 'number'
        ? { limit: limitOrOptions, editSessionId }
        : limitOrOptions
    const { data } = await api.get(`/render/projects/${projectId}/timeline/revisions`, {
      params: {
        limit: opts.limit ?? 30,
        editSessionId: opts.editSessionId || undefined,
        authorUserId: opts.authorUserId || undefined,
        source: opts.source || undefined,
      },
    })
    return data
  },

  async facets(projectId: string): Promise<TimelineRevisionFacets> {
    const { data } = await api.get(`/render/projects/${projectId}/timeline/revisions/facets`)
    return data
  },

  async updateAnnotation(
    projectId: string,
    revisionId: string,
    message: string,
    labels?: string[]
  ): Promise<TimelineRevisionListItem> {
    const { data } = await api.patch(
      `/render/projects/${projectId}/timeline/revisions/${revisionId}/annotation`,
      { message, labels: labels ?? [] }
    )
    return data
  },

  async listEditSessions(projectId: string, limit = 20): Promise<TimelineEditSessionItem[]> {
    const { data } = await api.get(
      `/render/projects/${projectId}/timeline/revisions/edit-sessions`,
      { params: { limit } }
    )
    return data
  },

  async compare(
    projectId: string,
    fromRevisionId: string,
    toRevisionId: string
  ): Promise<TimelineRevisionCompareResult> {
    const { data } = await api.get(`/render/projects/${projectId}/timeline/revisions/compare`, {
      params: { from: fromRevisionId, to: toRevisionId },
    })
    return data
  },

  async head(projectId: string): Promise<TimelineRevisionListItem | null> {
    try {
      const { data } = await api.get(`/render/projects/${projectId}/timeline/revisions/head`)
      return data
    } catch {
      return null
    }
  },

  async restore(projectId: string, revisionId: string): Promise<TimelineRestoreResponse> {
    const { data } = await api.post(
      `/render/projects/${projectId}/timeline/revisions/${revisionId}/restore`
    )
    return data
  },

  async patchPreview(
    projectId: string,
    revisionId: string
  ): Promise<TimelinePatchPreviewResult> {
    const { data } = await api.get(
      `/render/projects/${projectId}/timeline/revisions/${revisionId}/patch-preview`
    )
    return data
  },

  async patchSteps(projectId: string, revisionId: string): Promise<TimelinePatchStepsResult> {
    const { data } = await api.get(
      `/render/projects/${projectId}/timeline/revisions/${revisionId}/patch-steps`
    )
    return data
  },

  async revisionSnapshot(
    projectId: string,
    revisionId: string
  ): Promise<TimelineRevisionSnapshotResponse> {
    const { data } = await api.get(
      `/render/projects/${projectId}/timeline/revisions/${revisionId}/snapshot`
    )
    return data
  },
}
