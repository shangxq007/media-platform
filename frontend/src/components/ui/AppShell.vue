<script setup lang="ts">
defineProps<{
  sidebarWidth?: string
}>()
</script>

<template>
  <div class="layout-shell">
    <slot name="sidebar" />
    <div class="layout-main">
      <slot name="header" />
      <div class="layout-content theme-scrollbar">
        <slot />
      </div>
      <slot name="footer" />
    </div>
  </div>
</template>
