<script setup lang="ts">
defineProps<{
  logoText?: string
  logoIcon?: string
}>()

defineEmits<{
  toggleSidebar: []
}>()

defineSlots<{
  logo?: () => unknown
  workspace?: () => unknown
  monitoring?: () => unknown
  notifications?: () => unknown
  feedback?: () => unknown
  userMenu?: () => unknown
  actions?: () => unknown
}>()
</script>

<template>
  <header class="layout-header">
    <button
      class="theme-btn theme-btn-ghost theme-btn-sm flex-shrink-0"
      @click="$emit('toggleSidebar')"
    >
      <span class="text-base">☰</span>
    </button>

    <div class="flex items-center gap-sm flex-shrink-0">
      <slot name="logo">
        <span class="text-lg font-semibold text-text-primary">
          {{ logoIcon }} {{ logoText }}
        </span>
      </slot>
    </div>

    <slot name="workspace" />

    <div class="flex-1" />

    <div class="flex items-center gap-sm flex-shrink-0">
      <slot name="monitoring" />
      <slot name="notifications" />
      <slot name="feedback" />
      <slot name="actions" />
      <slot name="userMenu" />
    </div>
  </header>
</template>
