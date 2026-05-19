<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  providerName?: string
  status?: 'offline' | 'degraded' | 'maintenance'
  showRetry?: boolean
  alternativeProviders?: string[]
}>(), {
  providerName: 'Provider',
  status: 'offline',
  showRetry: true,
})

const emit = defineEmits<{
  retry: []
  selectAlternative: [provider: string]
}>()

const statusLabel = computed(() => {
  switch (props.status) {
    case 'offline': return 'Offline'
    case 'degraded': return 'Degraded'
    case 'maintenance': return 'Maintenance'
    default: return 'Unavailable'
  }
})

const statusColor = computed(() => {
  switch (props.status) {
    case 'offline': return 'bg-danger-500'
    case 'degraded': return 'bg-warning-500'
    case 'maintenance': return 'bg-info-500'
    default: return 'bg-text-muted'
  }
})
</script>

<template>
  <div class="c-state-page" role="alert" aria-labelledby="provider-title">
    <div class="c-state-icon" aria-hidden="true">
      <svg class="w-16 h-16 text-danger-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
          d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
      </svg>
    </div>
    <h2 id="provider-title" class="c-state-title">{{ providerName }} is {{ statusLabel }}</h2>
    <p class="c-state-description">
      The {{ providerName }} service is currently {{ statusLabel.toLowerCase() }}. Please try again later or use an alternative provider.
    </p>

    <div class="flex items-center gap-sm mt-md">
      <span class="w-2 h-2 rounded-full" :class="statusColor" aria-hidden="true" />
      <span class="text-xs font-medium text-text-secondary">{{ statusLabel }}</span>
    </div>

    <div v-if="alternativeProviders && alternativeProviders.length > 0" class="mt-md w-full max-w-xs">
      <div class="text-xs font-medium text-text-muted mb-sm">Alternative providers:</div>
      <div class="flex flex-wrap gap-sm">
        <button
          v-for="provider in alternativeProviders"
          :key="provider"
          class="theme-btn theme-btn-secondary theme-btn-sm"
          @click="emit('selectAlternative', provider)"
        >
          {{ provider }}
        </button>
      </div>
    </div>

    <div class="c-state-actions">
      <button v-if="showRetry" class="theme-btn theme-btn-primary" @click="emit('retry')">
        Retry
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
