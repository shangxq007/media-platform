import { computed, ref } from 'vue'
import { TimelineSyncAPI, type TimelineSyncPullResponse } from '@/api/timelineSync'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { useTimelineSyncMetaStore } from '@/stores/timelineSyncMeta'
import { buildEditorTimelineJson } from '@/utils/timelineExport'
import {
  buildCanonicalEditorJson,
  detectTimelineConflict,
  hashEditorPayload,
  mergeEditorTimelines,
  shouldFastForwardServer,
  type ConflictResolution,
  type PendingTimelineConflict,
} from '@/utils/timelineConflictMerge'
import {
  isDemoProjectId,
  parseEditorTimelinePayload,
  type EditorTimelinePayload,
} from '@/utils/timelineImport'
import { parseInternalRevision } from '@/utils/timelineSyncHash'
import { getErrorMessage } from '@/utils/i18n'

export interface UseTimelineSyncDeps {
  isDirty: () => boolean
  markDirty: () => void
  clearDirty: () => void
}

const noop = () => {}

function syncAuthorUserId(): string {
  return localStorage.getItem('user_id') || 'user-1'
}

export function useTimelineSync(deps?: UseTimelineSyncDeps) {
  const resolved: UseTimelineSyncDeps = deps ?? {
    isDirty: () => false,
    markDirty: noop,
    clearDirty: noop,
  }
  const projectStore = useProjectStore()
  const timelineStore = useTimelineStore()
  const metaStore = useTimelineSyncMetaStore()

  const isSyncing = ref(false)
  const isPulling = ref(false)
  const syncError = ref<string | null>(null)
  const lastSyncedAt = ref<Date | null>(null)
  const lastPulledAt = ref<Date | null>(null)
  const fastForwardNotice = ref<string | null>(null)

  const pendingConflict = computed(() => metaStore.pendingConflict)

  function currentProjectId(): string | null {
    return projectStore.currentProject?.id ?? null
  }

  function buildLocalPayload(): EditorTimelinePayload {
    return {
      ...(timelineStore.toJSON() as Record<string, unknown>),
      clips: [...timelineStore.clips],
      schemaVersion: '2.0.0',
    } as EditorTimelinePayload
  }

  function applyEditorTimeline(editorJson: string) {
    const { state, clips } = parseEditorTimelinePayload(editorJson)
    timelineStore.loadFromJSON(state)
    timelineStore.clips.splice(0, timelineStore.clips.length, ...clips)
  }

  function updateBaselineFromPull(projectId: string, pull: TimelineSyncPullResponse, editorJson: string) {
    const payload = parseEditorTimelinePayload(editorJson)
    metaStore.setBaseline({
      projectId,
      snapshotId: pull.snapshotId,
      serverRevision: pull.headRevisionNumber || pull.targetRevision || parseInternalRevision(pull.internalTimelineJson),
      contentHash: hashEditorPayload(payload),
      editorJson: buildCanonicalEditorJson(payload),
      syncedAt: new Date().toISOString(),
      headRevisionId: pull.headRevisionId ?? null,
    })
    metaStore.clearOfflineDraft(projectId)
  }

  function persistOfflineDraft(projectId?: string) {
    const id = projectId ?? currentProjectId()
    if (!id || isDemoProjectId(id)) {
      return
    }
    const payload = buildLocalPayload()
    const baseline = metaStore.getBaseline(id)
    metaStore.saveOfflineDraft({
      projectId: id,
      editorJson: buildCanonicalEditorJson(payload),
      contentHash: hashEditorPayload(payload),
      baselineHash: baseline?.contentHash ?? null,
      serverRevision: baseline?.serverRevision ?? 0,
      snapshotId: baseline?.snapshotId ?? null,
      savedAt: new Date().toISOString(),
    })
  }

  function tryRestoreOfflineDraft(projectId?: string): boolean {
    const id = projectId ?? currentProjectId()
    if (!id || isDemoProjectId(id)) {
      return false
    }
    const draft = metaStore.loadOfflineDraft(id)
    if (!draft?.editorJson) {
      return false
    }
    applyEditorTimeline(draft.editorJson)
    resolved.markDirty()
    return true
  }

  async function pullTimeline(projectId?: string, options?: { force?: boolean; skipConflictUi?: boolean }) {
    const id = projectId ?? currentProjectId()
    if (!id || isDemoProjectId(id)) {
      return { status: 'skipped' as const }
    }
    if (options?.force !== true && isPulling.value) {
      return { status: 'skipped' as const }
    }

    isPulling.value = true
    syncError.value = null
    fastForwardNotice.value = null
    try {
      const pull = await TimelineSyncAPI.pullByProject(id)
      const serverPayload = parseEditorTimelinePayload(pull.editorTimelineJson)
      const serverHash = hashEditorPayload(serverPayload)
      const localPayload = buildLocalPayload()
      const localHash = hashEditorPayload(localPayload)
      const baseline = metaStore.getBaseline(id)
      const baselineHash = baseline?.contentHash ?? null
      const dirty = resolved.isDirty()

      if (shouldFastForwardServer(dirty, baselineHash, localHash, serverHash)) {
        applyEditorTimeline(pull.editorTimelineJson)
        updateBaselineFromPull(id, pull, pull.editorTimelineJson)
        resolved.clearDirty()
        lastPulledAt.value = new Date()
        metaStore.noteFastForward()
        fastForwardNotice.value = 'Timeline updated from server (no local clip changes).'
        return { status: 'fast-forward' as const }
      }

      const conflict = detectTimelineConflict(dirty, baselineHash, localHash, serverHash)
      if (conflict && !options?.skipConflictUi) {
        const pending: PendingTimelineConflict = {
          projectId: id,
          snapshotId: pull.snapshotId,
          serverRevision: pull.headRevisionNumber || pull.targetRevision || parseInternalRevision(pull.internalTimelineJson),
          headRevisionId: pull.headRevisionId ?? null,
          headRevisionNumber: pull.headRevisionNumber || null,
          baselineRevisionNumber: baseline?.serverRevision ?? null,
          baselineRevisionId: baseline?.headRevisionId ?? null,
          localTrackCount: localPayload.tracks?.length ?? 0,
          serverTrackCount: serverPayload.tracks?.length ?? 0,
          localClipCount: countClips(localPayload),
          serverClipCount: countClips(serverPayload),
          baselineHash,
          localHash,
          serverHash,
          serverEditorJson: buildCanonicalEditorJson(serverPayload),
          serverInternalTimelineJson: pull.internalTimelineJson ?? null,
          localEditorJson: buildCanonicalEditorJson(localPayload),
          baselineEditorJson: baseline?.editorJson ?? null,
        }
        metaStore.setPendingConflict(pending)
        const revIds = [pending.baselineRevisionId, pending.headRevisionId].filter(
          (id): id is string => !!id
        )
        metaStore.setHighlightedRevisionIds(revIds)
        return { status: 'conflict' as const }
      }

      if (!dirty || options?.force) {
        applyEditorTimeline(pull.editorTimelineJson)
        updateBaselineFromPull(id, pull, pull.editorTimelineJson)
        if (!dirty) {
          resolved.clearDirty()
        }
      }

      lastPulledAt.value = new Date()
      return { status: 'ok' as const }
    } catch (err: any) {
      const code = err.response?.data?.errorCode
      if (code && String(code).includes('404')) {
        return { status: 'empty' as const }
      }
      const message = code ? getErrorMessage(code) : err.message || 'Failed to load timeline'
      syncError.value = code ? `${code}: ${message}` : message
      return { status: 'error' as const }
    } finally {
      isPulling.value = false
    }
  }

  async function resolveConflict(strategy: ConflictResolution) {
    const conflict = metaStore.pendingConflict
    if (!conflict) {
      return
    }

    const projectId = conflict.projectId
    try {
      if (strategy === 'keep-local') {
        metaStore.clearHighlightedRevisionIds()
        metaStore.setPendingConflict(null)
        const synced = await syncTimeline(projectId, {
          source: 'conflict-keep-local',
          message: `Kept local over server rev #${conflict.headRevisionNumber ?? conflict.serverRevision}`,
        })
        if (synced) {
          resolved.clearDirty()
          metaStore.clearOfflineDraft(projectId)
        }
        return
      }

      if (strategy === 'use-server') {
        applyEditorTimeline(conflict.serverEditorJson)
        const pull = await TimelineSyncAPI.pullByProject(projectId)
        updateBaselineFromPull(projectId, pull, conflict.serverEditorJson)
        resolved.clearDirty()
        metaStore.setPendingConflict(null)
        lastPulledAt.value = new Date()
        return
      }

      if (strategy === 'merge') {
        const local = parseEditorTimelinePayload(conflict.localEditorJson)
        const remote = parseEditorTimelinePayload(conflict.serverEditorJson)
        const base = conflict.baselineEditorJson
          ? parseEditorTimelinePayload(conflict.baselineEditorJson)
          : remote
        const merged = mergeEditorTimelines(base, local, remote)
        timelineStore.loadFromJSON(merged.state)
        timelineStore.clips.splice(0, timelineStore.clips.length, ...merged.clips)
        metaStore.clearHighlightedRevisionIds()
        metaStore.setPendingConflict(null)
        const synced = await syncTimeline(projectId, {
          source: 'conflict-merge',
          message: `Three-way merge vs server rev #${conflict.headRevisionNumber ?? conflict.serverRevision}`,
        })
        if (synced) {
          resolved.clearDirty()
          metaStore.clearOfflineDraft(projectId)
        } else {
          resolved.markDirty()
          persistOfflineDraft(projectId)
        }
        return
      }
    } catch (err: any) {
      const code = err.response?.data?.errorCode || 'COMMON-500-001'
      syncError.value = `${code}: ${getErrorMessage(code)}`
    }
  }

  function dismissConflict() {
    metaStore.setPendingConflict(null)
  }

  async function syncTimeline(
    projectId?: string,
    options?: { editSessionId?: string; message?: string; source?: string }
  ) {
    const id = projectId ?? currentProjectId()
    if (!id || isDemoProjectId(id)) {
      return null
    }

    isSyncing.value = true
    syncError.value = null
    try {
      const timelineJson = buildEditorTimelineJson(
        timelineStore.toJSON() as Record<string, unknown>,
        timelineStore.clips
      )
      const result = await TimelineSyncAPI.sync(id, timelineJson, {
        authorUserId: syncAuthorUserId(),
        editSessionId: options?.editSessionId,
        message: options?.message,
        source: options?.source,
      })
      applyEditorTimeline(result.editorTimelineJson)
      const pull: TimelineSyncPullResponse = {
        editorTimelineJson: result.editorTimelineJson,
        internalTimelineJson: result.internalTimelineJson,
        snapshotId: result.snapshotId,
        projectId: id,
        storedSchemaVersion: 'internal-1.0',
        editorSchema: 'editor-2.0.0',
        resolvedSourceSchema: result.sourceSchema,
        sourceTrackOrLayerCount: 0,
        internalTrackOrLayerCount: 0,
        sourceClipCount: 0,
        internalClipCount: result.internalClipCount,
        targetRevision: result.targetRevision,
        headRevisionId: result.revisionId ?? null,
        headRevisionNumber: result.revisionNumber ?? 0,
        headParentRevisionId: result.parentRevisionId ?? null,
      }
      updateBaselineFromPull(id, pull, result.editorTimelineJson)
      resolved.clearDirty()
      lastSyncedAt.value = new Date()
      metaStore.setPendingConflict(null)
      metaStore.clearHighlightedRevisionIds()
      return result
    } catch (err: any) {
      const code = err.response?.data?.errorCode || 'COMMON-500-001'
      syncError.value = `${code}: ${getErrorMessage(code)}`
      return null
    } finally {
      isSyncing.value = false
    }
  }

  return {
    isSyncing,
    isPulling,
    syncError,
    lastSyncedAt,
    lastPulledAt,
    fastForwardNotice,
    pendingConflict,
    pullTimeline,
    syncTimeline,
    resolveConflict,
    dismissConflict,
    persistOfflineDraft,
    tryRestoreOfflineDraft,
    applyEditorTimeline,
  }
}

function countClips(payload: EditorTimelinePayload): number {
  const onTracks = (payload.tracks ?? []).reduce((n, t) => n + (t.clips?.length ?? 0), 0)
  return Math.max(onTracks, payload.clips?.length ?? 0)
}
