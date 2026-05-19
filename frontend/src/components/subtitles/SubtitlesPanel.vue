<script setup lang="ts">
import { ref, computed } from 'vue'
import { useSubtitleStore } from '@/stores/subtitle'
import { useTimelineStore } from '@/stores/timeline'
import type { SubtitleCue } from '@/types'
import SubtitleCueList from './SubtitleCueList.vue'
import SubtitleCueEditor from './SubtitleCueEditor.vue'

const subtitleStore = useSubtitleStore()
const timelineStore = useTimelineStore()

const language = ref('en')
const burnIn = ref(true)
const uploading = ref(false)
const editingCue = ref<SubtitleCue | null>(null)

const timelineDuration = computed(() => timelineStore.state.duration)

const languages = [
  { value: 'en', label: 'English' },
  { value: 'zh', label: '中文' },
  { value: 'ja', label: '日本語' },
  { value: 'ko', label: '한국어' },
  { value: 'es', label: 'Español' },
  { value: 'fr', label: 'Français' },
  { value: 'de', label: 'Deutsch' },
]

async function onSubtitleUpload(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  uploading.value = true
  for (const file of Array.from(input.files)) {
    await subtitleStore.uploadSubtitleFile(file, language.value, burnIn.value)
  }
  uploading.value = false
  input.value = ''
}

async function onFontUpload(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  for (const file of Array.from(input.files)) {
    subtitleStore.uploadFont(file)
  }
  input.value = ''
}

function addCue() {
  const track = subtitleStore.activeTrack
  if (!track) return
  const lastCue = track.cues[track.cues.length - 1]
  const start = lastCue ? lastCue.endTime + 0.5 : 0
  const newCue: SubtitleCue = {
    id: `cue_${Date.now()}`,
    index: track.cues.length + 1,
    startTime: start,
    endTime: Math.min(start + 2, timelineDuration.value),
    text: 'New subtitle'
  }
  track.cues.push(newCue)
}

function handleEditCue(cue: SubtitleCue) {
  editingCue.value = cue
}

function handleSaveCue(updates: Partial<SubtitleCue>) {
  if (!editingCue.value || !subtitleStore.activeTrack) return
  subtitleStore.updateCue(subtitleStore.activeTrack.id, editingCue.value.id, updates)
  editingCue.value = null
}

function handleCancelEdit() {
  editingCue.value = null
}

function handleDeleteCue(cueId: string) {
  const track = subtitleStore.activeTrack
  if (!track) return
  const idx = track.cues.findIndex(c => c.id === cueId)
  if (idx >= 0) {
    track.cues.splice(idx, 1)
    track.cues.forEach((c, i) => { c.index = i + 1 })
  }
}

function handleUpdateCue(cueId: string, updates: Partial<SubtitleCue>) {
  const track = subtitleStore.activeTrack
  if (!track) return
  subtitleStore.updateCue(track.id, cueId, updates)
}

function handleReorderCues(cues: SubtitleCue[]) {
  const track = subtitleStore.activeTrack
  if (!track) return
  track.cues = cues
}

function toggleTrackVisibility() {
  const track = subtitleStore.activeTrack
  if (!track) return
  subtitleStore.setTrackBurnIn(track.id, !track.burnIn)
}

