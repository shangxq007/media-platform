<script setup lang="ts">
withDefaults(defineProps<{
  workerName?: string
  workerId?: string
  showRetry?: boolean
  showFallback?: boolean
}>(), {
  workerName: 'Render Worker',
  showRetry: true,
  showFallback: true,
})

defineEmits<{
  retry: []
  useFallback: []
}>()
</script>

<template>
  <div class="c-state-page" role="alert" aria-labelledby="worker-title">
    <div class="c-state-icon" aria-hidden="true">
      <svg class="w-16 h-16 text-danger-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
          d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
      </svg>
    </div>
    <h2 id="worker-title" class="c-state-title">{{ workerName }} Offline</h2>
    <p class="c-state-description">
      The {{ workerName }} is currently offline and cannot process requests. You can retry or use the local fallback.
    </p>

    <div v-if="workerId" class="mt-md flex items-center gap-sm">
      <span class="text-xs text-text-muted">Worker ID:</span>
      <code class="text-xs font-mono bg-bg-surface px-sm py-xs rounded text-text-primary">{{ workerId }}</code>
    </div>

    <div class="flex items-center gap-sm mt-md">
      <span class="w-2 h-2 rounded-full bg-danger-500 animate-pulse" aria-hidden="true" />
      <span class="text-xs font-medium text-danger-500">Offline</span>
    </div>

    <div class="c-state-actions">
      <button v-if="showRetry" class="theme-btn theme-btn-primary" @click="$emit('retry')">
        Retry Connection
      </button>
      <button v-if="showFallback" class="theme-btn theme-btn-secondary" @click="$emit('useFallback')">
        Use Local Fallback
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
