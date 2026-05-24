<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{
  isAdmin?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const route = useRoute()
const router = useRouter()

const menuItems = computed(() => {
  const items: Array<{
    routeKey: string
    path: string
    label: string
    icon: string
    dividerBefore?: boolean
    isAdmin?: boolean
  }> = [
    { routeKey: 'me-dashboard', path: '/me', label: 'Dashboard', icon: 'layout-dashboard' },
    { routeKey: 'me-projects', path: '/me/projects', label: 'Projects', icon: 'folder-open' },
    { routeKey: 'me-shared-resources', path: '/me/shared-resources', label: 'Shared With Me', icon: 'share-2' },
    { routeKey: 'editor', path: '/', label: 'Video Editor', icon: 'clapperboard' },
    { routeKey: 'me-exports', path: '/me/exports', label: 'Exports', icon: 'upload', dividerBefore: true },
    { routeKey: 'me-capabilities', path: '/me/capabilities', label: 'Capabilities', icon: 'shield', dividerBefore: true },
    { routeKey: 'me-usage', path: '/me/usage', label: 'Usage', icon: 'bar-chart-3' },
    { routeKey: 'me-billing', path: '/me/billing', label: 'Billing', icon: 'credit-card' },
    { routeKey: 'me-credits', path: '/me/credits', label: 'Credits', icon: 'wallet' },
    { routeKey: 'me-reports', path: '/me/reports', label: 'Reports', icon: 'file-text' },
    { routeKey: 'me-feedback', path: '/me/feedback', label: 'Feedback', icon: 'message-circle', dividerBefore: true },
    { routeKey: 'me-notification-settings', path: '/me/notification-settings', label: 'Notification Settings', icon: 'bell' },
    { routeKey: 'me-settings', path: '/me/settings', label: 'Settings', icon: 'settings', dividerBefore: true },
  ]

  if (props.isAdmin) {
    items.push({ routeKey: 'admin-dashboard', path: '/admin', label: 'Admin Console', icon: 'key-round', dividerBefore: true, isAdmin: true })
  }

  return items
})

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}

function handleClick(path: string) {
  router.push(path)
  emit('close')
}
</script>

<template>
  <div class="py-sm min-w-52" role="menu" aria-label="User account menu">
    <template v-for="item in menuItems" :key="item.routeKey">
      <div v-if="item.dividerBefore" class="my-xs border-t border-default" role="separator" />
      <button
        class="w-full flex items-center gap-sm px-md py-sm text-sm transition-colors duration-fast text-left"
        :class="isActive(item.path)
          ? 'bg-primary-500/10 text-primary-400'
          : 'text-text-secondary hover:bg-bg-surface-hover hover:text-text-primary'"
        role="menuitem"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        @click="handleClick(item.path)"
      >
        <AppIcon :name="item.icon" :size="18" class="flex-shrink-0" />
        <span class="truncate-text flex-1">{{ item.label }}</span>
        <span v-if="item.isAdmin" class="theme-badge bg-danger-muted text-danger text-[9px] flex-shrink-0">ADMIN</span>
      </button>
    </template>
  </div>
</template>
