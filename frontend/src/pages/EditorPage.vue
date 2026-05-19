<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import TimelineEditor from '@/components/timeline/TimelineEditor.vue'
import ClipLibrary from '@/components/clip-library/ClipLibrary.vue'
import ExportPanel from '@/components/export/ExportPanel.vue'
import EffectsPanel from '@/components/effects/EffectsPanel.vue'
import SubtitlesPanel from '@/components/subtitles/SubtitlesPanel.vue'
import PropertiesPanel from '@/components/editor/PropertiesPanel.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import EmptyProjectGuide from '@/components/editor/EmptyProjectGuide.vue'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { useSubtitleStore } from '@/stores/subtitle'
import { useHistoryStore } from '@/stores/history'
import { createDemoProject } from '@/utils/demoProjectFactory'
import { usePlayback } from '@/composables/usePlayback'
import { useSaveProject } from '@/composables/useSaveProject'
import { useEditorFeatureFlags } from '@/composables/useFeatureFlag'

const projectStore = useProjectStore()
const timelineStore = useTimelineStore()
const subtitleStore = useSubtitleStore()
const historyStore = useHistoryStore()

const {
  isEnabled: isEditorFlagEnabled,
} = useEditorFeatureFlags()

const showDemoToast = ref(false)

const leftPanelCollapsed = ref(false)
const leftPanelWidth = ref(280)
const rightPanelWidth = ref(300)
const activeRightTab = ref<'effects' | 'subtitles' | 'export' | 'properties'>('effects')
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
  getSaveStatusText,
} = useSaveProject()

const saveStatusText = computed(() => {
  if (isSaving.value) return 'Saving...'
  if (saveError.value) return `Save failed: ${saveError.value}`
  return getSaveStatusText()
})

const saveStatusColor = computed(() => {
  if (isSaving.value) return 'text-warning-500'
  if (saveError.value) return 'text-danger-500'
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
  const version = json.schemaVersion || '1.0.0'
  return version.startsWith('1.')
})

const hasClips = computed(() => timelineStore.clips.length > 0)
const hasTracks = computed(() => timelineStore.state.tracks.length > 0)

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

const rightTabs = computed(() => {
  const tabs = [
    { key: 'effects' as const, label: 'Effects', icon: '✨', beta: useEffectChainV2.value },
    { key: 'subtitles' as const, label: 'Subtitles', icon: '📝', beta: useSubtitlePanelV2.value },
    { key: 'export' as const, label: 'Export', icon: '📤', beta: false },
    { key: 'properties' as const, label: 'Properties', icon: '⚙️', beta: false },
  ]
  return tabs
})

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

function handleSave() {
  saveProject()
}

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

watch(() => timelineStore.state.tracks, () => {
  markDirty()
}, { deep: true })

watch(currentTime, (t) => {
  timelineStore.setCurrentTime(t)
})

onMounted(() => {
  projectStore.setTenant('tenant-1')
  if (timelineStore.state.tracks.length === 0) {
    timelineStore.addTrack('Video 1', 'video')
    timelineStore.addTrack('Audio 1', 'audio')
    timelineStore.addTrack('Text 1', 'text')
  }
})
</script>

