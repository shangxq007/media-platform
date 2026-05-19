<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  quotaType?: string
  quotaLabel?: string
  currentUsage?: number
  limit?: number
  unit?: string
  showUpgrade?: boolean
  showRequestIncrease?: boolean
}>(), {
  quotaType: 'usage',
  quotaLabel: 'Usage',
  currentUsage: 0,
  limit: 0,
  unit: '',
  showUpgrade: true,
  showRequestIncrease: true,
})

defineEmits<{
  upgrade: []
  requestIncrease: []
}>()

const percentage = computed(() => {
  if (!props.limit || props.limit <= 0) return 100
  return Math.min(100, Math.round((props.currentUsage / props.limit) * 100))
})

const formattedUsage = computed(() => `${props.currentUsage.toLocaleString()}${props.unit}`)
const formattedLimit = computed(() => `${props.limit.toLocaleString()}${props.unit}`)
</script>

<template>
  <div class="c-state-page" role="alert" aria-labelledby="quota-title">
    <div class="c-state-icon" aria-hidden="true">
      <svg class="w-16 h-16 text-warning-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
          d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
      </svg>
    </div>
    <h2 id="quota-title" class="c-state-title">{{ quotaLabel }} limit reached</h2>
    <p class="c-state-description">
      You've used {{ formattedUsage }} out of your {{ formattedLimit }} {{ quotaType }} limit.
    </p>

    <div class="c-quota-bar" role="progressbar" :aria-valuenow="percentage" aria-valuemin="0" aria-valuemax="100"
      :aria-label="`${quotaLabel} usage: ${percentage} percent`">
      <div class="c-quota-bar-fill c-quota-bar-fill-exceeded" :style="{ width: `${percentage}%` }" />
    </div>
    <div class="flex justify-between text-xs text-text-muted mt-xs">
      <span>{{ formattedUsage }} used</span>
      <span>{{ formattedLimit }} limit</span>
    </div>

    <div class="c-state-actions">
      <button v-if="showUpgrade" class="theme-btn theme-btn-primary" @click="$emit('upgrade')">
        Upgrade for More
      </button>
      <button v-if="showRequestIncrease" class="theme-btn theme-btn-secondary" @click="$emit('requestIncrease')">
        Request Increase
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
