<script setup lang="ts">
import { ref } from 'vue'
import { useSubtitleStore } from '@/stores/subtitle'

const subtitleStore = useSubtitleStore()
const language = ref('en')
const burnIn = ref(true)
const uploading = ref(false)

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
</script>

<template>
  <div class="p-2 space-y-3">
    <h4 class="text-xs font-medium text-white">Subtitles</h4>

    <!-- Upload -->
    <div class="space-y-2">
      <div class="flex gap-2">
        <select v-model="language" class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white flex-1">
          <option value="en">English</option>
          <option value="zh">中文</option>
          <option value="ja">日本語</option>
          <option value="ko">한국어</option>
          <option value="es">Español</option>
          <option value="fr">Français</option>
          <option value="de">Deutsch</option>
        </select>
        <select v-model="burnIn" class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white">
          <option :value="true">Burn-in</option>
          <option :value="false">External</option>
        </select>
      </div>

      <label class="block">
        <span class="block w-full text-center py-1.5 text-xs bg-clip-video/20 text-clip-video rounded cursor-pointer hover:bg-clip-video/30">
          {{ uploading ? 'Uploading...' : '+ Upload SRT/ASS/VTT' }}
        </span>
        <input type="file" accept=".srt,.ass,.vtt,.ssa" multiple class="hidden" @change="onSubtitleUpload" />
      </label>

      <label class="block">
        <span class="block w-full text-center py-1.5 text-xs bg-gray-700 text-white rounded cursor-pointer hover:bg-gray-600">
          + Upload Font (TTF/OTF)
        </span>
        <input type="file" accept=".ttf,.otf" multiple class="hidden" @change="onFontUpload" />
      </label>
    </div>

    <!-- Tracks -->
    <div v-if="subtitleStore.tracks.length" class="space-y-1">
      <div class="text-xs text-gray-400">Tracks</div>
      <div
        v-for="track in subtitleStore.tracks"
        :key="track.id"
        class="p-2 rounded border text-xs cursor-pointer"
        :class="subtitleStore.activeTrackId === track.id ? 'border-clip-video bg-clip-video/10' : 'border-gray-700'"
        @click="subtitleStore.activeTrackId = track.id"
      >
        <div class="flex items-center justify-between text-white">
          <span>{{ track.label }}</span>
          <div class="flex gap-1">
            <span v-if="track.burnIn" class="px-1 py-0 rounded bg-yellow-600/50 text-yellow-400 text-[8px]">BURN</span>
            <span v-else class="px-1 py-0 rounded bg-blue-600/50 text-blue-400 text-[8px]">EXT</span>
            <button class="text-red-400 hover:text-red-300" @click.stop="subtitleStore.removeTrack(track.id)">✕</button>
          </div>
        </div>
        <div class="text-gray-500 mt-0.5">{{ track.cues.length }} cues · {{ track.language }}</div>
        <!-- Font selector -->
        <div v-if="subtitleStore.fonts.length" class="mt-1">
          <select
            :value="track.fontId || ''"
            class="w-full bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white"
            @change="subtitleStore.setTrackFont(track.id, ($event.target as HTMLInputElement).value)"
          >
            <option value="">Default Font</option>
            <option v-for="font in subtitleStore.fonts" :key="font.fontId" :value="font.fontId">
              {{ font.family }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- Fonts -->
    <div v-if="subtitleStore.fonts.length" class="space-y-1">
      <div class="text-xs text-gray-400">Fonts</div>
      <div v-for="font in subtitleStore.fonts" :key="font.fontId" class="p-1.5 rounded border border-gray-700 text-xs text-white flex justify-between">
        <span>{{ font.family }} ({{ font.format.toUpperCase() }})</span>
        <span class="text-gray-500">{{ (font.fileSize / 1024).toFixed(0) }}KB</span>
      </div>
    </div>

    <!-- Error -->
    <div v-if="subtitleStore.error" class="p-2 rounded bg-red-900/30 border border-red-700 text-xs text-red-400">
      {{ subtitleStore.error }}
    </div>
  </div>
</template>
