<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  flagKey?: string
  flagKeys?: string[]
  status?: 'ACTIVE' | 'BETA' | 'ROLLOUT' | 'INACTIVE'
  size?: 'sm' | 'md'
}>(), {
  size: 'sm',
})

const displayKeys = computed(() => {
  if (props.flagKeys?.length) return props.flagKeys
  if (props.flagKey) return [props.flagKey]
  return []
})

const statusClass = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'bg-success-muted text-success'
    case 'BETA': return 'bg-accent-500/10 text-accent-300'
    case 'ROLLOUT': return 'bg-info-muted text-info'
    case 'INACTIVE': return 'bg-surface-4/20 text-text-secondary'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
})

const statusLabel = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'ON'
    case 'BETA': return 'BETA'
    case 'ROLLOUT': return 'ROLL'
    case 'INACTIVE': return 'OFF'
    default: return '—'
  }
})
</script>

<template>
  <div v-if="displayKeys.length > 0 || status" class="flex flex-wrap gap-1">
    <span
      v-if="status"
      class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium"
      :class="[statusClass, size === 'md' ? 'text-xs px-2 py-0.5' : '']"
    >
      {{ statusLabel }}
    </span>
    <span
      v-for="key in displayKeys"
      :key="key"
      class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-blue-600/10 text-info border border-info/30"
      :class="size === 'md' ? 'text-xs px-2 py-0.5' : ''"
    >
      🚩 {{ key }}
    </span>
  </div>
</template>
