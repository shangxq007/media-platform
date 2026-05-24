<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Artifact } from '@/types'

const props = defineProps<{
  artifact: Artifact
}>()

const emit = defineEmits<{
  preview: [artifact: Artifact]
  download: [artifact: Artifact]
  copyId: [id: string]
  openCatalog: [catalogId: string]
  viewLogs: [url: string]
}>()

const copied = ref(false)

const formattedSize = computed(() => {
  const bytes = props.artifact.fileSize
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
})

const formattedDuration = computed(() => {
  const s = Math.floor(props.artifact.duration)
  const m = Math.floor(s / 60)
  const rem = s % 60
  return `${m}:${rem.toString().padStart(2, '0')}`
})

const formattedDate = computed(() => {
  return new Date(props.artifact.createdAt).toLocaleString()
})

const isVideo = computed(() => ['mp4', 'webm', 'mov', 'mkv'].includes(props.artifact.outputFormat))
const isAudio = computed(() => ['mp3', 'wav', 'aac', 'ogg'].includes(props.artifact.outputFormat))
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'webp'].includes(props.artifact.outputFormat))

function copyId() {
  navigator.clipboard.writeText(props.artifact.id)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
  emit('copyId', props.artifact.id)
}
</script>

<template>
  <div class="p-2 rounded bg-surface-2/50 border border-border-subtle space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-xs text-text-secondary font-medium">Export Result</span>
      <span class="px-1.5 py-0.5 rounded text-[10px] font-medium bg-success-muted text-success">
        Completed
      </span>
    </div>

    <div class="flex items-center gap-2 p-1.5 rounded bg-surface-0/40">
      <div class="w-10 h-10 rounded bg-surface-3 flex items-center justify-center flex-shrink-0">
        <span class="text-lg" v-if="isVideo">🎬</span>
        <span class="text-lg" v-else-if="isAudio">🎵</span>
        <span class="text-lg" v-else-if="isImage">🖼️</span>
        <span class="text-lg" v-else>📄</span>
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-xs text-white truncate">{{ artifact.name }}</div>
        <div class="text-[10px] text-text-tertiary truncate">
          {{ artifact.outputFormat.toUpperCase() }} · {{ formattedDuration }} · {{ formattedSize }}
        </div>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-x-3 gap-y-1 text-[10px]">
      <div class="flex justify-between">
        <span class="text-text-tertiary">Provider</span>
        <span class="text-text-primary">{{ artifact.provider }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-tertiary">Format</span>
        <span class="text-text-primary">{{ artifact.outputFormat }}</span>
      </div>
      <div v-if="artifact.width && artifact.height" class="flex justify-between">
        <span class="text-text-tertiary">Resolution</span>
        <span class="text-text-primary">{{ artifact.width }}×{{ artifact.height }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-text-tertiary">Created</span>
        <span class="text-text-primary">{{ formattedDate }}</span>
      </div>
    </div>

    <div class="flex items-center gap-1">
      <code class="text-[10px] text-text-secondary font-mono bg-surface-0/50 px-1 py-0.5 rounded flex-1 truncate">
        {{ artifact.id }}
      </code>
      <button
        class="text-[10px] text-info hover:text-info px-1"
        @click="copyId"
      >
        {{ copied ? '✓' : '📋' }}
      </button>
    </div>

    <div class="flex flex-wrap gap-1.5 pt-1 border-t border-border-subtle">
      <button
        class="flex-1 px-2 py-1 text-[10px] bg-info-muted text-info rounded hover:bg-blue-600/30"
        @click="$emit('preview', artifact)"
      >
        👁 Preview
      </button>
      <button
        class="flex-1 px-2 py-1 text-[10px] bg-success-muted text-success rounded hover:bg-green-600/30"
        @click="$emit('download', artifact)"
      >
        ⬇ Download
      </button>
    </div>

    <div class="flex flex-wrap gap-1.5">
      <button
        v-if="artifact.catalogId"
        class="flex-1 px-2 py-1 text-[10px] bg-accent-500/10 text-accent-400 rounded hover:bg-purple-600/30"
        @click="$emit('openCatalog', artifact.catalogId!)"
      >
        📂 Open in Catalog
      </button>
      <a
        v-if="artifact.renderLogsUrl"
        :href="artifact.renderLogsUrl"
        target="_blank"
        class="flex-1 px-2 py-1 text-[10px] bg-surface-4/20 text-text-secondary rounded hover:bg-surface-4/30 text-center"
      >
        📋 Render Logs
      </a>
    </div>
  </div>
</template>
