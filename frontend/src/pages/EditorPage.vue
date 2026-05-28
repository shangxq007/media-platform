<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ProjectAPI } from '@/api'
import { useExportUiStore } from '@/stores/exportUi'
import TimelineEditor from '@/components/timeline/TimelineEditor.vue'
import TimelineConflictDialog from '@/components/timeline/TimelineConflictDialog.vue'
import TimelineConflictBanner from '@/components/timeline/TimelineConflictBanner.vue'
import TimelineHistoryPanel from '@/components/timeline/TimelineHistoryPanel.vue'
import TimelineHighlightNavigator from '@/components/timeline/TimelineHighlightNavigator.vue'
import ClipLibrary from '@/components/clip-library/ClipLibrary.vue'
import ExportPanel from '@/components/export/ExportPanel.vue'
import EffectsPanel from '@/components/effects/EffectsPanel.vue'
import SubtitlesPanel from '@/components/subtitles/SubtitlesPanel.vue'
import PropertiesPanel from '@/components/editor/PropertiesPanel.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { useSubtitleStore } from '@/stores/subtitle'
import { useHistoryStore } from '@/stores/history'
import { createDemoProject } from '@/utils/demoProjectFactory'
import { usePlayback } from '@/composables/usePlayback'
import { useSaveProject } from '@/composables/useSaveProject'
import { useTimelineSync } from '@/composables/useTimelineSync'
import { useTimelineSyncMetaStore } from '@/stores/timelineSyncMeta'
import { isDemoProjectId } from '@/utils/timelineImport'
import { loadConflictClipHighlights } from '@/utils/timelinePatchHighlight'
import { useEditorFeatureFlags } from '@/composables/useFeatureFlag'
import { useEditorTimelineLifecycle } from '@/composables/useEditorTimelineLifecycle'
import AppIcon from '@/components/ui/AppIcon.vue'
import ProgramMonitor from '@/components/editor/ProgramMonitor.vue'
import { buildEditorTimelineJson } from '@/utils/timelineExport'

const route = useRoute()
const exportUi = useExportUiStore()
const projectStore = useProjectStore()
const timelineStore = useTimelineStore()
const subtitleStore = useSubtitleStore()
const historyStore = useHistoryStore()
const metaStore = useTimelineSyncMetaStore()

const {
  isEnabled: isEditorFlagEnabled,
} = useEditorFeatureFlags()

const showDemoToast = ref(false)

const leftPanelCollapsed = ref(false)
const leftPanelWidth = ref(280)
const rightPanelWidth = ref(300)
const activeRightTab = ref<'effects' | 'subtitles' | 'export' | 'properties' | 'history'>('effects')
const zoomLevel = ref(100)
const isMobile = ref(false)

const totalDuration = computed(() => timelineStore.state.duration)

const {
  isPlaying,
  currentTime,
  togglePlayback,
  stepForward,
  stepBackward,
} = usePlayback(() => totalDuration.value)

const {
  isSaving,
  isDirty,
  saveError,
  saveProject,
  markDirty,
  clearDirty,
  getSaveStatusText,
} = useSaveProject()

const {
  isSyncing,
  isPulling,
  syncError,
  fastForwardNotice,
  pendingConflict,
  pullTimeline,
  syncTimeline,
  resolveConflict,
  dismissConflict,
  persistOfflineDraft,
  tryRestoreOfflineDraft,
  applyEditorTimeline,
} = useTimelineSync({
  isDirty: () => isDirty.value,
  markDirty,
  clearDirty,
})

let offlineSaveTimer: ReturnType<typeof setTimeout> | null = null

useEditorTimelineLifecycle({
  isDirty: () => isDirty.value,
  hasPendingConflict: () => !!pendingConflict.value,
  onReconnect: () => {
    const pid = projectStore.currentProject?.id
    if (pid && !isDemoProjectId(pid)) {
      void pullTimeline(pid)
    }
  },
})

const saveStatusText = computed(() => {
  if (isSaving.value || isSyncing.value) return isSyncing.value ? 'Syncing timeline...' : 'Saving...'
  if (isPulling.value) return 'Loading timeline...'
  if (saveError.value) return `Save failed: ${saveError.value}`
  if (syncError.value) return `Timeline sync failed: ${syncError.value}`
  if (fastForwardNotice.value) return fastForwardNotice.value
  if (pendingConflict.value) return '同步冲突 — 已打开 History，琥珀色高亮为受影响片段'
  return getSaveStatusText()
})

