<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MeEntitlementAPI, type NotificationItem } from '@/api/me'
import NotificationListItem from './NotificationListItem.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const notifications = ref<NotificationItem[]>([])

onMounted(loadNotifications)

async function loadNotifications() {
  loading.value = true
  error.value = null
  try {
    const result = await MeEntitlementAPI.getMyNotifications(0, 10)
    notifications.value = result.notifications
  } catch (e: unknown) {
    notifications.value = []
  } finally {
    loading.value = false
  }
}

async function markAsRead(id: string) {
  try {
    await MeEntitlementAPI.markNotificationRead(id)
  } catch { /* noop */ }
  const n = notifications.value.find(n => n.id === id)
  if (n) n.read = true
}

const unreadCount = () => notifications.value.filter(n => !n.read).length

function goToNotifications() {
  router.push('/me/notifications')
}

function goToSettings() {
  router.push('/me/notification-settings')
}
</script>

<template>
  <div class="w-80 flex flex-col max-h-96">
    <div class="flex items-center justify-between px-md py-sm border-b border-default flex-shrink-0">
      <h3 class="text-sm font-semibold text-text-primary">Notifications</h3>
      <div class="flex items-center gap-xs">
        <span v-if="unreadCount() > 0" class="theme-badge bg-primary-500/20 text-primary-400 text-[10px]">
          {{ unreadCount() }} new
        </span>
        <button
          class="text-xs text-primary-500 hover:text-primary-400 transition-colors"
          @click="loadNotifications"
        >
          Refresh
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex-1 flex items-center justify-center py-lg">
      <div class="c-spinner c-spinner-sm text-text-muted" />
    </div>

    <div v-else-if="notifications.length === 0" class="flex-1 py-lg px-md">
      <EmptyState
        icon="🔔"
        title="No notifications"
        description="You're all caught up! We'll notify you when something needs your attention."
      />
    </div>

    <div v-else class="flex-1 overflow-y-auto theme-scrollbar">
      <NotificationListItem
        v-for="n in notifications"
        :key="n.id"
        :id="n.id"
        :type="n.type"
        :title="n.title"
        :message="n.message"
        :read="n.read"
        :created-at="n.createdAt"
        :link="n.link"
        @mark-read="markAsRead"
      />
    </div>

    <div class="flex items-center justify-between px-md py-sm border-t border-default flex-shrink-0">
      <button
        class="text-xs text-primary-500 hover:text-primary-400 transition-colors"
        @click="goToNotifications"
      >
        View all notifications
      </button>
      <button
        class="text-xs text-text-muted hover:text-text-secondary transition-colors"
        @click="goToSettings"
      >
        Settings
      </button>
    </div>
  </div>
</template>