function shiftAllCues(delta: number) {
  const track = subtitleStore.activeTrack
  if (!track) return
  for (const cue of track.cues) {
    cue.startTime = Math.max(0, cue.startTime + delta)
    cue.endTime = Math.max(0.1, cue.endTime + delta)
  }
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Upload Section -->
    <div class="p-2 space-y-2 border-b border-gray-700">
      <h4 class="text-xs font-medium text-white">Upload Subtitles</h4>

      <div class="flex gap-2">
        <select
          v-model="language"
          class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white flex-1 focus:outline-none focus:border-primary-400"
        >
          <option v-for="lang in languages" :key="lang.value" :value="lang.value">
            {{ lang.label }}
          </option>
        </select>
        <select
          v-model="burnIn"
          class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-primary-400"
        >
          <option :value="true">Burn-in</option>
          <option :value="false">External</option>
        </select>
      </div>

      <label class="block">
        <span
          class="block w-full text-center py-1.5 text-xs rounded cursor-pointer transition-colors"
          :class="uploading ? 'bg-gray-700 text-gray-400' : 'bg-primary-500/20 text-primary-400 hover:bg-primary-500/30'"
        >
          {{ uploading ? 'Uploading...' : '+ Upload SRT/ASS/VTT' }}
        </span>
        <input type="file" accept=".srt,.ass,.vtt,.ssa" multiple class="hidden" @change="onSubtitleUpload" />
      </label>

      <label class="block">
        <span class="block w-full text-center py-1.5 text-xs bg-gray-700 text-gray-400 rounded cursor-pointer hover:bg-gray-600 transition-colors">
          + Upload Font (TTF/OTF)
        </span>
        <input type="file" accept=".ttf,.otf" multiple class="hidden" @change="onFontUpload" />
      </label>
    </div>

    <!-- Track Selector -->
    <div v-if="subtitleStore.tracks.length" class="p-2 border-b border-gray-700 space-y-1">
      <div class="text-[10px] text-gray-500 uppercase tracking-wider">Tracks</div>
      <div
        v-for="track in subtitleStore.tracks"
        :key="track.id"
        class="p-1.5 rounded border text-xs cursor-pointer transition-colors"
        :class="subtitleStore.activeTrackId === track.id
          ? 'border-primary-400 bg-primary-500/10'
          : 'border-gray-700 hover:border-gray-600'"
        @click="subtitleStore.activeTrackId = track.id"
      >
        <div class="flex items-center justify-between">
          <span class="text-white">{{ track.label }}</span>
          <div class="flex items-center gap-1">
            <span
              class="px-1 py-0 rounded text-[8px]"
              :class="track.burnIn ? 'bg-warning-500/30 text-warning-500' : 'bg-info-500/30 text-info-500'"
            >
              {{ track.burnIn ? 'BURN' : 'EXT' }}
            </span>
            <span class="text-gray-500 text-[10px]">{{ track.cues.length }}</span>
            <button
              class="text-gray-600 hover:text-danger-500 transition-colors"
              title="Remove track"
              @click.stop="subtitleStore.removeTrack(track.id)"
            >
              ✕
            </button>
          </div>
        </div>
        <div v-if="subtitleStore.fonts.length" class="mt-1">
          <select
            :value="track.fontId || ''"
            class="w-full bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white focus:outline-none"
            @change="subtitleStore.setTrackFont(track.id, ($event.target as HTMLInputElement).value)"
            @click.stop
          >
            <option value="">Default Font</option>
            <option v-for="font in subtitleStore.fonts" :key="font.fontId" :value="font.fontId">
              {{ font.family }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- Track Controls -->
    <div v-if="subtitleStore.activeTrack" class="px-2 py-1 border-b border-gray-700/50 flex items-center justify-between">
      <div class="flex gap-1">
        <button
          class="px-1.5 py-0.5 text-[10px] bg-gray-700 rounded text-gray-400 hover:text-white transition-colors"
          @click="shiftAllCues(-0.5)"
        >
          ◀ 0.5s
        </button>
        <button
          class="px-1.5 py-0.5 text-[10px] bg-gray-700 rounded text-gray-400 hover:text-white transition-colors"
          @click="shiftAllCues(0.5)"
        >
          0.5s ▶
        </button>
        <button
          class="px-1.5 py-0.5 text-[10px] rounded transition-colors"
          :class="subtitleStore.activeTrack?.burnIn
            ? 'bg-warning-500/20 text-warning-500 hover:bg-warning-500/30'
            : 'bg-info-500/20 text-info-500 hover:bg-info-500/30'"
          @click="toggleTrackVisibility"
        >
          {{ subtitleStore.activeTrack?.burnIn ? 'Burn-in' : 'External' }}
        </button>
      </div>
      <button
        class="px-1.5 py-0.5 text-[10px] bg-primary-500/20 text-primary-400 rounded hover:bg-primary-500/30 transition-colors"
        @click="addCue"
      >
        + Cue
      </button>
    </div>

    <!-- Cue List -->
    <div v-if="subtitleStore.activeTrack" class="flex-1 overflow-y-auto theme-scrollbar">
      <SubtitleCueList
        :track="subtitleStore.activeTrack"
        :duration="timelineDuration"
        @edit-cue="handleEditCue"
        @delete-cue="handleDeleteCue"
        @update-cue="handleUpdateCue"
        @add-cue="addCue"
        @reorder-cues="handleReorderCues"
      />
    </div>

    <!-- Cue Editor -->
    <div v-if="editingCue && subtitleStore.activeTrack" class="p-2 border-t border-gray-700">
      <SubtitleCueEditor
        :cue="editingCue"
        :duration="timelineDuration"
        @save="handleSaveCue"
        @cancel="handleCancelEdit"
      />
    </div>

    <!-- No Track State -->
    <div v-if="!subtitleStore.tracks.length" class="flex-1 flex items-center justify-center p-4">
      <div class="text-center">
        <div class="text-2xl mb-2">📝</div>
        <p class="text-xs text-gray-500">No subtitle tracks</p>
        <p class="text-[10px] text-gray-600 mt-1">Upload a file to get started</p>
      </div>
    </div>

    <!-- Fonts List -->
    <div v-if="subtitleStore.fonts.length" class="p-2 border-t border-gray-700 space-y-1">
      <div class="text-[10px] text-gray-500 uppercase tracking-wider">Fonts</div>
      <div
        v-for="font in subtitleStore.fonts"
        :key="font.fontId"
        class="p-1.5 rounded border border-gray-700 text-xs text-white flex justify-between"
      >
        <span>{{ font.family }} ({{ font.format.toUpperCase() }})</span>
        <span class="text-gray-500">{{ (font.fileSize / 1024).toFixed(0) }}KB</span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="subtitleStore.error" class="p-2 m-2 rounded bg-danger-500/10 border border-danger-500/30 text-xs text-danger-500">
      {{ subtitleStore.error }}
    </div>
  </div>
</template>
