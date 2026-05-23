<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { NotificationInboxItem } from '@/api/me'
import { useI18nError } from '@/utils/i18n'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const { t } = useI18nError()

const loading = ref(true)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const marking = ref(false)

const items = ref<NotificationInboxItem[]>([])
const total = ref(0)
const unreadCount = ref(0)
const page = ref(0)
const size = ref(20)
const filterUnread = ref(false)

const hasMore = computed(() => items.value.length < total.value)

onMounted(loadNotifications)

async function loadNotifications() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    const result = await MeEntitlementAPI.getNotificationInbox(page.value, size.value)
    items.value = result.items
    total.value = result.total
    unreadCount.value = result.unreadCount
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('COMMON-500-001', 'Failed to load notifications')
    if (msg.includes('401')) errorCode.value = 'COMMON-401-001'
    else if (msg.includes('403')) errorCode.value = 'COMMON-403-001'
    else errorCode.value = 'COMMON-500-001'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  try {
    const result = await MeEntitlementAPI.getNotificationInbox(page.value, size.value)
    items.value = [...items.value, ...result.items]
  } catch {
    page.value--
  }
}

const filteredNotifications = computed(() => {
  if (filterUnread.value) return items.value.filter(n => !n.read)
  return items.value
})

function typeVariant(type: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (type) {
    case 'SUCCESS': return 'success'
    case 'WARNING': return 'warning'
    case 'ERROR': return 'danger'
    case 'INFO': return 'info'
    default: return 'neutral'
  }
}

function typeIcon(type: string): string {
  switch (type) {
    case 'SUCCESS': return '✅'
    case 'WARNING': return '⚠️'
    case 'ERROR': return '❌'
    case 'INFO': return 'ℹ️'
    default: return '📌'
  }
}

async function markAsRead(id: string) {
  marking.value = true
  try {
    await MeEntitlementAPI.markInboxNotificationRead(id)
    const item = items.value.find(n => n.id === id)
    if (item) {
      item.read = true
      item.readAt = new Date().toISOString()
    }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to mark as read')
  } finally {
    marking.value = false
  }
}

async function markAllRead() {
  marking.value = true
  try {
    await MeEntitlementAPI.markAllInboxNotificationsRead()
    items.value.forEach(n => {
      n.read = true
      if (!n.readAt) n.readAt = new Date().toISOString()
    })
    unreadCount.value = 0
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to mark all as read')
  } finally {
    marking.value = false
  }
}

function formatTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return 'Just now'
    if (diffMin < 60) return `${diffMin}m ago`
    const diffHr = Math.floor(diffMin / 60)
    if (diffHr < 24) return `${diffHr}h ago`
    const diffDay = Math.floor(diffHr / 24)
    if (diffDay < 7) return `${diffDay}d ago`
    return d.toLocaleDateString()
  } catch {
    return dateStr
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Notifications" :subtitle="`${unreadCount} unread notifications`">
      <template #actions>
        <button v-if="unreadCount > 0" class="theme-btn theme-btn-secondary theme-btn-sm" :disabled="marking" @click="markAllRead">
          Mark all read
        </button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadNotifications">Refresh</button>
      </template>
    </PageHeader>

    <!-- Error banner -->
    <div v-if="error" class="p-sm bg-danger-500/10 border border-danger-500/30 rounded text-xs text-danger-500 flex items-center gap-sm">
      <span>⚠️</span>
      <span>{{ error }}</span>
      <code v-if="errorCode" class="ml-auto text-[10px] font-mono bg-danger-500/10 px-xs py-0.5 rounded">{{ errorCode }}</code>
    </div>

    <LoadingState v-if="loading" message="Loading notifications..." />
    <ErrorState v-else-if="error && items.length === 0" :description="error" @retry="loadNotifications" />

    <template v-else>
      <!-- Filter tabs -->
      <div class="flex gap-xs border-b border-default">
        <button
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="!filterUnread ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="filterUnread = false">
          All ({{ total }})
        </button>
        <button
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="filterUnread ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="filterUnread = true">
          Unread ({{ unreadCount }})
        </button>
      </div>

      <EmptyState v-if="filteredNotifications.length === 0" icon="🔔" title="No notifications" description="You're all caught up! Notifications about your account will appear here." />

      <PageSection v-else title="Recent">
        <div class="space-y-sm">
          <div v-for="n in filteredNotifications" :key="n.id"
            class="c-card transition-colors cursor-pointer"
            :class="!n.read ? 'border-primary-200 bg-primary-500/5' : ''"
            @click="n.link ? $router.push(n.link) : null">
            <div class="c-card-body">
              <div class="flex items-start gap-md">
                <span class="text-lg flex-shrink-0" aria-hidden="true">{{ typeIcon(n.type) }}</span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-sm mb-xs flex-wrap">
                    <h3 class="text-sm font-medium text-text-primary" :class="!n.read ? 'font-semibold' : ''">{{ n.title }}</h3>
                    <StatusBadge :variant="typeVariant(n.type)" :label="n.type" size="sm" />
                    <span v-if="!n.read" class="w-2 h-2 rounded-full bg-primary-500 flex-shrink-0" />
                  </div>
                  <p class="text-xs text-text-secondary">{{ n.message }}</p>
                  <div class="flex items-center gap-sm mt-sm flex-wrap">
                    <span class="text-xs text-text-muted">{{ formatTime(n.createdAt) }}</span>
                    <span v-if="n.resourceType" class="text-[10px] px-1.5 py-0.5 rounded bg-bg-surface text-text-muted font-mono">
                      {{ n.resourceType }}<span v-if="n.resourceId">:{{ n.resourceId }}</span>
                    </span>
                    <button v-if="!n.read" class="text-xs text-primary-500 hover:text-primary-400" @click.stop="markAsRead(n.id)">
                      Mark as read
                    </button>
                    <router-link v-if="n.link" :to="n.link" class="text-xs text-primary-500 hover:text-primary-400" @click.stop>
                      View details →
                    </router-link>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Load more -->
          <div v-if="hasMore" class="flex justify-center pt-md">
            <button class="theme-btn theme-btn-secondary" @click="loadMore">Load more</button>
          </div>
        </div>
      </PageSection>
    </template>
  </div>
</template>
