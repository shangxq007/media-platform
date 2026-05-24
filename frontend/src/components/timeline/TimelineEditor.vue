<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useTimelineStore } from '@/stores/timeline'
import { useHistoryStore } from '@/stores/history'
import SubtitleTimeline from './SubtitleTimeline.vue'
import SubtitleTimingEditor from '@/components/common/SubtitleTimingEditor.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Clip, Track, TrackClip } from '@/types'

const props = defineProps<{
  currentTime?: number
}>()

const emit = defineEmits<{
  'update:currentTime': [time: number]
}>()

const store = useTimelineStore()
const history = useHistoryStore()
const timelineRef = ref<HTMLElement | null>(null)
const dragging = ref<{ clipId: string; trackId: string; offsetX: number } | null>(null)

const PIXELS_PER_SECOND = 50
const TRACK_HEIGHT = 60
const HEADER_WIDTH = 120

const hasClipsOnTracks = computed(() =>
  store.state.tracks.some(track => track.clips.length > 0)
)

const timelineWidth = computed(() => Math.max(store.state.duration * PIXELS_PER_SECOND, 400))
const rulerMarkers = computed(() => {
  const markers: { time: number; label: string }[] = []
  const step = store.state.zoom > 2 ? 1 : store.state.zoom > 0.5 ? 5 : 10
  for (let t = 0; t <= store.state.duration; t += step) {
    markers.push({ time: t, label: formatTime(t) })
  }
  return markers
})

const totalClips = computed(() =>
  store.state.tracks.reduce((sum, t) => sum + t.clips.length, 0)
)

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function getClipColor(clip: Clip | undefined): string {
  if (!clip) return 'bg-surface-4'
  switch (clip.type) {
    case 'video': return 'bg-blue-600'
    case 'audio': return 'bg-green-600'
    case 'text': return 'bg-red-600'
    case 'image': return 'bg-purple-600'
    case 'subtitle': return 'bg-yellow-600'
    default: return 'bg-surface-4'
  }
}

function getClipStyle(tc: TrackClip) {
  return {
    left: `${tc.start * PIXELS_PER_SECOND}px`,
    width: `${Math.max(tc.duration * PIXELS_PER_SECOND, 20)}px`,
    height: `${TRACK_HEIGHT - 8}px`,
    top: '4px',
  }
}

function getTrackColor(type: string): string {
  switch (type) {
    case 'video': return 'border-info/30'
    case 'audio': return 'border-success/30'
    case 'text': return 'border-danger/30'
    default: return 'border-border-default'
  }
}

function getTrackIcon(type: string): string {
  switch (type) {
    case 'video': return '🎬'
    case 'audio': return '🎵'
    case 'text': return '📝'
    default: return '📄'
  }
}

function isClipSelected(tc: TrackClip): boolean {
  return store.selectedClipId === tc.id
}

function isClipPatchHighlighted(tc: TrackClip): boolean {
  return store.isClipPatchHighlighted(tc.id)
}

function onClipClick(e: MouseEvent, tc: TrackClip) {
  e.stopPropagation()
  store.selectClip(tc.id)
}

function onClipMouseDown(e: MouseEvent, trackId: string, clipId: string) {
  const track = store.state.tracks.find(t => t.id === trackId)
  if (track?.locked) return
  store.selectClip(clipId)
  dragging.value = { clipId, trackId, offsetX: e.offsetX }
  history.saveState(store)
  e.preventDefault()
}

function onTimelineMouseMove(e: MouseEvent) {
  if (!dragging.value || !timelineRef.value) return
  const rect = timelineRef.value.getBoundingClientRect()
  const scrollLeft = timelineRef.value.scrollLeft
  const x = e.clientX - rect.left + scrollLeft - dragging.value.offsetX
  const newStart = Math.max(0, x / PIXELS_PER_SECOND)
  store.moveClip(dragging.value.trackId, dragging.value.clipId, newStart)
}

function onMouseUp() {
  if (dragging.value) {
    history.saveState(store)
  }
  dragging.value = null
}

function onClipDrop(e: DragEvent, trackId: string, clipId: string) {
  const effectKey = e.dataTransfer?.getData('effectKey')
  if (!effectKey) return

  const track = store.state.tracks.find(t => t.id === trackId)
  if (!track) return
  const tc = track.clips.find(c => c.id === clipId)
  if (!tc) return

  if (!tc.effects) tc.effects = []
  tc.effects.push({
    id: `ce_${Date.now()}`,
    effectKey,
    providerPreference: ['javacv'],
    parameters: {}
  })
  history.saveState(store)
}

function onTrackDragOver(e: DragEvent) {
  e.preventDefault()
}

function onTrackDrop(e: DragEvent, track: Track) {
  e.preventDefault()
  const clipId = e.dataTransfer?.getData('clipId')
  if (!clipId) return

  const clip = store.clips.find(c => c.id === clipId)
  if (!clip) return

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const scrollLeft = timelineRef.value?.scrollLeft || 0
  const x = e.clientX - rect.left + scrollLeft
  const startTime = Math.max(0, x / PIXELS_PER_SECOND)

  history.saveState(store)
  store.addClipToTrack(track.id, clip, startTime)
}

