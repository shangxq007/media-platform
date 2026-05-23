<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import AppShell from '@/components/ui/AppShell.vue'
import UserSidebar from '@/components/user/UserSidebar.vue'
import AppBreadcrumb from '@/components/ui/AppBreadcrumb.vue'

const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth <= 640
  if (isMobile.value) sidebarCollapsed.value = true
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

function toggleMobileSidebar() {
  mobileSidebarOpen.value = !mobileSidebarOpen.value
}
</script>

<template>
  <AppShell :sidebar-width="sidebarCollapsed ? '56px' : '220px'">
    <template #sidebar>
      <div
        v-if="isMobile && mobileSidebarOpen"
        class="sidebar-backdrop"
        @click="mobileSidebarOpen = false"
        aria-hidden="true"
      />
      <UserSidebar
        :class="{ 'layout-sidebar-overlay': isMobile && mobileSidebarOpen }"
        :collapsed="sidebarCollapsed"
        @toggle="sidebarCollapsed = !sidebarCollapsed"
        @navigate="if (isMobile) mobileSidebarOpen = false"
      />
    </template>
    <template #default>
      <div class="h-full flex flex-col">
        <div class="px-lg pt-sm pb-xs border-b border-default flex items-center justify-between flex-shrink-0">
          <button
            v-if="isMobile"
            class="theme-btn theme-btn-ghost theme-btn-sm mr-sm"
            aria-label="Open navigation menu"
            @click="toggleMobileSidebar"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <AppBreadcrumb :items="[]" />
        </div>
        <div class="flex-1 overflow-y-auto overflow-x-hidden theme-scrollbar">
          <slot />
        </div>
      </div>
    </template>
  </AppShell>
</template>
