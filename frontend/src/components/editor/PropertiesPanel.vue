<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useTimelineStore } from '@/stores/timeline'
import { useProjectStore } from '@/stores/project'
import { useSubtitleStore } from '@/stores/subtitle'

const timelineStore = useTimelineStore()
const projectStore = useProjectStore()
const subtitleStore = useSubtitleStore()

const hasSelectedClip = computed(() => timelineStore.selectedClipId !== null)

const selectedClipData = computed(() => {
  if (!hasSelectedClip.value) return null
  const sel = timelineStore.selectedTrackClip
  if (!sel) return null
  return {
    trackClip: sel.trackClip,
    track: sel.track,
    clip: timelineStore.selectedClip,
  }
})

const editableName = ref('')
const editableStart = ref('0')
const editableEnd = ref('0')
const volume = ref(100)
const opacity = ref(100)

watch(() => selectedClipData.value?.clip?.name, (name) => {
  if (name) editableName.value = name
}, { immediate: true })

watch(() => selectedClipData.value?.trackClip, (tc) => {
  if (tc) {
    editableStart.value = tc.start.toFixed(1)
    editableEnd.value = (tc.start + tc.duration).toFixed(1)
  }
}, { immediate: true })

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function applyName() {
  if (selectedClipData.value?.clip && editableName.value.trim()) {
    timelineStore.updateClipName(selectedClipData.value.clip.id, editableName.value.trim())
  }
}

function applyStartTime() {
  if (!selectedClipData.value?.trackClip) return
  const start = parseFloat(editableStart.value)
  if (!isNaN(start) && start >= 0) {
    const tc = selectedClipData.value.trackClip
    timelineStore.updateTrackClipTime(tc.id, tc.trackId, start, tc.duration)
  }
}

function applyEndTime() {
  if (!selectedClipData.value?.trackClip) return
  const end = parseFloat(editableEnd.value)
  const start = parseFloat(editableStart.value)
  if (!isNaN(end) && !isNaN(start) && end > start) {
    const tc = selectedClipData.value.trackClip
    timelineStore.updateTrackClipTime(tc.id, tc.trackId, start, end - start)
  }
}

function handleDelete() {
  timelineStore.deleteSelectedClip()
}

function handleDuplicate() {
  timelineStore.duplicateSelectedClip()
}

const totalClips = computed(() =>
  timelineStore.state.tracks.reduce((sum, t) => sum + t.clips.length, 0)
)

const totalEffects = computed(() =>
  timelineStore.state.tracks.reduce((sum, t) =>
    sum + t.clips.reduce((s, tc) => s + (tc.effects?.length || 0), 0), 0)
)

const projectDuration = computed(() => formatTime(timelineStore.state.duration))
</script>