function onRangeInput(e: Event) {
  const t = Number((e.target as HTMLInputElement).value)
  store.setCurrentTime(t)
  emit('update:currentTime', t)
}

function onTimelineClick(e: MouseEvent) {
  if (!timelineRef.value) return
  const rect = timelineRef.value.getBoundingClientRect()
  const scrollLeft = timelineRef.value.scrollLeft
  const x = e.clientX - rect.left + scrollLeft
  const time = x / PIXELS_PER_SECOND
  store.setCurrentTime(time)
  emit('update:currentTime', time)
  store.deselectClip()
}

function toggleTrackLock(track: Track) {
  track.locked = !track.locked
}

function toggleTrackMute(track: Track) {
  track.muted = !track.muted
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (store.selectedClipId) {
      e.preventDefault()
      history.saveState(store)
      store.deleteSelectedClip()
    }
  } else if (e.key === 'd' && (e.ctrlKey || e.metaKey)) {
    if (store.selectedClipId) {
      e.preventDefault()
      history.saveState(store)
      store.duplicateSelectedClip()
    }
  }
}

watch(() => props.currentTime, (t) => {
  if (t !== undefined && t !== store.state.currentTime) {
    store.state.currentTime = t
  }
})

const PIXELS_PER_SECOND_SCROLL = 50

function scrollTrackClipIntoView(trackClipId: string) {
  if (!timelineRef.value) {
    return
  }
  for (const track of store.state.tracks) {
    const tc = track.clips.find(c => c.id === trackClipId)
    if (!tc) {
      continue
    }
    const targetLeft = Math.max(0, tc.start * PIXELS_PER_SECOND_SCROLL - 80)
    timelineRef.value.scrollLeft = targetLeft
    return
  }
}

watch(
  () => store.scrollToTrackClipId,
  id => {
    if (id) {
      scrollTrackClipIntoView(id)
      store.clearScrollToTrackClipRequest()
    }
  }
)

