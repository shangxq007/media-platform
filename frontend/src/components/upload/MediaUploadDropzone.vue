<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  'files-selected': [files: File[]]
}>()

const isDragging = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const ACCEPTED_TYPES = [
  'video/*',
  'audio/*',
  'image/*',
  '.srt',
  '.ass',
  '.vtt',
  '.json',
]

function onDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function onDragLeave() {
  isDragging.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  if (e.dataTransfer?.files.length) {
    emit('files-selected', Array.from(e.dataTransfer.files))
  }
}

function onClick() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) {
    emit('files-selected', Array.from(input.files))
  }
  input.value = ''
}
</script>

<template>
  <div
    class="border-2 border-dashed rounded-lg p-4 text-center cursor-pointer transition-colors"
    :class="isDragging
      ? 'border-primary-400 bg-primary-500/10'
      : 'border-gray-600 hover:border-gray-500 hover:bg-gray-800/50'"
    tabindex="0"
    role="button"
    aria-label="Upload media files"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
    @click="onClick"
    @keydown.enter.prevent="onClick"
    @keydown.space.prevent="onClick"
  >
    <div class="text-2xl mb-1" aria-hidden="true">📁</div>
    <p class="text-xs text-gray-400">
      <span v-if="isDragging">Drop files here</span>
      <span v-else>Drag & drop files or <span class="text-primary-400 underline">browse</span></span>
    </p>
    <p class="text-[10px] text-gray-600 mt-1">
      Video, Audio, Image, SRT, ASS, VTT, JSON
    </p>
    <input
      ref="fileInputRef"
      type="file"
      multiple
      :accept="ACCEPTED_TYPES.join(',')"
      class="hidden"
      @change="onFileChange"
    />
  </div>
</template>
