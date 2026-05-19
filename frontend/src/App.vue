<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/ui/AppShell.vue'
import AppHeader from '@/components/ui/AppHeader.vue'
import AppBreadcrumb from '@/components/ui/AppBreadcrumb.vue'
import FeedbackButton from '@/components/feedback/FeedbackButton.vue'
import { useNavigation } from '@/composables/useNavigation'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const { upgradeSuggestions, fetchNavigation } = useNavigation()
const { isDark, toggleTheme } = useTheme()

const breadcrumbItems = computed(() => {
  const matched = route.matched.filter(r => r.name)
  return matched.map(r => ({
    label: String(r.name),
    path: r.path !== route.path ? r.path : undefined,
  }))
})

onMounted(async () => {
  await fetchNavigation()
})
</script>

<template>
  <AppShell>
    <template #header>
      <AppHeader logo-icon="🎬" logo-text="Media Platform">
        <template #workspace>
          <div class="flex items-center gap-sm">
            <span class="theme-badge bg-bg-surface text-text-secondary text-xs">
              Workspace: Default
            </span>
          </div>
        </template>

        <template #monitoring>
          <button class="theme-btn theme-btn-ghost theme-btn-sm" title="Monitoring">
            <span class="text-sm">📡</span>
          </button>
        </template>

        <template #notifications>
          <button class="theme-btn theme-btn-ghost theme-btn-sm relative" title="Notifications">
            <span class="text-sm">🔔</span>
            <span class="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-danger-500"></span>
          </button>
        </template>

        <template #feedback>
          <button class="theme-btn theme-btn-ghost theme-btn-sm" title="Help & Feedback">
            <span class="text-sm">❓</span>
          </button>
        </template>

        <template #userMenu>
          <button
            class="theme-btn theme-btn-ghost theme-btn-sm"
            :title="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
            @click="toggleTheme"
          >
            <span class="text-sm">{{ isDark ? '☀️' : '🌙' }}</span>
          </button>
          <div class="flex items-center gap-sm ml-sm">
            <div class="w-7 h-7 rounded-full bg-primary-600 flex items-center justify-center text-xs text-white font-medium">
              U
            </div>
          </div>
        </template>
      </AppHeader>
    </template>

    <template #default>
      <div class="h-full flex flex-col">
        <div v-if="upgradeSuggestions.length > 0" class="bg-warning-500/10 border-b border-warning-500/30 px-md py-sm flex items-center gap-md flex-shrink-0">
          <span class="text-xs text-warning-600 font-medium">Upgrade available:</span>
          <template v-for="suggestion in upgradeSuggestions" :key="suggestion.routeKey">
            <span class="text-xs text-text-secondary">
              {{ suggestion.title }} — {{ suggestion.requiredUpgrade }}
            </span>
          </template>
        </div>

        <div class="px-lg pt-sm pb-xs border-b border-default flex-shrink-0">
          <AppBreadcrumb :items="breadcrumbItems" />
        </div>

        <main class="flex-1 overflow-hidden relative">
          <RouterView />
          <FeedbackButton />
        </main>
      </div>
    </template>
  </AppShell>
</template>
