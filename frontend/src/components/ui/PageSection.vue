<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  compact?: boolean
}>(), {
  compact: false,
})

defineSlots<{
  default: () => unknown
  actions?: () => unknown
  header?: () => unknown
}>()
</script>

<template>
  <section class="section">
    <slot name="header">
      <div v-if="title || $slots.actions" class="section-header">
        <div>
          <h2 v-if="title" class="section-title">{{ title }}</h2>
          <p v-if="description" class="section-description">{{ description }}</p>
        </div>
        <div class="flex items-center gap-sm">
          <slot name="actions" />
        </div>
      </div>
    </slot>
    <div :class="{ 'c-card': !compact }">
      <slot />
    </div>
  </section>
</template>