const saveStatusColor = computed(() => {
  if (isSaving.value || isSyncing.value || isPulling.value) return 'text-warning-500'
  if (saveError.value || syncError.value) return 'text-danger-500'
  if (isDirty.value) return 'text-text-muted'
  return 'text-success-500'
})

function checkMobile() {
  isMobile.value = window.innerWidth <= 640
  if (isMobile.value) {
    leftPanelCollapsed.value = true
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const showMigrationBanner = computed(() => {
  const json = timelineStore.toJSON()
  const version = String(json.schemaVersion ?? '1.0.0')
  return version.startsWith('1.')
})

const hasTracks = computed(() => timelineStore.state.tracks.length > 0)

const programMonitorJson = computed(() =>
  buildEditorTimelineJson(
    timelineStore.toJSON() as Record<string, unknown>,
    timelineStore.clips
  )
)

const currentClipName = computed(() => {
  const sel = timelineStore.selectedTrackClip
  if (!sel) return null
  const clip = timelineStore.clips.find((c: any) => c.id === sel.trackClip.clipId)
  return clip?.name || null
})

const showTryDemoButton = computed(() => isEditorFlagEnabled('editor.demoProject.enabled'))
const useNewTimeline = computed(() => isEditorFlagEnabled('editor.newTimeline.enabled'))
const useSubtitlePanelV2 = computed(() => isEditorFlagEnabled('editor.subtitlePanel.v2'))
const useEffectChainV2 = computed(() => isEditorFlagEnabled('editor.effectChain.v2'))

const rightTabs = computed(() => [
  { key: 'effects' as const, label: 'Effects', icon: 'sparkles', beta: useEffectChainV2.value },
  { key: 'subtitles' as const, label: 'Subtitles', icon: 'file-text', beta: useSubtitlePanelV2.value },
  { key: 'export' as const, label: 'Export', icon: 'upload', beta: false },
  { key: 'history' as const, label: 'History', icon: 'history', beta: false },
  { key: 'properties' as const, label: 'Properties', icon: 'settings', beta: false },
])

function onTimelineRestored(payload: { editorTimelineJson: string }) {
  applyEditorTimeline(payload.editorTimelineJson)
  clearDirty()
  metaStore.setPendingConflict(null)
  timelineStore.clearPatchHighlightClipIds()
  metaStore.clearHighlightedRevisionIds()
}

function handleZoomIn() {
  zoomLevel.value = Math.min(200, zoomLevel.value + 25)
}

function handleZoomOut() {
  zoomLevel.value = Math.max(25, zoomLevel.value - 25)
}

function handleUndo() {
  historyStore.undo(timelineStore)
  markDirty()
}

function handleRedo() {
  historyStore.redo(timelineStore)
  markDirty()
}

async function handleSave() {
  await saveProject()
  const projectId = projectStore.currentProject?.id
  if (projectId && !isDemoProjectId(projectId)) {
    const synced = await syncTimeline(projectId, {
      editSessionId: metaStore.activeEditSessionId ?? undefined,
    })
    if (synced) {
      clearDirty()
    }
  }
}

async function loadProjectTimeline(projectId: string) {
  if (isDemoProjectId(projectId)) {
    return
  }
  const restored = tryRestoreOfflineDraft(projectId)
  await pullTimeline(projectId, { force: !restored })
}

function onConflictResolve(strategy: 'keep-local' | 'use-server' | 'merge') {
  timelineStore.clearPatchHighlightClipIds()
  void resolveConflict(strategy)
}

watch(pendingConflict, async c => {
  if (!c) {
    timelineStore.clearPatchHighlightClipIds()
    metaStore.clearHighlightedRevisionIds()
    return
  }
  activeRightTab.value = 'history'
  if (c.baselineRevisionId && c.headRevisionId && c.projectId) {
    await loadConflictClipHighlights(
      c.projectId,
      c.baselineRevisionId,
      c.headRevisionId,
      timelineStore,
      c.serverInternalTimelineJson
    )
  }
})

function handleExport() {
  activeRightTab.value = 'export'
}

function handleTryDemoProject() {
  const demoProject = createDemoProject()
  timelineStore.loadDemoProject(demoProject)
  if (demoProject.subtitleTracks.length > 0) {
    subtitleStore.tracks.push(...demoProject.subtitleTracks)
    if (!subtitleStore.activeTrackId) {
      subtitleStore.activeTrackId = demoProject.subtitleTracks[0].id
    }
  }
  projectStore.setProject({
    id: `demo_project_${Date.now()}`,
    name: demoProject.name,
    tenantId: projectStore.currentTenant,
    description: 'A demo project to explore the editor',
    status: 'active',
    createdAt: new Date().toISOString(),
  })
  showDemoToast.value = true
  markDirty()
  setTimeout(() => {
    showDemoToast.value = false
  }, 3000)
}

function scheduleOfflineDraftSave() {
  const pid = projectStore.currentProject?.id
  if (!pid || isDemoProjectId(pid)) {
    return
  }
  markDirty()
  if (offlineSaveTimer) {
    clearTimeout(offlineSaveTimer)
  }
  offlineSaveTimer = setTimeout(() => persistOfflineDraft(pid), 800)
}

watch(() => timelineStore.state.tracks, scheduleOfflineDraftSave, { deep: true })
watch(() => timelineStore.clips, scheduleOfflineDraftSave, { deep: true })

watch(currentTime, (t) => {
  timelineStore.setCurrentTime(t)
})

async function applyRouteExportIntent() {
  if (route.query.export === 'incremental') {
    exportUi.mode = 'incremental'
    activeRightTab.value = 'export'
  }
  const baseJobId = typeof route.query.baseJobId === 'string' ? route.query.baseJobId : ''
  if (baseJobId) {
    exportUi.baseJobId = baseJobId
  }
  const projectId =
    (typeof route.query.projectId === 'string' && route.query.projectId) ||
    exportUi.pendingProjectId ||
    null
  if (projectId) {
    try {
      const project = await ProjectAPI.get(projectId)
      projectStore.setProject(project)
      if (!isDemoProjectId(project.id)) {
        await loadProjectTimeline(project.id)
      }
    } catch {
      /* 项目不存在时保留当前上下文 */
    }
    exportUi.clearPendingProject()
  }
  if (exportUi.consumeOpenExportTab()) {
    activeRightTab.value = 'export'
  }
}

watch(() => route.query, () => { void applyRouteExportIntent() }, { immediate: true })

watch(
  () => projectStore.currentProject?.id,
  (id, prev) => {
    if (id && id !== prev && !isDemoProjectId(id)) {
      void loadProjectTimeline(id)
    }
  }
)

onMounted(() => {
  const pid = projectStore.currentProject?.id
  if (pid && !isDemoProjectId(pid)) {
    void loadProjectTimeline(pid)
  }
  if (timelineStore.state.tracks.length === 0) {
    timelineStore.addTrack('Video 1', 'video')
    timelineStore.addTrack('Audio 1', 'audio')
    timelineStore.addTrack('Text 1', 'text')
  }
  void applyRouteExportIntent()
})
</script>

<template>
  <TimelineConflictDialog
    :open="!!pendingConflict"
    :conflict="pendingConflict"
    :project-id="projectStore.currentProject?.id"
    @resolve="onConflictResolve"
    @dismiss="dismissConflict"
  />
  <div class="h-full flex flex-col editor-shell">
    <TimelineConflictBanner :conflict="pendingConflict" />
    <header class="editor-toolbar flex-shrink-0">
      <div class="editor-toolbar-group min-w-0">
        <router-link to="/me" class="editor-icon-btn no-underline" title="Back to Dashboard">
          <AppIcon name="chevron-left" :size="18" />
        </router-link>
        <div class="editor-toolbar-divider" />
        <AppIcon name="clapperboard" :size="18" class="text-primary-400 hidden sm:block" />
        <span class="text-sm font-semibold text-text-primary truncate-text max-w-[12rem]">
          {{ projectStore.currentProject?.name || 'Untitled Project' }}
        </span>
        <span class="text-xs px-2 py-0.5 rounded-full bg-bg-base border border-default" :class="saveStatusColor">
          {{ saveStatusText }}
        </span>
      </div>
      <div class="editor-toolbar-group">
        <span class="hidden md:flex items-center gap-3 text-[10px] text-text-muted mr-sm">
          <span class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-danger-500" /> CPU</span>
          <span class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-text-muted" /> Worker</span>
          <span class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-success-500" /> Live</span>
        </span>
        <div class="editor-toolbar-divider hidden md:block" />
        <button type="button" class="editor-icon-btn" title="Undo" @click="handleUndo">
          <AppIcon name="undo-2" :size="18" />
        </button>
        <button type="button" class="editor-icon-btn" title="Redo" @click="handleRedo">
          <AppIcon name="redo-2" :size="18" />
        </button>
        <button
          v-if="showTryDemoButton"
          type="button"
          class="theme-btn theme-btn-ghost theme-btn-sm hidden sm:inline-flex"
          @click="handleTryDemoProject"
        >
          <AppIcon name="wand-2" :size="16" class="mr-1" />
          Demo
        </button>
        <button type="button" class="theme-btn theme-btn-secondary theme-btn-sm" @click="handleSave">
          <AppIcon name="save" :size="16" class="mr-1" />
          Save
        </button>
        <button type="button" class="theme-btn theme-btn-primary theme-btn-sm" @click="handleExport">
          <AppIcon name="upload" :size="16" class="mr-1" />
          Export
        </button>
      </div>
    </header>
    <div class="flex-1 overflow-hidden">
    <div class="h-full flex flex-col">
      <div class="flex-1 flex overflow-hidden mobile-stack">
        <aside
          v-if="!leftPanelCollapsed"
          class="editor-panel flex-shrink-0 border-r"
          :style="{ width: isMobile ? '100%' : `${leftPanelWidth}px` }"
        >
          <div class="editor-panel-header">
            <span class="flex items-center gap-2">
              <AppIcon name="folder-open" :size="14" />
              Media
            </span>
            <button type="button" class="editor-icon-btn" aria-label="Collapse clip library" @click="leftPanelCollapsed = true">
              <AppIcon name="panel-left-close" :size="16" />
            </button>
          </div>
          <div class="flex-1 overflow-y-auto theme-scrollbar">
             <ClipLibrary
               @try-demo="handleTryDemoProject"
               @import-subtitle="activeRightTab = 'subtitles'"
             />
           </div>
        </aside>

        <button
          v-else-if="!isMobile"
          type="button"
          class="editor-panel flex-shrink-0 w-9 border-r items-center justify-center"
          aria-label="Expand clip library"
          @click="leftPanelCollapsed = false"
        >
          <AppIcon name="panel-left-open" :size="16" class="text-text-muted" />
        </button>

        <div class="flex-1 flex flex-col overflow-hidden min-w-0">
          <div class="flex-1 flex items-center justify-center editor-preview-stage relative overflow-hidden">
            <div v-if="hasTracks" class="w-full h-full flex items-center justify-center p-md">
              <div class="w-full max-w-3xl aspect-video bg-black editor-preview-frame flex flex-col">
                <div class="flex-1 flex items-center justify-center relative min-h-0">
                  <ProgramMonitor
                    :timeline-json="programMonitorJson"
                    :current-time="currentTime"
                    :is-playing="isPlaying"
                    :watermark="true"
                    class="absolute inset-0"
                  />
                  <div class="absolute bottom-2 left-2 right-2 flex items-center justify-between pointer-events-none">
                    <span class="text-[10px] text-text-muted font-mono bg-black/60 px-1.5 py-0.5 rounded" aria-live="polite">
                      {{ currentTime.toFixed(1) }}s
                    </span>
                    <span v-if="currentClipName" class="text-[10px] text-primary-400 bg-black/60 px-1.5 py-0.5 rounded truncate max-w-[40%]">
                      {{ currentClipName }}
                    </span>
                    <span class="text-[10px] text-text-muted font-mono bg-black/60 px-1.5 py-0.5 rounded">
                      {{ totalDuration.toFixed(1) }}s
                    </span>
                  </div>
                </div>
                <div class="h-9 flex items-center justify-center gap-1 border-t border-white/10 bg-black/40">
                  <button type="button" class="editor-icon-btn" @click="stepBackward(1)" aria-label="Step backward">
                    <AppIcon name="skip-back" :size="16" />
                  </button>
                  <button type="button" class="editor-icon-btn is-primary mx-1" @click="togglePlayback" :aria-label="isPlaying ? 'Pause' : 'Play'">
                    <AppIcon :name="isPlaying ? 'pause' : 'play'" :size="18" />
                  </button>
                  <button type="button" class="editor-icon-btn" @click="stepForward(1)" aria-label="Step forward">
                    <AppIcon name="skip-forward" :size="16" />
                  </button>
                </div>
              </div>
            </div>
            <EmptyState v-else title="No tracks to preview" description="Add tracks and clips to see a preview">
              <template #icon>
                <AppIcon name="film" :size="40" class="text-text-muted opacity-50" />
              </template>
            </EmptyState>
          </div>

          <div class="editor-transport flex-shrink-0 justify-between">
            <div class="flex items-center gap-xs">
              <button type="button" class="editor-icon-btn" @click="stepBackward(5)" aria-label="Skip backward 5s">
                <AppIcon name="skip-back" :size="18" />
              </button>
              <button type="button" class="editor-icon-btn is-primary" @click="togglePlayback" :aria-label="isPlaying ? 'Pause' : 'Play'">
                <AppIcon :name="isPlaying ? 'pause' : 'play'" :size="20" />
              </button>
              <button type="button" class="editor-icon-btn" @click="stepForward(5)" aria-label="Skip forward 5s">
                <AppIcon name="skip-forward" :size="18" />
              </button>
              <span class="text-xs text-text-muted font-mono" aria-live="polite">
                {{ currentTime.toFixed(1) }}s / {{ totalDuration.toFixed(1) }}s
              </span>
              <span v-if="currentClipName" class="text-[10px] text-primary-400 truncate max-w-32" aria-live="polite">
                {{ currentClipName }}
              </span>
            </div>
            <div class="flex items-center gap-xs">
              <button type="button" class="editor-icon-btn" aria-label="Zoom out" @click="handleZoomOut">
                <AppIcon name="zoom-out" :size="16" />
              </button>
              <span class="text-xs text-text-muted w-12 text-center font-mono tabular-nums">{{ zoomLevel }}%</span>
              <button type="button" class="editor-icon-btn" aria-label="Zoom in" @click="handleZoomIn">
                <AppIcon name="zoom-in" :size="16" />
              </button>
            </div>
          </div>
        </div>

        <aside
          v-if="!isMobile || !leftPanelCollapsed"
          class="editor-panel flex-shrink-0 border-l"
          :style="{ width: isMobile ? '100%' : `${rightPanelWidth}px` }"
        >
          <div class="editor-tab-strip flex-shrink-0" role="tablist" aria-label="Right panel tabs">
            <button
              v-for="tab in rightTabs"
              :key="tab.key"
              type="button"
              role="tab"
              :aria-selected="activeRightTab === tab.key"
              :aria-controls="`panel-${tab.key}`"
              class="editor-tab"
              :class="{ 'is-active': activeRightTab === tab.key }"
              @click="activeRightTab = tab.key"
            >
              <AppIcon :name="tab.icon" :size="16" />
              <span>{{ tab.label }}</span>
              <span v-if="tab.beta" class="absolute top-1 right-1 text-[8px] px-1 rounded bg-purple-500/25 text-accent-300 font-semibold">β</span>
            </button>
          </div>
          <div class="flex-1 overflow-y-auto theme-scrollbar">
            <div v-if="activeRightTab === 'effects'" id="panel-effects" role="tabpanel" aria-label="Effects panel">
              <EffectsPanel />
            </div>
            <div v-else-if="activeRightTab === 'subtitles'" id="panel-subtitles" role="tabpanel" aria-label="Subtitles panel">
              <SubtitlesPanel />
            </div>
            <div v-else-if="activeRightTab === 'export'" id="panel-export" role="tabpanel" aria-label="Export panel">
              <ExportPanel />
            </div>
            <div v-else-if="activeRightTab === 'history'" id="panel-history" role="tabpanel" aria-label="History panel" class="p-3">
              <TimelineHistoryPanel
                :project-id="projectStore.currentProject?.id"
                @restored="onTimelineRestored"
              />
            </div>
            <div v-else-if="activeRightTab === 'properties'" id="panel-properties" role="tabpanel" aria-label="Properties panel">
              <PropertiesPanel />
            </div>
          </div>
        </aside>
      </div>

      <div v-if="showMigrationBanner" class="bg-warning-500/10 border-t border-warning-500/30 px-md py-sm flex items-center justify-between flex-shrink-0">
        <span class="text-xs text-warning-600">alert-triangle Timeline schema v1 detected. Migration to v2 available.</span>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="activeRightTab = 'effects'">
          Review Migration
        </button>
      </div>

      <div
        v-if="showDemoToast"
        class="bg-success-500/10 border-t border-success-500/30 px-md py-sm flex items-center justify-between flex-shrink-0"
        role="status"
        aria-live="polite"
      >
        <span class="text-xs text-success-600">check Demo project loaded successfully! Explore the editor with sample clips and subtitles.</span>
        <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="showDemoToast = false" aria-label="Dismiss notification">
          ✕
        </button>
      </div>

      <div
        v-if="timelineStore.patchHighlightClipIds.length"
        class="flex-shrink-0 px-3 py-1 border-t border-default bg-bg-base/30"
      >
        <TimelineHighlightNavigator />
      </div>

      <div class="flex-shrink-0 border-t border-default" :style="{ height: isMobile ? '120px' : '200px' }">
        <TimelineEditor v-if="useNewTimeline" v-model:currentTime="currentTime" :enhanced="true" />
        <TimelineEditor v-else v-model:currentTime="currentTime" />
      </div>
    </div>
    </div>
  </div>
</template>
