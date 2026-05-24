<script setup lang="ts">
import type { } from 'vue'
import { useSubtitleStore } from '@/stores/subtitle'

const subtitleStore = useSubtitleStore()
const PIXELS_PER_SECOND = 50

function cueStyle(cue: any) {
  return {
    left: `${cue.startTime * PIXELS_PER_SECOND}px`,
    width: `${(cue.endTime - cue.startTime) * PIXELS_PER_SECOND}px`
  }
}
</script>

<template>
  <div v-if="subtitleStore.tracks.length" class="border-t border-border-subtle bg-track-bg/50">
    <div class="px-2 py-1 text-xs text-text-secondary border-b border-border-subtle/50 flex items-center justify-between">
      <span>Subtitle Tracks</span>
      <span class="text-[10px]">{{ subtitleStore.activeTrack?.label || 'None' }}</span>
    </div>
    <div
      v-for="track in subtitleStore.tracks"
      :key="track.id"
      class="relative border-b border-border-subtle/30"
      :class="subtitleStore.activeTrackId === track.id ? 'bg-clip-text/5' : ''"
      :style="{ height: '32px' }"
      @click="subtitleStore.activeTrackId = track.id"
    >
      <div class="absolute left-1 top-0.5 text-[9px] text-text-tertiary z-10">
        {{ track.language }}
      </div>
      <div
        v-for="cue in track.cues"
        :key="cue.id"
        class="absolute top-5 h-5 rounded-sm bg-clip-text/20 border border-clip-text/40 flex items-center px-1 overflow-hidden cursor-pointer hover:bg-clip-text/30"
        :style="cueStyle(cue)"
        :title="cue.text"
      >
        <span class="text-[8px] text-clip-text truncate">{{ cue.text }}</span>
      </div>
    </div>
  </div>
</template>
