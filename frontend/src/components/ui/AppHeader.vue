<script setup lang="ts">
import AppIcon from '@/components/ui/AppIcon.vue'

defineProps<{
  logoText?: string
  logoTo?: string
  showHamburger?: boolean
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
      v-if="showHamburger"
      class="theme-btn theme-btn-ghost theme-btn-sm flex-shrink-0"
      aria-label="Toggle sidebar"
      @click="$emit('toggleSidebar')"
    >
      <AppIcon name="panel-left-open" :size="20" />
    </button>

    <div class="flex items-center gap-sm flex-shrink-0 min-w-0">
      <slot name="logo">
        <router-link :to="logoTo || '/me'" class="flex items-center gap-sm no-underline min-w-0">
          <span class="flex h-7 w-7 items-center justify-center rounded-md bg-primary-500/15 text-primary-400">
            <AppIcon name="film" :size="16" />
          </span>
          <span v-if="logoText" class="text-sm font-semibold text-text-primary truncate-text hidden sm:inline">
            {{ logoText }}
          </span>
        </router-link>
      </slot>
    </div>

    <slot name="workspace" />

    <div class="flex-1" />

    <div class="flex items-center gap-xs flex-shrink-0">
      <slot name="monitoring" />
      <slot name="notifications" />
      <slot name="feedback" />
      <slot name="actions" />
      <slot name="userMenu" />
    </div>
  </header>
</template>
