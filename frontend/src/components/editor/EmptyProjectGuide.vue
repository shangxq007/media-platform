<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  upload: []
  tryDemo: []
  importSubtitle: []
}>()

const fileInput = ref<HTMLInputElement | null>(null)

function triggerFileUpload() {
  fileInput.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) {
    emit('upload')
  }
  input.value = ''
}
</script>

<template>
  <div class="flex flex-col items-center justify-center py-xl px-lg text-center">
    <div class="w-20 h-20 rounded-full bg-primary-50 flex items-center justify-center mb-lg">
      <span class="text-3xl" aria-hidden="true">🎬</span>
    </div>
    <h2 class="text-lg font-semibold text-text-primary mb-sm">Start Your Project</h2>
    <p class="text-sm text-text-secondary max-w-md mb-lg">
      Upload media files to start editing. You can also try a demo project to explore the editor.
    </p>
    <div class="flex flex-col gap-sm w-full max-w-xs">
      <button
        class="theme-btn theme-btn-primary theme-btn-lg w-full"
        @click="triggerFileUpload"
      >
        <span aria-hidden="true">📁</span>
        Upload Files
      </button>
      <button
        class="theme-btn theme-btn-secondary theme-btn-lg w-full"
        @click="emit('tryDemo')"
      >
        <span aria-hidden="true">✨</span>
        Try Demo Project
      </button>
      <button
        class="theme-btn theme-btn-ghost theme-btn-lg w-full"
        @click="emit('importSubtitle')"
      >
        <span aria-hidden="true">📝</span>
        Import Subtitle
      </button>
    </div>
    <p class="text-xs text-text-muted mt-lg">
      Supports: MP4, MOV, AVI, MP3, WAV, SRT, ASS, VTT
    </p>
    <input
      ref="fileInput"
      type="file"
      multiple
      accept="video/*,audio/*,.srt,.ass,.vtt"
      class="hidden"
      @change="onFileChange"
      aria-label="Upload media files"
    />
  </div>
</template>
