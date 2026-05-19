<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral'
  dot?: boolean
  label?: string
  value?: string | number
  size?: 'sm' | 'md'
}>(), {
  variant: 'neutral',
  dot: false,
  size: 'sm',
})

const colorClasses = computed(() => {
  const map = {
    success: 'bg-success-500/15 text-success-500',
    warning: 'bg-warning-500/15 text-warning-500',
    danger: 'bg-danger-500/15 text-danger-500',
    info: 'bg-info-500/15 text-info-500',
    neutral: 'bg-bg-surface text-text-secondary',
  }
  return map[props.variant]
})

const dotColorClasses = computed(() => {
  const map = {
    success: 'bg-success-500',
    warning: 'bg-warning-500',
    danger: 'bg-danger-500',
    info: 'bg-info-500',
    neutral: 'bg-text-muted',
  }
  return map[props.variant]
})
</script>

<template>
  <span
    class="theme-badge"
    :class="[colorClasses, size === 'md' ? 'text-sm' : 'text-xs']"
  >
    <span v-if="dot" class="w-1.5 h-1.5 rounded-full" :class="dotColorClasses" />
    <slot>{{ label ?? value }}</slot>
  </span>
</template>
