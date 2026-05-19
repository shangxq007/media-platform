<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()

defineSlots<{
  breadcrumb?: () => unknown
  actions?: () => unknown
  extra?: () => unknown
}>()
</script>

<template>
  <div class="page-header">
    <div class="flex-1 min-w-0">
      <slot name="breadcrumb" />
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-actions">
      <slot name="actions" />
    </div>
    <slot name="extra" />
  </div>
</template>
