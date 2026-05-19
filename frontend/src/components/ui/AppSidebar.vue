<script setup lang="ts">
import { ref, watch, computed } from 'vue'

interface NavItem {
  key: string
  label: string
  icon?: string
  path?: string
  badge?: string | number
  badgeVariant?: 'default' | 'pro' | 'beta' | 'admin'
  disabled?: boolean
  disabledReason?: string
  requiredUpgrade?: string
}

interface NavGroup {
  label?: string
  collapsible?: boolean
  items: NavItem[]
}

const props = withDefaults(defineProps<{
  groups?: NavGroup[]
  collapsed?: boolean
  width?: string
  collapsedWidth?: string
  activeKey?: string
  showSearch?: boolean
}>(), {
  groups: () => [],
  collapsed: false,
  width: '208px',
  collapsedWidth: '56px',
  showSearch: true,
})

const emit = defineEmits<{
  toggle: []
  navigate: [item: NavItem]
}>()

const isCollapsed = ref(props.collapsed)
const searchQuery = ref('')
const collapsedGroups = ref<Set<number>>(new Set())

watch(() => props.collapsed, (val) => {
  isCollapsed.value = val
})

const totalItems = computed(() =>
  props.groups.reduce((sum, g) => sum + g.items.length, 0)
)

const showSearchInput = computed(() =>
  props.showSearch && !isCollapsed.value && totalItems.value > 15
)

const filteredGroups = computed(() => {
  if (!searchQuery.value.trim()) return props.groups
  const q = searchQuery.value.toLowerCase()
  return props.groups
    .map(g => ({
      ...g,
      items: g.items.filter(item =>
        item.label.toLowerCase().includes(q) ||
        item.key.toLowerCase().includes(q)
      ),
    }))
    .filter(g => g.items.length > 0)
})

function handleToggle() {
  isCollapsed.value = !isCollapsed.value
  emit('toggle')
}

function handleNav(item: NavItem) {
  if (item.disabled) return
  emit('navigate', item)
}

function toggleGroup(index: number) {
  const s = new Set(collapsedGroups.value)
  if (s.has(index)) {
    s.delete(index)
  } else {
    s.add(index)
  }
  collapsedGroups.value = s
}

function badgeClass(variant: string): string {
  switch (variant) {
    case 'pro': return 'bg-primary-500/20 text-primary-400'
    case 'beta': return 'bg-warning-500/20 text-warning-500'
    case 'admin': return 'bg-danger-500/20 text-danger-500'
    default: return 'bg-bg-surface text-text-muted'
  }
}

function handleKeyNav(e: KeyboardEvent, item: NavItem) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    handleNav(item)
  }
}

const sidebarStyle = () => ({
  width: isCollapsed.value ? props.collapsedWidth : props.width,
})
</script>

<template>
  <aside
    class="layout-sidebar"
    :class="{ 'layout-sidebar-collapsed': isCollapsed }"
    :style="sidebarStyle()"
    role="navigation"
    aria-label="Main navigation"
  >
    <div class="flex items-center justify-between px-md py-sm border-b border-default flex-shrink-0" style="height: 3rem;">
      <span
        v-if="!isCollapsed"
        class="text-sm font-semibold text-text-primary truncate-text"
      >
        <slot name="title">Navigation</slot>
      </span>
      <button
        class="theme-btn theme-btn-ghost theme-btn-sm"
        :aria-label="isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="handleToggle"
      >
        <svg v-if="isCollapsed" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
        </svg>
        <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
        </svg>
      </button>
    </div>

    <div v-if="showSearchInput" class="px-sm py-sm flex-shrink-0">
      <div class="relative">
        <svg class="absolute left-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          v-model="searchQuery"
          type="search"
          placeholder="Search routes..."
          class="w-full theme-input pl-7 text-xs"
          aria-label="Search navigation routes"
        />
      </div>
    </div>

    <nav class="flex-1 overflow-y-auto theme-scrollbar py-sm" aria-label="Navigation menu">
      <template v-for="(group, gi) in filteredGroups" :key="gi">
        <div
          v-if="group.label && !isCollapsed"
          class="flex items-center justify-between px-md pt-sm pb-xs cursor-pointer select-none group-header"
          role="button"
          tabindex="0"
          :aria-expanded="!collapsedGroups.has(gi)"
          @click="group.collapsible !== false ? toggleGroup(gi) : null"
          @keydown.enter="group.collapsible !== false ? toggleGroup(gi) : null"
        >
          <span class="text-[10px] font-semibold uppercase tracking-wider text-text-muted">
            {{ group.label }}
          </span>
          <svg
            v-if="group.collapsible !== false"
            class="w-3 h-3 text-text-muted transition-transform duration-fast"
            :class="{ '-rotate-90': collapsedGroups.has(gi) }"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
        <div
          v-else-if="gi > 0"
          class="mx-sm my-xs border-t border-default"
        />

        <template v-if="!collapsedGroups.has(gi)">
          <slot
            v-for="item in group.items"
            :key="item.key"
            name="nav-item"
            :item="item"
            :collapsed="isCollapsed"
            :active="activeKey === item.key"
          >
            <div
              v-if="item.disabled"
              class="w-full flex items-center gap-sm px-md py-sm text-sm cursor-not-allowed opacity-60"
              :title="item.disabledReason || item.requiredUpgrade || 'Not available'"
              role="link"
              aria-disabled="true"
              :aria-label="`${item.label} - ${item.disabledReason || 'Disabled'}`"
            >
              <span v-if="item.icon" class="text-base flex-shrink-0 grayscale" aria-hidden="true">{{ item.icon }}</span>
              <span v-if="!isCollapsed" class="truncate-text line-through">{{ item.label }}</span>
              <span
                v-if="item.requiredUpgrade && !isCollapsed"
                class="ml-auto theme-badge text-[9px] flex-shrink-0"
                :class="badgeClass('pro')"
              >
                {{ item.requiredUpgrade }}
              </span>
              <svg v-if="!isCollapsed" class="w-3 h-3 text-text-muted flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <button
              v-else
              class="w-full flex items-center gap-sm px-md py-sm text-sm transition-colors duration-fast text-left"
              :class="activeKey === item.key
                ? 'bg-primary-500/10 text-primary-400 border-r-2 border-primary-400'
                : 'text-text-secondary hover:bg-bg-surface-hover hover:text-text-primary'"
              :aria-current="activeKey === item.key ? 'page' : undefined"
              :aria-label="item.label"
              @click="handleNav(item)"
              @keydown="handleKeyNav($event, item)"
            >
              <span v-if="item.icon" class="text-base flex-shrink-0" aria-hidden="true">{{ item.icon }}</span>
              <span v-if="!isCollapsed" class="truncate-text flex-1">{{ item.label }}</span>
              <span
                v-if="item.badge && !isCollapsed"
                class="ml-auto theme-badge flex-shrink-0"
                :class="badgeClass(item.badgeVariant || 'default')"
              >
                {{ item.badge }}
              </span>
              <span
                v-if="activeKey === item.key"
                class="absolute left-0 w-0.5 h-5 bg-primary-400 rounded-r"
                aria-hidden="true"
              />
            </button>
          </slot>
        </template>
      </template>

      <div v-if="filteredGroups.length === 0 && searchQuery" class="px-md py-sm text-xs text-text-muted text-center">
        No routes found
      </div>
    </nav>

    <slot name="footer" :collapsed="isCollapsed" />
  </aside>
</template>

<style scoped>
.group-header:hover .text-text-muted {
  color: var(--color-text-secondary);
}
</style>
