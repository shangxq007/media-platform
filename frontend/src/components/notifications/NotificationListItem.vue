<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  id: string
  type: 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS'
  title: string
  message: string
  read: boolean
  createdAt: string
  link?: string
}>()

const emit = defineEmits<{
  markRead: [id: string]
}>()

const typeIcon = computed(() => {
  switch (props.type) {
    case 'SUCCESS': return '✅'
    case 'WARNING': return '⚠️'
    case 'ERROR': return '❌'
    case 'INFO': return 'ℹ️'
    default: return '📌'
  }
})

const sanitizedMessage = computed(() => {
  let msg = props.message
  msg = msg.replace(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/, '***@***.***')
  msg = msg.replace(/\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/, '****-****-****-****')
  return msg
})

function timeAgo(dateStr: string): string {
  try {
    const date = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    if (diffMins < 1) return 'Just now'
    if (diffMins < 60) return `${diffMins}m ago`
    const diffHours = Math.floor(diffMins / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    const diffDays = Math.floor(diffHours / 24)
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString()
  } catch {
    return dateStr
  }
}
</script>

<template>
  <div
    class="flex items-start gap-sm p-sm rounded transition-colors duration-fast"
    :class="!read ? 'bg-primary-500/5' : 'hover:bg-bg-surface-hover'"
  >
    <span class="text-base flex-shrink-0 mt-0.5" aria-hidden="true">{{ typeIcon }}</span>
    <div class="min-w-0 flex-1">
      <div class="flex items-center gap-xs mb-0.5">
        <span class="text-sm truncate-text" :class="!read ? 'font-semibold text-text-primary' : 'font-medium text-text-secondary'">
          {{ title }}
        </span>
        <span v-if="!read" class="w-1.5 h-1.5 rounded-full bg-primary-500 flex-shrink-0" aria-label="Unread" />
      </div>
      <p class="text-xs text-text-muted line-clamp-2">{{ sanitizedMessage }}</p>
      <div class="flex items-center gap-sm mt-xs">
        <span class="text-[10px] text-text-muted">{{ timeAgo(createdAt) }}</span>
        <button
          v-if="!read"
          class="text-[10px] text-primary-500 hover:text-primary-400 transition-colors"
          @click="emit('markRead', id)"
        >
          Mark as read
        </button>
        <router-link
          v-if="link"
          :to="link"
          class="text-[10px] text-primary-500 hover:text-primary-400 transition-colors"
        >
          View
        </router-link>
      </div>
    </div>
  </div>
</template>