onMounted(() => {
  document.addEventListener('mouseup', onMouseUp)
  document.addEventListener('mousemove', onTimelineMouseMove)
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('mouseup', onMouseUp)
  document.removeEventListener('mousemove', onTimelineMouseMove)
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div class="flex-1 flex flex-col bg-timeline-bg overflow-hidden">
    <!-- Transport Controls -->
    <div class="flex items-center gap-2 px-4 py-2 bg-panel-bg border-b border-border-subtle">
      <button
        class="px-3 py-1 rounded text-sm"
        :class="store.state.playing ? 'bg-red-600' : 'bg-clip-video'"
        @click="store.togglePlayback"
      >
        {{ store.state.playing ? '⏸ Pause' : '▶ Play' }}
      </button>
      <span class="text-sm text-text-secondary font-mono">{{ formatTime(store.state.currentTime) }}</span>
      <input
        type="range"
        :max="store.state.duration"
        :value="store.state.currentTime"
        step="0.1"
        class="flex-1 mx-2"
        @input="onRangeInput"
      />
      <span class="text-sm text-text-secondary font-mono">{{ formatTime(store.state.duration) }}</span>
      <div class="flex items-center gap-1 ml-4">
        <button class="px-2 py-1 text-xs bg-surface-3 rounded" @click="store.setZoom(store.state.zoom / 1.2)">−</button>
        <span class="text-xs text-text-secondary w-12 text-center">{{ (store.state.zoom * 100).toFixed(0) }}%</span>
        <button class="px-2 py-1 text-xs bg-surface-3 rounded" @click="store.setZoom(store.state.zoom * 1.2)">+</button>
      </div>
      <div class="flex items-center gap-1 ml-4 border-l border-border-default pl-3">
        <button class="px-2 py-1 text-xs bg-surface-3 rounded" :disabled="!history.canUndo()" @click="history.undo(store)">↶</button>
        <button class="px-2 py-1 text-xs bg-surface-3 rounded" :disabled="!history.canRedo()" @click="history.redo(store)">↷</button>
      </div>
      <div class="ml-auto flex items-center gap-2 text-[10px] text-text-tertiary">
        <span
          v-if="store.patchHighlightClipIds.length"
          class="text-amber-400"
          title="Patch / 冲突 diff 影响的片段"
        >
          {{ store.patchHighlightClipIds.length }} 处高亮
        </span>
        <span>{{ store.state.tracks.length }} tracks</span>
        <span>{{ totalClips }} clips</span>
      </div>
    </div>

    <!-- Timeline Area -->
    <div class="flex-1 flex overflow-hidden">
      <!-- Track Headers -->
      <div class="flex-shrink-0 bg-panel-bg border-r border-border-subtle" :style="{ width: HEADER_WIDTH + 'px' }">
        <div class="h-8 border-b border-border-subtle flex items-center px-2">
          <span class="text-xs text-text-tertiary">Tracks</span>
        </div>
        <div
          v-for="track in store.state.tracks"
          :key="track.id"
          class="flex items-center justify-between px-2 border-b border-border-subtle/50"
          :style="{ height: TRACK_HEIGHT + 'px' }"
        >
          <div class="flex items-center gap-1 min-w-0 flex-1">
            <span class="text-xs" aria-hidden="true">{{ getTrackIcon(track.type) }}</span>
            <span class="text-xs text-text-primary truncate">{{ track.name }}</span>
          </div>
          <div class="flex gap-1">
            <button
              class="text-xs px-1 rounded"
              :class="track.muted ? 'bg-red-600' : 'bg-surface-4'"
              :title="track.muted ? 'Unmute' : 'Mute'"
              @click="toggleTrackMute(track)"
            >M</button>
            <button
              class="text-xs px-1 rounded"
              :class="track.locked ? 'bg-yellow-600' : 'bg-surface-4'"
              :title="track.locked ? 'Unlock' : 'Lock'"
              @click="toggleTrackLock(track)"
            >L</button>
          </div>
        </div>
        <div
          v-if="store.state.tracks.length === 0"
          class="flex items-center justify-center h-20 px-2"
        >
          <span class="text-[10px] text-text-tertiary">No tracks</span>
        </div>
      </div>

      <!-- Timeline Content -->
      <div
        ref="timelineRef"
        class="flex-1 overflow-x-auto overflow-y-auto relative"
        @click="onTimelineClick"
      >
        <!-- Ruler -->
        <div class="sticky top-0 h-8 bg-track-bg border-b border-border-subtle z-10" :style="{ width: timelineWidth + 'px', minWidth: '100%' }">
          <div
            v-for="marker in rulerMarkers"
            :key="marker.time"
            class="absolute top-0 h-full border-l border-border-default"
            :style="{ left: marker.time * PIXELS_PER_SECOND + 'px' }"
          >
            <span class="text-xs text-text-tertiary ml-1">{{ marker.label }}</span>
          </div>
        </div>

        <!-- Tracks -->
        <template v-if="store.state.tracks.length > 0">
          <div
            v-for="track in store.state.tracks"
            :key="track.id"
            class="relative border-b border-border-subtle/30"
            :class="getTrackColor(track.type)"
            :style="{ height: TRACK_HEIGHT + 'px', width: timelineWidth + 'px', minWidth: '100%' }"
            @dragover="onTrackDragOver"
            @drop="onTrackDrop($event, track)"
          >
            <!-- Empty track indicator -->
            <div
              v-if="track.clips.length === 0"
              class="absolute inset-0 flex items-center justify-center"
            >
              <span class="text-[10px] text-text-tertiary">Drop clips here</span>
            </div>

            <!-- Clips -->
            <div
              v-for="tc in track.clips"
              :key="tc.id"
              class="absolute rounded cursor-move flex items-center justify-start text-xs text-white font-medium overflow-hidden px-1"
              :class="[
                getClipColor(store.clips.find(c => c.id === tc.clipId)),
                track.locked ? 'opacity-50 cursor-not-allowed' : 'hover:ring-2 hover:ring-white/50',
                isClipSelected(tc) ? 'ring-2 ring-yellow-400' : '',
                isClipPatchHighlighted(tc) ? 'ring-2 ring-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.5)]' : ''
              ]"
              :style="getClipStyle(tc)"
              @click="onClipClick($event, tc)"
              @mousedown="onClipMouseDown($event, track.id, tc.id)"
              @dragover.prevent
              @drop="onClipDrop($event, track.id, tc.id)"
            >
              <div class="flex flex-col items-start w-full overflow-hidden">
                <span class="truncate w-full text-[10px]">{{ store.clips.find(c => c.id === tc.clipId)?.name || tc.clipId }}</span>
                <div class="flex items-center justify-between w-full">
                  <span class="text-[8px] text-white/60">{{ formatTime(tc.duration) }}</span>
                  <div v-if="tc.effects?.length" class="flex gap-0.5">
                    <span
                      v-for="eff in tc.effects.slice(0, 3)"
                      :key="eff.id"
                      class="px-0.5 py-0 rounded text-[7px] bg-blue-400/50"
                    >
                      {{ eff.effectKey?.split('.').pop() || 'fx' }}
                    </span>
                    <span v-if="tc.effects.length > 3" class="text-[7px] text-white/60">+{{ tc.effects.length - 3 }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- Empty State -->
        <div
          v-if="store.state.tracks.length === 0 || !hasClipsOnTracks"
          class="flex items-center justify-center py-xl"
          :style="{ width: timelineWidth + 'px', minWidth: '100%' }"
        >
          <EmptyState
            icon="🎞️"
            title="Timeline is empty"
            description="Upload clips or try a demo project to populate the timeline"
          />
        </div>

        <!-- Playhead -->
        <div
          class="absolute top-0 bottom-0 w-0.5 bg-red-500 z-20 pointer-events-none"
          :style="{ left: store.state.currentTime * PIXELS_PER_SECOND + HEADER_WIDTH + 'px' }"
        >
          <div class="absolute -top-1 -left-1.5 w-3 h-3 bg-red-500 rounded-full" />
        </div>
      </div>
    </div>
    <SubtitleTimeline />
    <SubtitleTimingEditor />
  </div>
</template>
