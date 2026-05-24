<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { NotificationAPI } from '@/api/admin/notification'
import type { NotificationDeliveryLog } from '@/api/admin/notification'
import { useI18nError } from '@/utils/i18n'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const { t } = useI18nError()

const loading = ref(true)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)

const items = ref<NotificationDeliveryLog[]>([])
const total = ref(0)
const currentPage = ref(0)
const pageSize = ref(20)

const filter = reactive({
  status: '',
  channel: '',
  eventKey: '',
  tenantId: '',
})

onMounted(loadLogs)

async function loadLogs() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    const result = await NotificationAPI.getDeliveryLogs({
      page: currentPage.value,
      size: pageSize.value,
      status: filter.status || undefined,
      channel: filter.channel || undefined,
      eventKey: filter.eventKey || undefined,
      tenantId: filter.tenantId || undefined,
    })
    items.value = result.items
    total.value = result.total
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('COMMON-500-001', 'Failed to load delivery logs')
    errorCode.value = 'COMMON-500-001'
  } finally {
    loading.value = false
  }
}

async function handleRetry(logId: string) {
  try {
    await NotificationAPI.retryDelivery(logId)
    await loadLogs()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to retry delivery')
    errorCode.value = 'NOTIFICATION-500-001'
  }
}

function handleFilter() {
  currentPage.value = 0
  loadLogs()
}

function handleReset() {
  filter.status = ''
  filter.channel = ''
  filter.eventKey = ''
  filter.tenantId = ''
  currentPage.value = 0
  loadLogs()
}

function handlePageChange(delta: number) {
  currentPage.value += delta
  loadLogs()
}

function deliveryStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  switch (status) {
    case 'DELIVERED': return 'success'
    case 'SENT': return 'info'
    case 'PENDING': return 'warning'
    case 'FAILED': return 'danger'
    case 'BOUNCED': return 'neutral'
    default: return 'neutral'
  }
}

function formatTime(dateStr?: string): string {
  if (!dateStr) return '—'
  try {
    const d = new Date(dateStr)
    return d.toLocaleString()
  } catch {
    return dateStr
  }
}

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
const hasPrev = computed(() => currentPage.value > 0)
const hasNext = computed(() => currentPage.value < totalPages.value - 1)
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Delivery Logs" :subtitle="`${total} total deliveries`">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadLogs">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading delivery logs..." />
    <ErrorState v-else-if="error && items.length === 0" :description="error" @retry="loadLogs" />

    <template v-else>
      <!-- Error banner -->
      <div v-if="errorCode && error" class="p-sm bg-danger-500/10 border border-danger-500/30 rounded text-xs text-danger-500 flex items-center gap-sm">
        <span>alert-triangle</span>
        <span>{{ error }}</span>
        <code class="ml-auto text-[10px] font-mono bg-danger-500/10 px-xs py-0.5 rounded">{{ errorCode }}</code>
      </div>

      <!-- Filters -->
      <div class="c-card">
        <div class="c-card-body">
          <div class="grid grid-cols-5 gap-md items-end">
            <div>
              <label class="c-form-label">Status</label>
              <select v-model="filter.status" class="theme-input w-full">
                <option value="">All</option>
                <option value="PENDING">Pending</option>
                <option value="SENT">Sent</option>
                <option value="DELIVERED">Delivered</option>
                <option value="FAILED">Failed</option>
                <option value="BOUNCED">Bounced</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Channel</label>
              <select v-model="filter.channel" class="theme-input w-full">
                <option value="">All</option>
                <option value="IN_APP">In-App</option>
                <option value="EMAIL">Email</option>
                <option value="SMS">SMS</option>
                <option value="WEBHOOK">Webhook</option>
                <option value="CHAT">Chat</option>
                <option value="PUSH">Push</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Event Key</label>
              <input v-model="filter.eventKey" type="text" class="theme-input w-full" placeholder="e.g. BILLING_PAYMENT_FAILED" />
            </div>
            <div>
              <label class="c-form-label">Tenant ID</label>
              <input v-model="filter.tenantId" type="text" class="theme-input w-full" placeholder="Filter by tenant" />
            </div>
            <div class="flex gap-sm">
              <button class="theme-btn theme-btn-primary" @click="handleFilter">Filter</button>
              <button class="theme-btn theme-btn-secondary" @click="handleReset">Reset</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Table -->
      <div class="c-card">
        <div class="c-card-body">
          <EmptyState v-if="items.length === 0" icon="mail" title="No delivery logs" description="No delivery records match the current filters." />
          <table v-else class="w-full text-sm">
            <thead>
              <tr class="border-b border-default text-xs text-text-muted">
                <th class="text-left px-sm py-sm">ID</th>
                <th class="text-left px-sm py-sm">Notification ID</th>
                <th class="text-left px-sm py-sm">Event</th>
                <th class="text-left px-sm py-sm">Channel</th>
                <th class="text-left px-sm py-sm">Status</th>
                <th class="text-left px-sm py-sm">Destination</th>
                <th class="text-left px-sm py-sm">Retries</th>
                <th class="text-left px-sm py-sm">Created</th>
                <th class="text-left px-sm py-sm">Sent</th>
                <th class="text-left px-sm py-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in items" :key="d.id" class="border-b border-default/50 hover:bg-bg-surface-hover"
                :class="d.status === 'FAILED' ? 'bg-danger-500/5' : ''">
                <td class="px-sm py-sm text-xs font-mono text-text-muted">{{ d.id.slice(0, 8) }}</td>
                <td class="px-sm py-sm text-xs font-mono text-text-muted">{{ d.notificationId.slice(0, 8) }}</td>
                <td class="px-sm py-sm text-xs font-mono text-primary-400">{{ d.eventKey }}</td>
                <td class="px-sm py-sm text-text-primary">{{ d.channel }}</td>
                <td class="px-sm py-sm"><StatusBadge :variant="deliveryStatusVariant(d.status)" :label="d.status" size="sm" /></td>
                <td class="px-sm py-sm text-xs text-text-muted">{{ d.destinationMasked }}</td>
                <td class="px-sm py-sm text-text-muted">{{ d.retryCount }}</td>
                <td class="px-sm py-sm text-xs text-text-muted">{{ formatTime(d.createdAt) }}</td>
                <td class="px-sm py-sm text-xs text-text-muted">{{ formatTime(d.sentAt) }}</td>
                <td class="px-sm py-sm">
                  <button v-if="d.status === 'FAILED'" class="theme-btn theme-btn-secondary theme-btn-sm" @click="handleRetry(d.id)">
                    Retry
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex items-center justify-between">
        <div class="text-xs text-text-muted">
          Showing {{ currentPage * pageSize + 1 }}–{{ Math.min((currentPage + 1) * pageSize, total) }} of {{ total }}
        </div>
        <div class="flex gap-sm">
          <button class="theme-btn theme-btn-secondary theme-btn-sm" :disabled="!hasPrev" @click="handlePageChange(-1)">← Prev</button>
          <span class="text-sm text-text-muted self-center">{{ currentPage + 1 }} / {{ totalPages }}</span>
          <button class="theme-btn theme-btn-secondary theme-btn-sm" :disabled="!hasNext" @click="handlePageChange(1)">Next →</button>
        </div>
      </div>
    </template>
  </div>
</template>
