<script setup lang="ts">
withDefaults(defineProps<{
  icon?: string
  title: string
  description?: string
}>(), {
  icon: '📭',
})

defineSlots<{
  default?: () => unknown
  icon?: () => unknown
  action?: () => unknown
}>()
</script>

<template>
  <div class="c-empty-state">
    <div class="c-empty-state-icon">
      <slot name="icon">{{ icon }}</slot>
    </div>
    <div class="c-empty-state-title">{{ title }}</div>
    <div v-if="description" class="c-empty-state-description">{{ description }}</div>
    <slot />
    <div v-if="$slots.action" class="mt-md">
      <slot name="action" />
    </div>
  </div>
</template>