<template>
  <div class="flex flex-col h-full overflow-y-auto p-3 space-y-3">
    <!-- Clip Properties -->
    <template v-if="hasSelectedClip && selectedClipData">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-white">Clip Properties</h3>
        <span class="text-[10px] px-1.5 py-0.5 rounded bg-surface-3 text-text-secondary capitalize">
          {{ selectedClipData.clip?.type }}
        </span>
      </div>

      <div class="space-y-2">
        <div>
          <label class="text-[10px] text-text-tertiary block mb-0.5">Name</label>
          <input
            v-model="editableName"
            type="text"
            class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
            @blur="applyName"
            @keydown.enter="applyName"
          />
        </div>

        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="text-[10px] text-text-tertiary block mb-0.5">Start</label>
            <input
              v-model="editableStart"
              type="number"
              step="0.1"
              min="0"
              class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
              @blur="applyStartTime"
              @keydown.enter="applyStartTime"
            />
          </div>
          <div>
            <label class="text-[10px] text-text-tertiary block mb-0.5">End</label>
            <input
              v-model="editableEnd"
              type="number"
              step="0.1"
              min="0"
              class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
              @blur="applyEndTime"
              @keydown.enter="applyEndTime"
            />
          </div>
        </div>

        <div class="flex items-center justify-between text-xs">
          <span class="text-text-secondary">Duration</span>
          <span class="text-white font-mono">{{ selectedClipData.trackClip ? formatTime(selectedClipData.trackClip.duration) : '0:00' }}</span>
        </div>

        <div class="flex items-center justify-between text-xs">
          <span class="text-text-secondary">Track</span>
          <span class="text-white">{{ selectedClipData.track?.name || '—' }}</span>
        </div>

        <div v-if="selectedClipData.clip" class="text-[10px] text-text-tertiary space-y-0.5">
          <div v-if="selectedClipData.clip.width && selectedClipData.clip.height">
            Resolution: {{ selectedClipData.clip.width }}×{{ selectedClipData.clip.height }}
          </div>
          <div v-if="selectedClipData.clip.fileSize">
            File size: {{ (selectedClipData.clip.fileSize / (1024 * 1024)).toFixed(1) }} MB
          </div>
        </div>
      </div>

      <div class="border-t border-border-subtle pt-2 space-y-2">
        <div>
          <div class="flex items-center justify-between mb-1">
            <label class="text-[10px] text-text-tertiary">Volume</label>
            <span class="text-[10px] text-text-secondary">{{ volume }}%</span>
          </div>
          <input
            v-model="volume"
            type="range"
            min="0"
            max="100"
            class="w-full h-1"
          />
        </div>
        <div>
          <div class="flex items-center justify-between mb-1">
            <label class="text-[10px] text-text-tertiary">Opacity</label>
            <span class="text-[10px] text-text-secondary">{{ opacity }}%</span>
          </div>
          <input
            v-model="opacity"
            type="range"
            min="0"
            max="100"
            class="w-full h-1"
          />
        </div>
      </div>

      <div v-if="selectedClipData.trackClip?.effects?.length" class="border-t border-border-subtle pt-2">
        <div class="text-[10px] text-text-tertiary mb-1">Effects ({{ selectedClipData.trackClip.effects.length }})</div>
        <div class="space-y-1">
          <div
            v-for="effect in selectedClipData.trackClip.effects"
            :key="effect.id"
            class="flex items-center justify-between px-2 py-1 rounded bg-surface-2/50"
          >
            <span class="text-[10px] text-white">{{ effect.effectKey }}</span>
          </div>
        </div>
      </div>

      <div class="border-t border-border-subtle pt-2 flex gap-2">
        <button
          class="flex-1 px-2 py-1.5 text-xs bg-info-muted text-info rounded hover:bg-blue-600/30"
          @click="handleDuplicate"
        >
          ⧉ Duplicate
        </button>
        <button
          class="flex-1 px-2 py-1.5 text-xs bg-danger-muted text-danger rounded hover:bg-red-600/30"
          @click="handleDelete"
        >
          🗑 Delete
        </button>
      </div>
    </template>

    <!-- Project Properties -->
    <template v-else>
      <h3 class="text-sm font-semibold text-white">Project Properties</h3>

      <div class="space-y-2">
        <div>
          <label class="text-[10px] text-text-tertiary block mb-0.5">Project Name</label>
          <input
            :value="projectStore.currentProject?.name || 'Untitled Project'"
            type="text"
            class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
            readonly
          />
        </div>

        <div class="grid grid-cols-2 gap-2 text-xs">
          <div class="p-2 rounded bg-surface-2/50">
            <div class="text-text-tertiary text-[10px]">Duration</div>
            <div class="text-white font-mono">{{ projectDuration }}</div>
          </div>
          <div class="p-2 rounded bg-surface-2/50">
            <div class="text-text-tertiary text-[10px]">Tracks</div>
            <div class="text-white font-mono">{{ timelineStore.state.tracks.length }}</div>
          </div>
          <div class="p-2 rounded bg-surface-2/50">
            <div class="text-text-tertiary text-[10px]">Clips</div>
            <div class="text-white font-mono">{{ totalClips }}</div>
          </div>
          <div class="p-2 rounded bg-surface-2/50">
            <div class="text-text-tertiary text-[10px]">Subtitles</div>
            <div class="text-white font-mono">{{ subtitleStore.tracks.length }}</div>
          </div>
          <div class="p-2 rounded bg-surface-2/50 col-span-2">
            <div class="text-text-tertiary text-[10px]">Effects</div>
            <div class="text-white font-mono">{{ totalEffects }}</div>
          </div>
        </div>
      </div>

      <div class="border-t border-border-subtle pt-2">
        <p class="text-[10px] text-text-tertiary">
          Select a clip on the timeline to view and edit its properties.
        </p>
      </div>
    </template>
  </div>
</template>
