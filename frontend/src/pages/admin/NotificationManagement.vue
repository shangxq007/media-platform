<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NotificationAPI } from '@/api/admin/notification'
import type { AdminNotification, NotificationDelivery } from '@/api/admin/notification'
import { useAdminTenantSelection } from '@/composables/useAdminTenantSelection'

const loading = ref(true)
const { tenants: _tenants, selectedTenantId, loading: _tenantsLoading } = useAdminTenantSelection()
const notifications = ref<AdminNotification[]>([])
const selectedId = ref<string | null>(null)
const deliveries = ref<NotificationDelivery[]>([])
const showPublish = ref(false)
const publishType = ref('')
const publishPayload = ref('{}')

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    notifications.value = await NotificationAPI.listNotifications(selectedTenantId.value)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function viewDeliveries(notificationId: string) {
  selectedId.value = notificationId
  try {
    deliveries.value = await NotificationAPI.getDeliveries(selectedTenantId.value, notificationId)
  } catch { /* backend may not be running */ }
}

async function retryNotification(notificationId: string) {
  await NotificationAPI.retryNotification(selectedTenantId.value, notificationId)
  await loadData()
}

async function publishEvent() {
  try {
    const payload = JSON.parse(publishPayload.value)
    await NotificationAPI.publishEvent({ type: publishType.value, tenantId: selectedTenantId.value, payload })
    showPublish.value = false
    publishType.value = ''
    publishPayload.value = '{}'
    await loadData()
  } catch { /* backend may not be running */ }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Notification Management</h1>
      <div class="flex items-center gap-3">
        <input
          v-model="selectedTenantId"
          type="text"
          class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Tenant ID"
        />
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="loadData">Refresh</button>
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="showPublish = !showPublish">Publish Event</button>
      </div>
    </div>

    <!-- Publish Form -->
    <div v-if="showPublish" class="bg-surface-2 border border-border-subtle rounded-lg p-4 mb-6">
      <h3 class="text-xs font-semibold text-text-primary mb-3">Publish Notification Event</h3>
      <div class="flex gap-3 max-w-lg">
        <input v-model="publishType" type="text" class="flex-1 bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" placeholder="Event type" />
        <input v-model="publishPayload" type="text" class="flex-1 bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white font-mono" placeholder='{"key":"value"}' />
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded" @click="publishEvent">Send</button>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="notifications.length === 0" class="text-text-tertiary text-sm">No notifications</div>

    <div v-else class="space-y-3">
      <div
        v-for="n in notifications"
        :key="n.id"
        class="bg-surface-2 border border-border-subtle rounded-lg p-3"
      >
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium">{{ n.title || n.type || 'Notification' }}</span>
            <span class="text-xs text-text-tertiary ml-2 font-mono">{{ n.id }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs px-1.5 py-0.5 rounded bg-surface-3 text-text-primary">{{ n.status }}</span>
            <button class="text-xs px-2 py-0.5 bg-surface-3 hover:bg-surface-4 rounded" @click="viewDeliveries(n.id!)">Deliveries</button>
            <button class="text-xs px-2 py-0.5 bg-info-muted text-info rounded" @click="retryNotification(n.id!)">Retry</button>
          </div>
        </div>
        <div class="text-xs text-text-tertiary mt-1">{{ n.createdAt }}</div>

        <!-- Deliveries -->
        <div v-if="selectedId === n.id && deliveries.length > 0" class="mt-3 pt-3 border-t border-border-subtle space-y-1">
          <div v-for="d in deliveries" :key="d.id" class="flex items-center gap-3 text-xs">
            <span class="px-1.5 py-0.5 rounded bg-surface-3">{{ d.channel }}</span>
            <span :class="d.status === 'DELIVERED' ? 'text-success' : 'text-danger'">{{ d.status }}</span>
            <span class="text-text-tertiary">{{ d.sentAt || '—' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
