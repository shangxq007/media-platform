<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  feature: string
  variant?: 'default' | 'premium' | 'enterprise' | 'beta'
  label?: string
  showIcon?: boolean
}>(), {
  variant: 'default',
  showIcon: true,
})

const variantClasses = computed(() => {
  const map = {
    default: 'bg-bg-surface text-text-secondary border border-default',
    premium: 'bg-primary-500/10 text-primary-500 border border-primary-200',
    enterprise: 'bg-warning-500/10 text-warning-600 border border-warning-200',
    beta: 'bg-info-500/10 text-info-500 border border-info-200',
  }
  return map[props.variant]
})

const icon = computed(() => {
  const map = {
    default: '✦',
    premium: '★',
    enterprise: '◆',
    beta: '⚗',
  }
  return map[props.variant]
})
</script>

<template>
  <span class="theme-badge text-xs" :class="variantClasses">
    <span v-if="showIcon" class="flex-shrink-0">{{ icon }}</span>
    <slot>{{ label ?? feature }}</slot>
  </span>
</template>
