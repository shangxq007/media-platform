<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  level: 'low' | 'medium' | 'high' | 'critical'
  label?: string
  dot?: boolean
  bordered?: boolean
}>(), {
  dot: true,
  bordered: false,
})

const colorClasses = computed(() => {
  const map = {
    low: 'text-risk-low bg-risk-low/10',
    medium: 'text-risk-medium bg-risk-medium/10',
    high: 'text-risk-high bg-risk-high/10',
    critical: 'text-risk-critical bg-risk-critical/10',
  }
  return map[props.level]
})

const dotColorClasses = computed(() => {
  const map = {
    low: 'bg-risk-low',
    medium: 'bg-risk-medium',
    high: 'bg-risk-high',
    critical: 'bg-risk-critical',
  }
  return map[props.level]
})

const displayLabel = computed(() => {
  if (props.label) return props.label
  return props.level.charAt(0).toUpperCase() + props.level.slice(1)
})
</script>

<template>
  <span
    class="theme-badge"
    :class="[
      colorClasses,
      bordered ? 'border border-current' : '',
    ]"
  >
    <span v-if="dot" class="w-1.5 h-1.5 rounded-full flex-shrink-0" :class="dotColorClasses" />
    <slot>{{ displayLabel }}</slot>
  </span>
</template>
