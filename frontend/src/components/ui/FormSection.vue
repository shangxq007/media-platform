<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  columns?: 1 | 2 | 3
}>(), {
  columns: 1,
})

defineSlots<{
  default: () => unknown
  header?: () => unknown
  footer?: () => unknown
}>()
</script>

<template>
  <div>
    <slot name="header">
      <div v-if="title || description" class="mb-md">
        <h3 v-if="title" class="text-base font-semibold text-text-primary">{{ title }}</h3>
        <p v-if="description" class="text-sm text-text-secondary mt-xs">{{ description }}</p>
      </div>
    </slot>

    <div
      class="grid gap-md"
      :class="{
        'grid-cols-1': columns === 1,
        'grid-cols-1 md:grid-cols-2': columns === 2,
        'grid-cols-1 md:grid-cols-3': columns === 3,
      }"
    >
      <slot />
    </div>

    <div v-if="$slots.footer" class="mt-md pt-md border-t border-default">
      <slot name="footer" />
    </div>
  </div>
</template>
