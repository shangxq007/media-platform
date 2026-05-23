<script setup lang="ts">
import { ref, computed } from 'vue'
import { useTimelineStore } from '@/stores/timeline'
import { useHistoryStore } from '@/stores/history'
import type { Clip, UploadItem } from '@/types'
import { probeMediaFile, getFileType } from '@/utils/mediaProbe'
import EmptyProjectGuide from '@/components/editor/EmptyProjectGuide.vue'
import MediaUploadDropzone from '@/components/upload/MediaUploadDropzone.vue'
import UploadProgressList from '@/components/upload/UploadProgressList.vue'

defineEmits<{
  tryDemo: []
  importSubtitle: []
}>()

const timelineStore = useTimelineStore()
const historyStore = useHistoryStore()

const searchQuery = ref('')
const selectedType = ref<'all' | 'video' | 'audio' | 'text' | 'image' | 'subtitle'>('all')
const uploadItems = ref<UploadItem[]>([])

function onFilesSelected(files: File[]) {
  files.forEach(file => {
    const id = `upload_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const item: UploadItem = {
      id,
      file,
      name: file.name,
      progress: 0,
      status: 'uploading',
    }
    uploadItems.value.push(item)
    processFile(item)
  })
}

async function processFile(item: UploadItem) {
  const type = getFileType(item.file)

  item.progress = 30

  const clip: Clip = {
    id: `clip_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: item.file.name,
    type,
    sourceUrl: item.file.type.startsWith('image/') || item.file.type.startsWith('video/')
      ? URL.createObjectURL(item.file)
      : undefined,
    duration: type === 'image' ? 5 : 0,
    startTime: 0,
    endTime: type === 'image' ? 5 : 0,
    metadata: { size: String(item.file.size), mimeType: item.file.type },
    fileSize: item.file.size,
    uploadStatus: 'probing',
  }

  item.progress = 60
  item.clipId = clip.id

  try {
    const probe = await probeMediaFile(item.file)
    if (probe.error) {
      clip.uploadStatus = 'error'
      clip.probeError = probe.error
    } else {
      clip.uploadStatus = 'ready'
      if (probe.duration !== undefined) {
        clip.duration = probe.duration
        clip.endTime = probe.duration
      }
      if (probe.width !== undefined) {
        clip.width = probe.width
        clip.metadata.width = String(probe.width)
      }
      if (probe.height !== undefined) {
        clip.height = probe.height
        clip.metadata.height = String(probe.height)
      }
      if (probe.cueCount !== undefined) {
        clip.metadata.cueCount = String(probe.cueCount)
      }
    }
  } catch {
    clip.uploadStatus = 'error'
    clip.probeError = 'Metadata extraction failed'
  }

  timelineStore.clips.push(clip)

  item.progress = 100
  item.status = 'success'
}

function cancelUpload(id: string) {
  const idx = uploadItems.value.findIndex(i => i.id === id)
  if (idx >= 0) {
    uploadItems.value[idx].status = 'cancelled'
  }
}

function clearCompletedUploads() {
  uploadItems.value = uploadItems.value.filter(i => i.status === 'uploading')
}

const allClips = computed(() => {
  let clips = [...timelineStore.clips]
  if (selectedType.value !== 'all') {
    clips = clips.filter(c => c.type === selectedType.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    clips = clips.filter(c => c.name.toLowerCase().includes(q))
  }
  return clips
})

function formatDuration(seconds: number): string {
  if (!seconds || seconds <= 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function getTypeIcon(type: string): string {
  switch (type) {
    case 'video': return '🎬'
    case 'audio': return '🎵'
    case 'image': return '🖼️'
    case 'subtitle': return '📝'
    case 'text': return '📄'
    default: return '📄'
  }
}

function getTypeColor(type: string): string {
  switch (type) {
    case 'video': return 'border-clip-video bg-clip-video/10'
    case 'audio': return 'border-clip-audio bg-clip-audio/10'
    case 'text': return 'border-clip-text bg-clip-text/10'
    case 'image': return 'border-purple-500 bg-purple-500/10'
    case 'subtitle': return 'border-yellow-500 bg-yellow-500/10'
    default: return 'border-gray-500 bg-gray-500/10'
  }
}

function getThumbnailColor(type: string): string {
  switch (type) {
    case 'video': return 'bg-blue-900'
    case 'audio': return 'bg-green-900'
    case 'image': return 'bg-purple-900'
    case 'subtitle': return 'bg-yellow-900'
    default: return 'bg-gray-700'
  }
}

function insertClipAtPlayhead(clip: Clip) {
  historyStore.saveState(timelineStore)
  const trackType = clip.type === 'image' ? 'video' : clip.type === 'subtitle' ? 'text' : clip.type
  timelineStore.insertClipAtPlayhead(clip, trackType)
}

function deleteClip(clipId: string) {
  const idx = timelineStore.clips.findIndex(c => c.id === clipId)
  if (idx >= 0) timelineStore.clips.splice(idx, 1)
  timelineStore.state.tracks.forEach(track => {
    const tcIdx = track.clips.findIndex(tc => tc.clipId === clipId)
    if (tcIdx >= 0) track.clips.splice(tcIdx, 1)
  })
}

function onDragStart(e: DragEvent, clip: Clip) {
  e.dataTransfer?.setData('clipId', clip.id)
}

function getResolution(clip: Clip): string {
  if (clip.width && clip.height) return `${clip.width}×${clip.height}`
  if (clip.metadata.width && clip.metadata.height) return `${clip.metadata.width}×${clip.metadata.height}`
  return ''
}

function getMetadataStatus(clip: Clip): string {
  if (clip.uploadStatus === 'probing') return 'Pending'
  if (clip.uploadStatus === 'error') return 'Unknown'
  return ''
}
</script>

<template>
  <div class="flex flex-col h-full">
    <div class="p-2 border-b border-gray-700">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search clips..."
        class="w-full px-2 py-1 bg-gray-800 border border-gray-600 rounded text-sm text-white placeholder-gray-500"
      />
      <div class="flex gap-1 mt-2 flex-wrap">
        <button
          v-for="t in (['all', 'video', 'audio', 'image', 'subtitle', 'text'] as const)"
          :key="t"
          class="px-2 py-0.5 text-xs rounded capitalize"
          :class="selectedType === t ? 'bg-clip-video text-white' : 'bg-gray-700 text-gray-400'"
          @click="selectedType = t"
        >
          {{ t }}
        </button>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto p-2 space-y-1">
      <div
        v-for="clip in allClips"
        :key="clip.id"
        class="flex items-center gap-2 p-2 rounded border cursor-pointer hover:bg-gray-700/50 transition-colors"
        :class="getTypeColor(clip.type)"
        draggable="true"
        @dragstart="onDragStart($event, clip)"
      >
        <div
          :class="getThumbnailColor(clip.type)"
          class="w-10 h-8 rounded flex items-center justify-center flex-shrink-0"
        >
          <span class="text-sm" aria-hidden="true">{{ getTypeIcon(clip.type) }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-1">
            <span class="text-xs text-white truncate">{{ clip.name }}</span>
            <span
              v-if="getMetadataStatus(clip)"
              class="text-[9px] px-1 rounded"
              :class="clip.uploadStatus === 'probing' ? 'bg-yellow-600/30 text-yellow-400' : 'bg-red-600/30 text-red-400'"
            >
              {{ getMetadataStatus(clip) }}
            </span>
          </div>
          <div class="flex items-center gap-2 text-[10px] text-gray-500">
            <span>{{ formatDuration(clip.duration) }}</span>
            <span v-if="clip.fileSize">{{ formatFileSize(clip.fileSize) }}</span>
            <span v-if="getResolution(clip)">{{ getResolution(clip) }}</span>
          </div>
        </div>
        <span class="text-[9px] px-1.5 py-0.5 rounded bg-gray-700 text-gray-400 capitalize flex-shrink-0">
          {{ clip.type }}
        </span>
        <div class="flex flex-col gap-0.5 flex-shrink-0">
          <button
            class="text-green-400 hover:text-green-300 text-xs"
            title="Insert at playhead"
            @click.stop="insertClipAtPlayhead(clip)"
          >➕</button>
          <button
            class="text-red-400 hover:text-red-300 text-xs"
            title="Delete clip"
            @click.stop="deleteClip(clip.id)"
          >✕</button>
        </div>
      </div>

      <EmptyProjectGuide
        v-if="!allClips.length && !uploadItems.length"
        @upload="onFilesSelected"
        @try-demo="$emit('tryDemo')"
        @import-subtitle="$emit('importSubtitle')"
      />
    </div>

    <div class="p-2 border-t border-gray-700 space-y-2">
      <MediaUploadDropzone @files-selected="onFilesSelected" />
      <UploadProgressList
        :items="uploadItems.filter(i => i.status === 'uploading' || i.status === 'failed')"
        @cancel="cancelUpload"
      />
      <button
        v-if="uploadItems.some(i => i.status === 'success')"
        class="text-[10px] text-gray-500 hover:text-gray-400 w-full text-center"
        @click="clearCompletedUploads"
      >
        Clear completed
      </button>
    </div>
  </div>
</template>
