<script setup lang="ts">
defineProps<{
  riskLevel?: string
  action?: string
}>()

function getRiskClass(level?: string): string {
  switch (level) {
    case 'CRITICAL': return 'bg-red-600 text-white'
    case 'HIGH': return 'bg-orange-500 text-white'
    case 'MEDIUM': return 'bg-yellow-500 text-black'
    case 'LOW': return 'bg-green-500 text-white'
    default: return 'bg-surface-4 text-white'
  }
}

function getActionIcon(action?: string): string {
  switch (action) {
    case 'BLOCK': return '🚫'
    case 'REQUIRE_REVIEW': return '⚠️'
    case 'WARN': return '⚡'
    case 'ALLOW': return '✅'
    default: return '❓'
  }
}
</script>

<template>
  <div v-if="riskLevel" class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-medium"
    :class="getRiskClass(riskLevel)">
    <span>{{ getActionIcon(action) }}</span>
    <span>{{ riskLevel }}</span>
    <span v-if="action && action !== 'ALLOW'" class="opacity-75">({{ action }})</span>
  </div>
</template>
