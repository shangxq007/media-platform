<script setup lang="ts">
import type { UploadItem } from '@/types'

defineProps<{
  items: UploadItem[]
}>()

const emit = defineEmits<{
  cancel: [id: string]
}>()

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function getStatusColor(status: UploadItem['status']): string {
  switch (status) {
    case 'uploading': return 'bg-primary-500'
    case 'success': return 'bg-green-500'
    case 'failed': return 'bg-red-500'
    case 'cancelled': return 'bg-gray-500'
  }
}

function getStatusLabel(status: UploadItem['status']): string {
  switch (status) {
    case 'uploading': return 'Uploading'
    case 'success': return 'Done'
    case 'failed': return 'Failed'
    case 'cancelled': return 'Cancelled'
  }
}
</script>

<template>
  <div v-if="items.length" class="space-y-2">
    <div
      v-for="item in items"
      :key="item.id"
      class="p-2 rounded bg-gray-800/50 border border-gray-700"
    >
      <div class="flex items-center justify-between gap-2">
        <span class="text-xs text-white truncate flex-1" :title="item.name">{{ item.name }}</span>
        <span class="text-[10px] text-gray-500 flex-shrink-0">{{ formatFileSize(item.file.size) }}</span>
        <button
          v-if="item.status === 'uploading'"
          class="text-gray-500 hover:text-red-400 text-xs flex-shrink-0"
          aria-label="Cancel upload"
          @click="emit('cancel', item.id)"
        >
          ✕
        </button>
      </div>
      <div class="mt-1 h-1 bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full rounded-full transition-all duration-200"
          :class="getStatusColor(item.status)"
          :style="{ width: item.status === 'success' ? '100%' : `${item.progress}%` }"
        />
      </div>
      <div class="flex items-center justify-between mt-0.5">
        <span class="text-[10px]" :class="item.status === 'failed' ? 'text-red-400' : 'text-gray-500'">
          {{ item.error || getStatusLabel(item.status) }}
        </span>
        <span v-if="item.status === 'uploading'" class="text-[10px] text-gray-500">
          {{ item.progress }}%
        </span>
      </div>
    </div>
  </div>
</template>
