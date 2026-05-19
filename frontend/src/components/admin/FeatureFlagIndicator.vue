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
    case 'ACTIVE': return 'bg-green-600/20 text-green-300'
    case 'BETA': return 'bg-purple-600/20 text-purple-300'
    case 'ROLLOUT': return 'bg-blue-600/20 text-blue-300'
    case 'INACTIVE': return 'bg-gray-600/20 text-gray-400'
    default: return 'bg-gray-600/20 text-gray-400'
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
      class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-blue-600/10 text-blue-300 border border-blue-700/30"
      :class="size === 'md' ? 'text-xs px-2 py-0.5' : ''"
    >
      🚩 {{ key }}
    </span>
  </div>
</template>
