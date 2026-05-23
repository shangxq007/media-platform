import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PendingTimelineConflict } from '@/utils/timelineConflictMerge'

const OFFLINE_PREFIX = 'timeline-offline-draft:'

export interface TimelineBaseline {
  projectId: string
  snapshotId: string | null
  headRevisionId: string | null
  serverRevision: number
  contentHash: string
  editorJson: string
  syncedAt: string
}

export interface OfflineTimelineDraft {
  projectId: string
  editorJson: string
  contentHash: string
  baselineHash: string | null
  serverRevision: number
  snapshotId: string | null
  savedAt: string
}

export const useTimelineSyncMetaStore = defineStore('timelineSyncMeta', () => {
  const baselines = ref<Record<string, TimelineBaseline>>({})
  const pendingConflict = ref<PendingTimelineConflict | null>(null)
  const lastFastForwardAt = ref<string | null>(null)
  const activeEditSessionId = ref<string | null>(null)
  const highlightedRevisionIds = ref<string[]>([])

  function getBaseline(projectId: string): TimelineBaseline | null {
    return baselines.value[projectId] ?? null
  }

  function setBaseline(meta: TimelineBaseline) {
    baselines.value[meta.projectId] = meta
  }

  function clearBaseline(projectId: string) {
    delete baselines.value[projectId]
  }

  function setPendingConflict(conflict: PendingTimelineConflict | null) {
    pendingConflict.value = conflict
  }

  function saveOfflineDraft(draft: OfflineTimelineDraft) {
    try {
      localStorage.setItem(OFFLINE_PREFIX + draft.projectId, JSON.stringify(draft))
    } catch {
      /* quota or private mode */
    }
  }

  function loadOfflineDraft(projectId: string): OfflineTimelineDraft | null {
    try {
      const raw = localStorage.getItem(OFFLINE_PREFIX + projectId)
      if (!raw) {
        return null
      }
      return JSON.parse(raw) as OfflineTimelineDraft
    } catch {
      return null
    }
  }

  function clearOfflineDraft(projectId: string) {
    try {
      localStorage.removeItem(OFFLINE_PREFIX + projectId)
    } catch {
      /* ignore */
    }
  }

  function noteFastForward() {
    lastFastForwardAt.value = new Date().toISOString()
  }

  function setActiveEditSessionId(sessionId: string | null) {
    activeEditSessionId.value = sessionId
  }

  function setHighlightedRevisionIds(ids: string[]) {
    highlightedRevisionIds.value = ids.filter(Boolean)
  }

  function clearHighlightedRevisionIds() {
    highlightedRevisionIds.value = []
  }

  return {
    baselines,
    pendingConflict,
    lastFastForwardAt,
    activeEditSessionId,
    highlightedRevisionIds,
    setActiveEditSessionId,
    setHighlightedRevisionIds,
    clearHighlightedRevisionIds,
    getBaseline,
    setBaseline,
    clearBaseline,
    setPendingConflict,
    saveOfflineDraft,
    loadOfflineDraft,
    clearOfflineDraft,
    noteFastForward,
  }
})
