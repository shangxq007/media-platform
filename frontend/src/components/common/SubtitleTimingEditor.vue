<script setup lang="ts">
import { ref, computed } from 'vue'
import { useSubtitleStore } from '@/stores/subtitle'

const subtitleStore = useSubtitleStore()
const editingCueId = ref<string | null>(null)

const activeTrack = computed(() => subtitleStore.activeTrack)

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.floor((seconds % 1) * 1000)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms).padStart(3, '0')}`
}

function parseTimeStr(str: string): number {
  const parts = str.split(':')
  if (parts.length === 3) {
    return parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2])
  }
  return 0
}

function updateCueTime(cueId: string, field: 'startTime' | 'endTime', value: string) {
  if (!activeTrack.value) return
  const time = parseTimeStr(value)
  subtitleStore.updateCue(activeTrack.value.id, cueId, { [field]: time })
}

function addCue() {
  if (!activeTrack.value) return
  const lastCue = activeTrack.value.cues[activeTrack.value.cues.length - 1]
  const start = lastCue ? lastCue.endTime + 0.5 : 0
  const newCue = {
    id: `cue_${Date.now()}`,
    index: activeTrack.value.cues.length + 1,
    startTime: start,
    endTime: start + 2,
    text: 'New subtitle'
  }
  activeTrack.value.cues.push(newCue)
}

function removeCue(cueId: string) {
  if (!activeTrack.value) return
  const idx = activeTrack.value.cues.findIndex((c: any) => c.id === cueId)
  if (idx >= 0) activeTrack.value.cues.splice(idx, 1)
}

function shiftAllCues(delta: number) {
  if (!activeTrack.value) return
  for (const cue of activeTrack.value.cues) {
    cue.startTime = Math.max(0, cue.startTime + delta)
    cue.endTime = Math.max(0, cue.endTime + delta)
  }
}
</script>

<template>
  <div v-if="activeTrack" class="border-t border-gray-700 bg-gray-800/50">
    <div class="px-2 py-1 flex items-center justify-between border-b border-gray-700/50">
      <span class="text-xs text-white font-medium">{{ activeTrack.label }} — Timing</span>
      <div class="flex gap-1">
        <button class="px-1.5 py-0.5 text-[10px] bg-gray-700 rounded text-white" @click="shiftAllCues(-0.5)">◀ 0.5s</button>
        <button class="px-1.5 py-0.5 text-[10px] bg-gray-700 rounded text-white" @click="shiftAllCues(0.5)">0.5s ▶</button>
        <button class="px-1.5 py-0.5 text-[10px] bg-clip-video/30 rounded text-clip-video" @click="addCue()">+ Cue</button>
      </div>
    </div>

    <div class="max-h-40 overflow-y-auto">
      <div
        v-for="cue in activeTrack.cues"
        :key="cue.id"
        class="flex items-center gap-1 px-2 py-1 border-b border-gray-700/30 text-xs"
        :class="editingCueId === cue.id ? 'bg-clip-video/10' : ''"
      >
        <span class="text-gray-500 w-6 text-right">{{ cue.index }}</span>
        <input :value="formatTime(cue.startTime)" class="w-20 bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white"
          @change="updateCueTime(cue.id, 'startTime', ($event.target as HTMLInputElement).value)" />
        <span class="text-gray-600">→</span>
        <input :value="formatTime(cue.endTime)" class="w-20 bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white"
          @change="updateCueTime(cue.id, 'endTime', ($event.target as HTMLInputElement).value)" />
        <input v-model="cue.text" class="flex-1 bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white min-w-0" />
        <button class="text-red-400 hover:text-red-300 text-[10px]" @click="removeCue(cue.id)">✕</button>
      </div>
    </div>

    <div v-if="!activeTrack.cues.length" class="p-2 text-xs text-gray-500 text-center">
      No cues. Upload a subtitle file or add cues manually.
    </div>
  </div>
</template>
