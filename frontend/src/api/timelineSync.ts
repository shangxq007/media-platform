import api from './index'

export interface TimelineSyncPushResponse {
  internalTimelineJson: string
  sourceSchema: string
  alreadyInternal: boolean
  snapshotId: string | null
  sourceTrackOrLayerCount: number
  internalTrackOrLayerCount: number
  sourceClipCount: number
  internalClipCount: number
  targetRevision: number
  jsonByteDelta: number
}

export interface TimelineSyncPullResponse {
  editorTimelineJson: string
  internalTimelineJson: string
  snapshotId: string
  projectId: string
  storedSchemaVersion: string
  editorSchema: string
  resolvedSourceSchema: string
  sourceTrackOrLayerCount: number
  internalTrackOrLayerCount: number
  sourceClipCount: number
  internalClipCount: number
  targetRevision: number
  headRevisionId: string | null
  headRevisionNumber: number
  headParentRevisionId: string | null
}

export interface TimelineSyncSyncResponse {
  editorTimelineJson: string
  internalTimelineJson: string
  snapshotId: string
  sourceSchema: string
  internalClipCount: number
  targetRevision: number
  revisionId: string | null
  revisionNumber: number
  parentRevisionId: string | null
}

export const TimelineSyncAPI = {
  async push(
    projectId: string,
    timelineJson: string,
    persistSnapshot = false
  ): Promise<TimelineSyncPushResponse> {
    const { data } = await api.post('/render/timeline-sync/push', {
      projectId,
      timelineJson,
      persistSnapshot
    })
    return data
  },

  async pullByProject(projectId: string): Promise<TimelineSyncPullResponse> {
    const { data } = await api.get('/render/timeline-sync/latest', { params: { projectId } })
    return data
  },

  async pullBySnapshot(snapshotId: string): Promise<TimelineSyncPullResponse> {
    const { data } = await api.post('/render/timeline-sync/pull', { snapshotId })
    return data
  },

  async sync(
    projectId: string,
    timelineJson: string,
    options?: import('./timelineRevision').TimelineSyncOptions
  ): Promise<TimelineSyncSyncResponse> {
    const { data } = await api.post('/render/timeline-sync/sync', {
      projectId,
      timelineJson,
      authorUserId: options?.authorUserId,
      editSessionId: options?.editSessionId,
      message: options?.message,
      source: options?.source,
    })
    return data
  },

  async saveSnapshotInternal(
    projectId: string,
    editorTimeline: unknown
  ): Promise<{ snapshotId: string; projectId: string }> {
    const { data } = await api.post('/render/timeline-snapshots', {
      projectId,
      editorTimeline,
      schemaVersion: '2.0.0',
      ensureInternal: true
    })
    return data
  }
}