<template>
  <div class="h-full flex flex-col bg-bg-base">
    <header class="h-12 flex items-center justify-between px-md border-b border-default bg-bg-surface flex-shrink-0">
      <div class="flex items-center gap-md">
        <router-link to="/" class="text-text-secondary hover:text-text-primary transition-colors">
          <span class="text-sm">←</span>
        </router-link>
        <span class="text-sm font-medium text-text-primary truncate-text max-w-48">{{ projectStore.currentProject?.name || 'Untitled Project' }}</span>
        <span class="text-xs" :class="saveStatusColor">{{ saveStatusText }}</span>
      </div>
      <div class="flex items-center gap-sm">
        <div class="flex items-center gap-xs mr-md">
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full bg-danger-500"></span> CPU
          </span>
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full bg-text-muted"></span> Worker
          </span>
          <span class="flex items-center gap-xs text-xs text-text-muted">
            <span class="w-2 h-2 rounded-full bg-success-500"></span> Connected
          </span>
        </div>
        <button class="theme-btn theme-btn-ghost theme-btn-sm" title="Undo" @click="handleUndo">↩️</button>
        <button class="theme-btn theme-btn-ghost theme-btn-sm" title="Redo" @click="handleRedo">↪️</button>
        <button v-if="showTryDemoButton" class="theme-btn theme-btn-ghost theme-btn-sm" title="Try Demo Project" @click="handleTryDemoProject">
          🎬 Demo
        </button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="handleSave">💾 Save</button>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="handleExport">📤 Export</button>
      </div>
    </header>
    <div class="flex-1 overflow-hidden">
    <div class="h-full flex flex-col">
      <div class="flex-1 flex overflow-hidden mobile-stack">
        <aside
          v-if="!leftPanelCollapsed"
          class="flex-shrink-0 border-r border-default bg-bg-surface flex flex-col"
          :style="{ width: isMobile ? '100%' : `${leftPanelWidth}px` }"
        >
          <div class="flex items-center justify-between px-md py-sm border-b border-default flex-shrink-0">
            <span class="text-xs font-medium text-text-secondary">Clip Library</span>
            <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="leftPanelCollapsed = true" aria-label="Collapse clip library">
              ◀
            </button>
          </div>
          <div class="flex-1 overflow-y-auto theme-scrollbar">
             <ClipLibrary v-if="hasClips" @try-demo="handleTryDemoProject" />
             <EmptyProjectGuide
               v-else
               @upload="() => {}"
               @try-demo="handleTryDemoProject"
               @import-subtitle="activeRightTab = 'subtitles'"
             />
           </div>
        </aside>

        <button
          v-else-if="!isMobile"
          class="flex-shrink-0 w-8 border-r border-default bg-bg-surface flex items-center justify-center hover:bg-bg-surface-hover transition-colors"
          @click="leftPanelCollapsed = false"
          aria-label="Expand clip library"
        >
          <span class="text-xs text-text-muted">▶</span>
        </button>

        <div class="flex-1 flex flex-col overflow-hidden min-w-0">
          <div class="flex-1 flex items-center justify-center bg-bg-base relative overflow-hidden">
            <div v-if="hasTracks" class="w-full h-full flex items-center justify-center p-sm">
              <div class="w-full max-w-3xl aspect-video bg-black rounded-lg shadow-lg flex flex-col border border-default">
                <div class="flex-1 flex items-center justify-center relative">
                  <div class="text-center">
                    <div class="w-16 h-16 mx-auto mb-4 rounded-lg bg-bg-surface flex items-center justify-center">
                      <span class="text-3xl" aria-hidden="true">🎥</span>
                    </div>
                    <p class="text-sm text-text-secondary">Video Preview</p>
                    <p v-if="currentClipName" class="text-xs text-primary-400 mt-1" aria-live="polite">
                      {{ currentClipName }}
                    </p>
                    <p v-else class="text-xs text-text-muted mt-1" aria-live="polite">
                      No clip selected
                    </p>
                  </div>
                  <div class="absolute bottom-2 left-2 right-2 flex items-center justify-between">
                    <span class="text-[10px] text-text-muted font-mono bg-black/60 px-1.5 py-0.5 rounded" aria-live="polite">
                      {{ currentTime.toFixed(1) }}s
                    </span>
                    <span class="text-[10px] text-text-muted font-mono bg-black/60 px-1.5 py-0.5 rounded">
                      {{ totalDuration.toFixed(1) }}s
                    </span>
                  </div>
                </div>
                <div class="h-8 flex items-center justify-center gap-2 border-t border-default bg-bg-surface/50">
                  <button class="text-xs text-text-secondary hover:text-text-primary px-1" @click="stepBackward(1)" aria-label="Step backward">
                    ⏮
                  </button>
                  <button class="text-sm text-text-primary hover:text-primary-400 px-2" @click="togglePlayback" :aria-label="isPlaying ? 'Pause' : 'Play'">
                    {{ isPlaying ? '⏸' : '▶' }}
                  </button>
                  <button class="text-xs text-text-secondary hover:text-text-primary px-1" @click="stepForward(1)" aria-label="Step forward">
                    ⏭
                  </button>
                </div>
              </div>
            </div>
            <EmptyState
              v-else
              icon="🎞️"
              title="No tracks to preview"
              description="Add tracks and clips to see a preview"
            />
          </div>

          <div class="h-10 flex items-center justify-between px-md border-t border-default bg-bg-surface flex-shrink-0">
            <div class="flex items-center gap-sm">
              <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="stepBackward(5)" aria-label="Skip backward 5s">⏪</button>
              <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="togglePlayback" :aria-label="isPlaying ? 'Pause' : 'Play'">
                {{ isPlaying ? '⏸' : '▶' }}
              </button>
              <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="stepForward(5)" aria-label="Skip forward 5s">⏩</button>
              <span class="text-xs text-text-muted font-mono" aria-live="polite">
                {{ currentTime.toFixed(1) }}s / {{ totalDuration.toFixed(1) }}s
              </span>
              <span v-if="currentClipName" class="text-[10px] text-primary-400 truncate max-w-32" aria-live="polite">
                {{ currentClipName }}
              </span>
            </div>
            <div class="flex items-center gap-xs">
              <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="handleZoomOut" aria-label="Zoom out">−</button>
              <span class="text-xs text-text-muted w-10 text-center" aria-label="Zoom level">{{ zoomLevel }}%</span>
              <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="handleZoomIn" aria-label="Zoom in">+</button>
            </div>
          </div>
        </div>

        <aside
          v-if="!isMobile || !leftPanelCollapsed"
          class="flex-shrink-0 border-l border-default bg-bg-surface flex flex-col"
          :style="{ width: isMobile ? '100%' : `${rightPanelWidth}px` }"
        >
          <div class="flex border-b border-default flex-shrink-0" role="tablist" aria-label="Right panel tabs">
            <button
              v-for="tab in rightTabs"
              :key="tab.key"
              role="tab"
              :aria-selected="activeRightTab === tab.key"
              :aria-controls="`panel-${tab.key}`"
              class="flex-1 px-2 py-2 text-xs transition-colors relative"
              :class="activeRightTab === tab.key
                ? 'bg-primary-500/10 text-primary-400 border-b-2 border-primary-400'
                : 'text-text-secondary hover:text-text-primary hover:bg-bg-surface-hover'"
              @click="activeRightTab = tab.key"
            >
              <span class="block text-sm" aria-hidden="true">{{ tab.icon }}</span>
              <span class="block mt-0.5">{{ tab.label }}</span>
              <span v-if="tab.beta" class="absolute top-1 right-1 text-[8px] px-1 py-0 rounded bg-purple-600/30 text-purple-300 font-medium">BETA</span>
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
            <div v-else id="panel-properties" role="tabpanel" aria-label="Properties panel">
              <PropertiesPanel />
            </div>
          </div>
        </aside>
      </div>

      <div v-if="showMigrationBanner" class="bg-warning-500/10 border-t border-warning-500/30 px-md py-sm flex items-center justify-between flex-shrink-0">
        <span class="text-xs text-warning-600">⚠️ Timeline schema v1 detected. Migration to v2 available.</span>
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
        <span class="text-xs text-success-600">✅ Demo project loaded successfully! Explore the editor with sample clips and subtitles.</span>
        <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="showDemoToast = false" aria-label="Dismiss notification">
          ✕
        </button>
      </div>

      <div class="flex-shrink-0 border-t border-default" :style="{ height: isMobile ? '120px' : '200px' }">
        <TimelineEditor v-if="useNewTimeline" v-model:currentTime="currentTime" :enhanced="true" />
        <TimelineEditor v-else v-model:currentTime="currentTime" />
      </div>
    </div>
    </div>
  </div>
</template>
