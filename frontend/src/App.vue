<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/ui/AppShell.vue'
import AppHeader from '@/components/ui/AppHeader.vue'
import AppBreadcrumb from '@/components/ui/AppBreadcrumb.vue'
import UserSidebar from '@/components/user/UserSidebar.vue'
import FeedbackButton from '@/components/feedback/FeedbackButton.vue'
import AvatarMenu from '@/components/navigation/AvatarMenu.vue'
import NotificationBell from '@/components/notifications/NotificationBell.vue'
import { useNavigation } from '@/composables/useNavigation'
import { useShellMode } from '@/composables/useShellMode'
import { useTheme } from '@/composables/useTheme'
import AppIcon from '@/components/ui/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const { upgradeSuggestions, fetchNavigation, isUsingFallback } = useNavigation()
const { mode, showUserSidebar, homePath } = useShellMode()
const { isDark, toggleTheme } = useTheme()

const userName = ref('User')
const isAdmin = ref(true)
const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const isMobile = ref(false)

const breadcrumbLabels: Record<string, string> = {
  'me-dashboard': 'Dashboard',
  'me-projects': 'Projects',
  'me-capabilities': 'Capabilities',
  'me-usage': 'Usage',
  'me-billing': 'Billing',
  'me-credits': 'Credits',
  'me-exports': 'Exports',
  'me-delivery-destinations': 'Delivery',
  'me-notifications': 'Notifications',
  'me-notification-settings': 'Notification Settings',
  'me-publish': 'Publish',
  'me-scheduler': 'Scheduler',
  'me-publish-history': 'Publish History',
  'me-analytics': 'Analytics',
  'me-reports': 'Reports',
  'me-feedback': 'Feedback',
  'me-settings': 'Settings',
  'editor': 'Video Editor',
  'prompts': 'Prompts',
  'effect-packs': 'Effect Packs',
  'workspace-members': 'Workspace Members',
  'workspace-roles': 'Roles',
  'workspace-pool': 'Entitlement Pool',
  'workspace-grants': 'Member Grants',
  'workspace-groups': 'Group Grants',
  'workspace-quota': 'Quota',
  'workspace-preview': 'Decision Preview',
  'workspace-debug': 'Access Debug',
}

const breadcrumbItems = computed(() => {
  if (mode.value === 'admin') return []
  const matched = route.matched.filter(r => r.name)
  return matched.map(r => ({
    label: breadcrumbLabels[String(r.name)] || String(r.name),
    path: r.path !== route.path ? r.path : undefined,
  }))
})

function checkMobile() {
  const wasMobile = isMobile.value
  isMobile.value = window.innerWidth <= 640
  if (isMobile.value) {
    sidebarCollapsed.value = true
    mobileSidebarOpen.value = false
  } else if (wasMobile && !isMobile.value) {
    mobileSidebarOpen.value = false
  }
}

function handleToggleSidebar() {
  if (!showUserSidebar.value) return
  if (isMobile.value) {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
  } else {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

function closeMobileSidebar() {
  if (isMobile.value) mobileSidebarOpen.value = false
}

function openMonitoring() {
  router.push('/admin/monitoring')
}

function openFeedback() {
  router.push('/me/feedback')
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  await fetchNavigation()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<template>
  <!-- Admin: nested AdminLayout owns the full shell -->
  <RouterView v-if="mode === 'admin'" />

  <AppShell
    v-else
    :sidebar-width="showUserSidebar ? (sidebarCollapsed ? '56px' : '240px') : undefined"
  >
    <template v-if="showUserSidebar" #sidebar>
      <div
        v-if="isMobile && mobileSidebarOpen"
        class="sidebar-backdrop"
        @click="mobileSidebarOpen = false"
        aria-hidden="true"
      />
      <UserSidebar
        :class="{ 'layout-sidebar-overlay': isMobile && mobileSidebarOpen }"
        :collapsed="sidebarCollapsed"
        @toggle="handleToggleSidebar"
        @navigate="closeMobileSidebar"
      />
    </template>

    <template #header>
      <AppHeader
        :show-hamburger="showUserSidebar"
        logo-text="Media Platform"
        :logo-to="homePath"
        @toggle-sidebar="handleToggleSidebar"
      >
        <template #workspace>
          <router-link
            to="/me"
            class="theme-badge bg-bg-surface text-text-secondary text-xs hover:text-primary-500 transition-colors no-underline"
          >
            Dashboard
          </router-link>
        </template>

        <template #monitoring>
          <button
            type="button"
            class="theme-btn theme-btn-ghost theme-btn-sm"
            title="Monitoring"
            @click="openMonitoring"
          >
            <AppIcon name="activity" :size="18" />
          </button>
        </template>

        <template #notifications>
          <NotificationBell />
        </template>

        <template #feedback>
          <button
            type="button"
            class="theme-btn theme-btn-ghost theme-btn-sm"
            title="Help & Feedback"
            @click="openFeedback"
          >
            <AppIcon name="message-circle" :size="18" />
          </button>
        </template>

        <template #userMenu>
          <button
            type="button"
            class="theme-btn theme-btn-ghost theme-btn-sm"
            :title="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
            @click="toggleTheme"
          >
            <AppIcon :name="isDark ? 'sun' : 'moon'" :size="18" />
          </button>
          <AvatarMenu
            :user-name="userName"
            :is-admin="isAdmin"
            class="ml-sm"
          />
        </template>
      </AppHeader>
    </template>

    <template #default>
      <div class="h-full flex flex-col min-h-0">
        <div v-if="isUsingFallback" class="bg-warning-500/10 border-b border-warning-500/30 px-md py-xs flex items-center gap-md flex-shrink-0">
          <span class="text-xs text-warning-600 font-medium">
            alert-triangle Using local navigation — backend navigation unavailable
          </span>
        </div>

        <div v-if="upgradeSuggestions.length > 0" class="bg-warning-500/10 border-b border-warning-500/30 px-md py-sm flex items-center gap-md flex-shrink-0">
          <span class="text-xs text-warning-600 font-medium">Upgrade available:</span>
          <template v-for="suggestion in upgradeSuggestions" :key="suggestion.routeKey">
            <span class="text-xs text-text-secondary">
              {{ suggestion.title }} — {{ suggestion.requiredUpgrade }}
            </span>
          </template>
        </div>

        <div
          v-if="breadcrumbItems.length > 0"
          class="px-lg pt-sm pb-xs border-b border-default flex items-center justify-between flex-shrink-0"
        >
          <AppBreadcrumb :items="breadcrumbItems" />
        </div>

        <main class="flex-1 min-h-0 overflow-auto">
          <RouterView />
          <FeedbackButton />
        </main>
      </div>
    </template>
  </AppShell>
</template>
