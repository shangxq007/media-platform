<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

interface NavItem {
  key: string
  label: string
  icon: string
  path: string
}

const navItems: NavItem[] = [
  { key: 'dashboard', label: 'Dashboard', icon: '📊', path: '/me' },
  { key: 'projects', label: 'Projects', icon: '📁', path: '/me/projects' },
  { key: 'editor', label: 'Editor', icon: '✂', path: '/' },
  { key: 'prompts', label: 'Prompts', icon: '🤖', path: '/prompts' },
  { key: 'effect-packs', label: 'Effect Packs', icon: '✨', path: '/effect-packs' },
  { key: 'capabilities', label: 'Capabilities', icon: '🛡', path: '/me/capabilities' },
  { key: 'usage', label: 'Usage', icon: '📈', path: '/me/usage' },
  { key: 'billing', label: 'Billing', icon: '💳', path: '/me/billing' },
  { key: 'credits', label: 'Credits', icon: '💰', path: '/me/credits' },
  { key: 'feedback', label: 'Feedback', icon: '💬', path: '/me/feedback' },
  { key: 'settings', label: 'Settings', icon: '⚙️', path: '/me/settings' },
]

const isActive = (path: string) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const currentLabel = computed(() => {
  const item = navItems.find(i => isActive(i.path))
  return item?.label || 'Dashboard'
})
</script>

<template>
  <nav class="layout-sidebar" aria-label="User navigation" style="width: 220px;">
    <div class="layout-header">
      <div class="flex items-center gap-sm">
        <span class="text-lg">🎬</span>
        <span class="text-sm font-semibold text-text-primary truncate-text">{{ currentLabel }}</span>
      </div>
    </div>
    <div class="flex-1 overflow-y-auto theme-scrollbar py-sm">
      <div class="px-sm space-y-xs">
        <router-link
          v-for="item in navItems"
          :key="item.key"
          :to="item.path"
          class="flex items-center gap-sm px-sm py-sm rounded-md text-sm transition-colors"
          :class="isActive(item.path)
            ? 'bg-primary-500/10 text-primary-500 font-medium'
            : 'text-text-secondary hover:text-text-primary hover:bg-bg-surface-hover'"
        >
          <span class="text-base flex-shrink-0" aria-hidden="true">{{ item.icon }}</span>
          <span class="truncate-text">{{ item.label }}</span>
        </router-link>
      </div>
    </div>
    <div class="px-sm py-sm border-t border-default">
      <router-link
        to="/admin"
        class="flex items-center gap-sm px-sm py-sm rounded-md text-sm text-text-muted hover:text-text-secondary transition-colors"
      >
        <span class="text-base">🔒</span>
        <span>Admin</span>
      </router-link>
    </div>
  </nav>
</template>
