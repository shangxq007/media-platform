<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/ui/AppShell.vue'
import AppSidebar from '@/components/ui/AppSidebar.vue'
import AppBreadcrumb from '@/components/ui/AppBreadcrumb.vue'
import PageHeader from '@/components/ui/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const isMobile = ref(false)
const workspaceName = ref('Default Workspace')

function checkMobile() {
  isMobile.value = window.innerWidth <= 640
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

type AdminNavItem = { key: string; label: string; path: string; icon?: string; badge?: string; badgeVariant?: 'default' | 'pro' | 'beta' | 'admin' }
type AdminNavGroup = { label: string; collapsible?: boolean; items: AdminNavItem[] }

const navGroups = computed<AdminNavGroup[]>(() => [
  {
    label: 'Platform',
    collapsible: true,
    items: [
      { key: 'dashboard', label: 'Dashboard', path: '/admin', icon: '📊' },
      { key: 'tenants', label: 'Tenants', path: '/admin/tenants', icon: '🏢' },
      { key: 'render-jobs', label: 'Render Jobs', path: '/admin/render-jobs', icon: '🎬' },
      { key: 'extensions', label: 'Extensions', path: '/admin/extensions', icon: '🔌' },
      { key: 'analytics', label: 'Analytics', path: '/admin/analytics', icon: '📈' },
       { key: 'notifications', label: 'Notifications', path: '/admin/notifications', icon: '🔔' },
       { key: 'audit', label: 'Audit & Outbox', path: '/admin/audit', icon: '📋' },
       { key: 'audit-log', label: 'Audit Log', path: '/admin/audit-log', icon: '📝' },
       { key: 'feedback-admin', label: 'Feedback Admin', path: '/admin/feedback', icon: '💬' },
       { key: 'config', label: 'Config', path: '/admin/config', icon: '⚙️' },
      { key: 'feature-flags', label: 'Feature Flags', path: '/admin/feature-flags', icon: '🚩' },
       { key: 'feature-flag-mgmt', label: 'Flag Management', path: '/admin/feature-flags/manage', icon: '🎯' },
       { key: 'policy-mgmt', label: 'Policy / ABAC', path: '/admin/policies', icon: '📜' },
       { key: 'routes', label: 'Route Management', path: '/admin/routes', icon: '🔀' },
    ]
  },
  {
    label: 'Entitlements',
    collapsible: true,
    items: [
      { key: 'entitlement-bundles', label: 'Bundles', path: '/admin/entitlements/bundles', icon: '📦' },
      { key: 'entitlement-overrides', label: 'Tenant Overrides', path: '/admin/entitlements/overrides', icon: '🔧', badge: 'Admin', badgeVariant: 'admin' as const },
      { key: 'entitlement-grants', label: 'User Grants', path: '/admin/entitlements/grants', icon: '🎫', badge: 'Admin', badgeVariant: 'admin' as const },
      { key: 'entitlement-quota', label: 'Quota Policies', path: '/admin/entitlements/quota', icon: '📊', badge: 'Pro', badgeVariant: 'pro' as const },
    ]
  },
  {
    label: 'Billing',
    collapsible: true,
    items: [
      { key: 'billing-plans', label: 'Plans', path: '/admin/billing/plans', icon: '📋' },
      { key: 'billing-pricing', label: 'Pricing Rules', path: '/admin/billing/pricing', icon: '💲', badge: 'Beta', badgeVariant: 'beta' as const },
      { key: 'billing-usage', label: 'Usage Ledger', path: '/admin/billing/usage', icon: '📈' },
      { key: 'billing-credits', label: 'Credit Wallets', path: '/admin/billing/credits', icon: '💰' },
      { key: 'billing-quotes', label: 'Quotes', path: '/admin/billing/quotes', icon: '🧾', badge: 'Beta', badgeVariant: 'beta' as const },
      { key: 'billing-invoices', label: 'Invoices', path: '/admin/billing/invoices', icon: '📄' },
    ]
  }
])

type FlatNavItem = { key: string; label: string; path: string; icon?: string; badge?: string; badgeVariant?: 'default' | 'pro' | 'beta' | 'admin' }

const flatNavItems = computed<FlatNavItem[]>(() => navGroups.value.flatMap(g => g.items))

const activeKey = computed(() => {
  const current = route.path
  const best = flatNavItems.value
    .filter(item => item.path !== '/admin' && current.startsWith(item.path))
    .sort((a, b) => b.path.length - a.path.length)[0]
  if (best) return best.key
  if (current === '/admin') return 'dashboard'
  return ''
})

function navigateTo(item: { path?: string }) {
  if (item.path) {
    router.push(item.path)
    if (isMobile.value) {
      mobileSidebarOpen.value = false
    }
  }
}

function toggleMobileSidebar() {
  mobileSidebarOpen.value = !mobileSidebarOpen.value
}

const breadcrumbItems = computed(() => {
  const matched = route.matched.filter(r => r.name)
  return matched.map(r => ({
    label: String(r.name),
    path: r.path !== route.path ? r.path : undefined,
  }))
})

const pageTitle = computed(() => {
  const name = route.name
  if (!name) return 'Admin'
  return String(name)
})

const pageSubtitle = computed(() => {
  const item = flatNavItems.value.find(i => i.key === activeKey.value)
  return item?.label
})
</script>

<template>
  <AppShell :sidebar-width="sidebarCollapsed ? '56px' : '224px'">
    <template #sidebar>
      <div
        v-if="isMobile && mobileSidebarOpen"
        class="sidebar-backdrop"
        @click="mobileSidebarOpen = false"
        aria-hidden="true"
      />
      <AppSidebar
        v-if="!isMobile || mobileSidebarOpen"
        :groups="navGroups"
        :collapsed="sidebarCollapsed"
        :active-key="activeKey"
        :width="isMobile ? '280px' : '224px'"
        collapsed-width="56px"
        :show-search="true"
        :class="{ 'layout-sidebar-overlay': isMobile && mobileSidebarOpen }"
        @toggle="sidebarCollapsed = !sidebarCollapsed"
        @navigate="navigateTo"
      >
        <template #title>
          <div class="flex items-center gap-xs">
            <span class="text-base">🛡️</span>
            <span>Admin Panel</span>
          </div>
        </template>

        <template #footer="{ collapsed: isCollapsed }">
          <div class="px-md py-sm border-t border-default">
            <button
              class="flex items-center gap-sm text-sm text-text-secondary hover:text-text-primary transition-colors w-full"
              @click="router.push('/')"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
              </svg>
              <span v-if="!isCollapsed">Back to Editor</span>
            </button>
          </div>
        </template>
      </AppSidebar>
    </template>

    <template #default>
      <div class="h-full flex flex-col">
        <div class="px-lg pt-sm pb-xs border-b border-default flex-shrink-0 flex items-center justify-between">
          <button
            v-if="isMobile"
            class="theme-btn theme-btn-ghost theme-btn-sm mr-sm mobile-only"
            aria-label="Open navigation menu"
            @click="toggleMobileSidebar"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <AppBreadcrumb :items="breadcrumbItems" />
          <span class="theme-badge bg-bg-surface text-text-muted text-xs">
            {{ workspaceName }}
          </span>
        </div>

        <div class="px-lg pt-md pb-sm flex-shrink-0">
          <PageHeader :title="pageTitle" :subtitle="pageSubtitle" />
        </div>

        <div class="flex-1 overflow-y-auto overflow-x-hidden px-lg pb-lg theme-scrollbar">
          <router-view />
        </div>
      </div>
    </template>
  </AppShell>
</template>
