<script setup lang="ts">
withDefaults(defineProps<{
  value: string | number
  label: string
  icon?: string
  trendValue?: string
  trendDirection?: 'up' | 'down' | 'neutral'
  trendLabel?: string
  loading?: boolean
}>(), {
  trendDirection: 'neutral',
  loading: false,
})

defineSlots<{
  icon?: () => unknown
  trend?: () => unknown
  footer?: () => unknown
}>()
</script>

<template>
  <div class="c-metric-card">
    <div class="flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <div v-if="loading" class="h-8 w-24 bg-bg-surface rounded animate-pulse mb-xs" />
        <div v-else class="c-metric-value">{{ value }}</div>
        <div class="c-metric-label">{{ label }}</div>
      </div>
      <div v-if="icon || $slots.icon" class="text-2xl flex-shrink-0 ml-md">
        <slot name="icon">{{ icon }}</slot>
      </div>
    </div>

    <div v-if="trendValue || $slots.trend" class="c-metric-trend" :class="{
      'c-metric-trend-up': trendDirection === 'up',
      'c-metric-trend-down': trendDirection === 'down',
      'text-text-muted': trendDirection === 'neutral',
    }">
      <slot name="trend">
        <span v-if="trendDirection === 'up'">↑</span>
        <span v-else-if="trendDirection === 'down'">↓</span>
        <span v-else>—</span>
        <span>{{ trendValue }}</span>
        <span v-if="trendLabel" class="text-text-muted">{{ trendLabel }}</span>
      </slot>
    </div>

    <slot name="footer" />
  </div>
</template>
