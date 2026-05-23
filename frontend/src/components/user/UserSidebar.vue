<script setup lang="ts">
import { useRoute } from 'vue-router'
import { userNavItems, type NavItem } from '@/config/navigation'
import AppIcon from '@/components/ui/AppIcon.vue'

const props = defineProps<{
  collapsed?: boolean
}>()

const emit = defineEmits<{
  toggle: []
  navigate: []
}>()

const route = useRoute()

const isActive = (path: string) => {
  if (!path) return false
  if (path === '/') return route.path === '/' || route.path.startsWith('/project/')
  if (path === '/me') return route.path === '/me'
  if (route.path === path) return true
  return route.path.startsWith(path + '/')
}

function handleNavClick() {
  emit('navigate')
}

function showSectionLabel(item: NavItem, index: number): boolean {
  if (props.collapsed || !item.section) return false
  const prev = userNavItems[index - 1]
  return !prev?.section || prev.section !== item.section
}
</script>

<template>
  <nav class="layout-sidebar" aria-label="User navigation" :style="{ width: props.collapsed ? '56px' : '240px' }">
    <div class="layout-header">
      <router-link to="/me" class="flex items-center gap-sm no-underline min-w-0 group" @click="handleNavClick">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-500/15 text-primary-400 flex-shrink-0">
          <AppIcon name="film" :size="18" />
        </span>
        <span v-if="!props.collapsed" class="text-sm font-semibold text-text-primary truncate-text group-hover:text-primary-400 transition-colors">
          Media Platform
        </span>
      </router-link>
    </div>
    <div class="flex-1 overflow-y-auto theme-scrollbar py-sm">
      <div class="px-sm space-y-0.5">
        <template v-for="(item, index) in userNavItems" :key="item.key">
          <div v-if="item.divider" class="border-t border-default my-sm mx-sm" role="separator" />
          <div
            v-else-if="showSectionLabel(item, index)"
            class="px-sm pt-md pb-xs text-[10px] font-semibold uppercase tracking-wider text-text-muted"
          >
            {{ item.section }}
          </div>
          <router-link
            :to="item.path"
            class="flex items-center gap-sm px-sm py-2 rounded-lg text-sm transition-all"
            :class="isActive(item.path)
              ? 'bg-primary-500/12 text-primary-400 font-medium shadow-sm shadow-primary-500/5'
              : 'text-text-secondary hover:text-text-primary hover:bg-bg-surface-hover'"
            :title="props.collapsed ? item.label : undefined"
            @click="handleNavClick"
          >
            <AppIcon v-if="item.icon" :name="item.icon" :size="18" class="flex-shrink-0 opacity-90" />
            <span v-if="!props.collapsed" class="truncate-text">{{ item.label }}</span>
          </router-link>
        </template>
      </div>
    </div>
    <div class="px-sm py-sm border-t border-default space-y-0.5">
      <router-link
        v-if="!props.collapsed"
        to="/admin"
        class="flex items-center gap-sm px-sm py-2 rounded-lg text-sm text-text-muted hover:text-text-secondary hover:bg-bg-surface-hover transition-colors"
        @click="handleNavClick"
      >
        <AppIcon name="shield" :size="18" />
        <span>Admin Console</span>
      </router-link>
      <button
        type="button"
        class="flex items-center justify-center gap-sm px-sm py-2 rounded-lg text-sm text-text-muted hover:text-text-secondary hover:bg-bg-surface-hover transition-colors w-full"
        :title="props.collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="emit('toggle')"
      >
        <AppIcon :name="props.collapsed ? 'panel-left-open' : 'panel-left-close'" :size="18" />
        <span v-if="!props.collapsed">Collapse</span>
      </button>
    </div>
  </nav>
</template>
