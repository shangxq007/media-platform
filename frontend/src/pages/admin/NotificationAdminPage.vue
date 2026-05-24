<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NotificationAPI } from '@/api/admin/notification'
import type { NotificationEventDefinition, NotificationProviderStatus, NotificationDeliveryLog } from '@/api/admin/notification'
import { useI18nError } from '@/utils/i18n'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const { t } = useI18nError()
const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)

const eventDefs = ref<NotificationEventDefinition[]>([])
const providerStatuses = ref<NotificationProviderStatus[]>([])
const recentDeliveries = ref<NotificationDeliveryLog[]>([])
const deliveriesTotal = ref(0)

const activeTab = ref<'overview' | 'events' | 'deliveries' | 'providers'>('overview')

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    const [defs, providers, deliveries] = await Promise.allSettled([
      NotificationAPI.getEventDefinitions(),
      NotificationAPI.getProviderStatus(),
      NotificationAPI.getDeliveryLogs({ page: 0, size: 5 }),
    ])
    if (defs.status === 'fulfilled') eventDefs.value = defs.value
    if (providers.status === 'fulfilled') providerStatuses.value = providers.value
    if (deliveries.status === 'fulfilled') {
      recentDeliveries.value = deliveries.value.items
      deliveriesTotal.value = deliveries.value.total
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('COMMON-500-001', 'Failed to load notification data')
    errorCode.value = 'COMMON-500-001'
  } finally {
    loading.value = false
  }
}

const activeEvents = computed(() => eventDefs.value.filter(e => !e.archived))
const archivedEvents = computed(() => eventDefs.value.filter(e => e.archived))
const criticalEvents = computed(() => activeEvents.value.filter(e => e.critical))

const activeProviders = computed(() => providerStatuses.value.filter(p => p.status === 'ACTIVE'))
const downProviders = computed(() => providerStatuses.value.filter(p => p.status === 'DOWN'))

import { computed } from 'vue'

function providerStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'DEGRADED': return 'warning'
    case 'DOWN': return 'danger'
    default: return 'neutral'
  }
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

function severityVariant(severity: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    case 'LOW': return 'success'
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
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Notification Management" subtitle="Overview of notification system health">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadAll">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading notification data..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadAll" />

    <template v-else>
      <!-- Error banner -->
      <div v-if="errorCode" class="p-sm bg-danger-500/10 border border-danger-500/30 rounded text-xs text-danger-500 flex items-center gap-sm">
        <span>alert-triangle</span>
        <span>{{ error }}</span>
        <code class="ml-auto text-[10px] font-mono bg-danger-500/10 px-xs py-0.5 rounded">{{ errorCode }}</code>
      </div>

      <!-- Tab navigation -->
      <div class="flex gap-xs border-b border-default">
        <button class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'overview' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'overview'">Overview</button>
        <button class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'events' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'events'">Event Definitions</button>
        <button class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'deliveries' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'deliveries'">Delivery Logs</button>
        <button class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'providers' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'providers'">Providers</button>
      </div>

      <!-- Overview Tab -->
      <template v-if="activeTab === 'overview'">
        <div class="grid grid-cols-4 gap-md">
          <MetricCard :value="activeEvents.length" label="Active Events" icon="file-text" />
          <MetricCard :value="criticalEvents.length" label="Critical Events" icon="🚨" />
          <MetricCard :value="deliveriesTotal" label="Total Deliveries" icon="inbox" />
          <MetricCard :value="activeProviders.length" :label="`Active Providers (${downProviders.length} down)`" icon="plug" />
        </div>

        <!-- Provider health summary -->
        <div v-if="downProviders.length > 0" class="c-card border-danger-500 bg-danger-500/5">
          <div class="c-card-body flex items-center gap-md">
            <span class="text-xl">alert-triangle</span>
            <div class="flex-1">
              <div class="text-sm font-semibold text-danger-500">Provider Issues Detected</div>
              <div class="text-xs text-text-secondary mt-xs">
                <span v-for="(p, i) in downProviders" :key="p.provider">
                  {{ p.provider }} ({{ p.channel }})<span v-if="i < downProviders.length - 1">, </span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Recent deliveries -->
        <div class="c-card">
          <div class="c-card-header flex items-center justify-between">
            <h3 class="text-sm font-semibold text-text-primary">Recent Deliveries</h3>
            <button class="text-xs text-primary-500 hover:text-primary-400" @click="activeTab = 'deliveries'">View all →</button>
          </div>
          <div class="c-card-body">
            <EmptyState v-if="recentDeliveries.length === 0" icon="mail" title="No recent deliveries" description="Delivery records will appear here." />
            <div v-else class="space-y-sm">
              <div v-for="d in recentDeliveries" :key="d.id" class="flex items-center justify-between p-sm rounded border border-default">
                <div class="flex items-center gap-md">
                  <StatusBadge :variant="deliveryStatusVariant(d.status)" :label="d.status" size="sm" />
                  <div>
                    <div class="text-sm text-text-primary">{{ d.eventKey }}</div>
                    <div class="text-xs text-text-muted">{{ d.channel }} → {{ d.destinationMasked }}</div>
                  </div>
                </div>
                <div class="text-xs text-text-muted">{{ formatTime(d.createdAt) }}</div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Events Tab -->
      <template v-if="activeTab === 'events'">
        <div class="flex items-center justify-between">
          <div class="text-sm text-text-muted">{{ eventDefs.length }} event definitions ({{ activeEvents.length }} active, {{ archivedEvents.length }} archived)</div>
          <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/admin/notifications/events')">
            Manage Events
          </button>
        </div>

        <div class="c-card">
          <div class="c-card-body">
            <EmptyState v-if="eventDefs.length === 0" icon="file-text" title="No event definitions" description="No notification event definitions configured." />
            <table v-else class="w-full text-sm">
              <thead>
                <tr class="border-b border-default text-xs text-text-muted">
                  <th class="text-left px-sm py-sm">Event Key</th>
                  <th class="text-left px-sm py-sm">Name</th>
                  <th class="text-left px-sm py-sm">Category</th>
                  <th class="text-left px-sm py-sm">Severity</th>
                  <th class="text-left px-sm py-sm">Channels</th>
                  <th class="text-left px-sm py-sm">Status</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="evt in eventDefs" :key="evt.eventKey" class="border-b border-default/50 hover:bg-bg-surface-hover">
                  <td class="px-sm py-sm text-xs font-mono text-primary-400">{{ evt.eventKey }}</td>
                  <td class="px-sm py-sm text-text-primary">
                    <div class="flex items-center gap-xs">
                      {{ evt.name }}
                      <span v-if="evt.critical" class="text-[10px] px-1 py-0.5 rounded bg-danger-500/10 text-danger-500">CRITICAL</span>
                    </div>
                  </td>
                  <td class="px-sm py-sm text-text-muted">{{ evt.category }}</td>
                  <td class="px-sm py-sm"><StatusBadge :variant="severityVariant(evt.severity)" :label="evt.severity" size="sm" /></td>
                  <td class="px-sm py-sm text-xs text-text-muted">{{ evt.supportedChannels.join(', ') }}</td>
                  <td class="px-sm py-sm">
                    <StatusBadge :variant="evt.archived ? 'neutral' : 'success'" :label="evt.archived ? 'Archived' : 'Active'" size="sm" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>

      <!-- Deliveries Tab -->
      <template v-if="activeTab === 'deliveries'">
        <div class="flex items-center justify-between">
          <div class="text-sm text-text-muted">{{ deliveriesTotal }} total deliveries</div>
          <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/admin/notifications/deliveries')">
            View Full Logs
          </button>
        </div>

        <div class="c-card">
          <div class="c-card-body">
            <EmptyState v-if="recentDeliveries.length === 0" icon="mail" title="No delivery logs" description="Delivery records will appear here." />
            <table v-else class="w-full text-sm">
              <thead>
                <tr class="border-b border-default text-xs text-text-muted">
                  <th class="text-left px-sm py-sm">ID</th>
                  <th class="text-left px-sm py-sm">Event</th>
                  <th class="text-left px-sm py-sm">Channel</th>
                  <th class="text-left px-sm py-sm">Status</th>
                  <th class="text-left px-sm py-sm">Destination</th>
                  <th class="text-left px-sm py-sm">Retries</th>
                  <th class="text-left px-sm py-sm">Created</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in recentDeliveries" :key="d.id" class="border-b border-default/50 hover:bg-bg-surface-hover">
                  <td class="px-sm py-sm text-xs font-mono text-text-muted">{{ d.id.slice(0, 8) }}</td>
                  <td class="px-sm py-sm text-xs font-mono text-primary-400">{{ d.eventKey }}</td>
                  <td class="px-sm py-sm text-text-primary">{{ d.channel }}</td>
                  <td class="px-sm py-sm"><StatusBadge :variant="deliveryStatusVariant(d.status)" :label="d.status" size="sm" /></td>
                  <td class="px-sm py-sm text-xs text-text-muted">{{ d.destinationMasked }}</td>
                  <td class="px-sm py-sm text-text-muted">{{ d.retryCount }}</td>
                  <td class="px-sm py-sm text-xs text-text-muted">{{ formatTime(d.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>

      <!-- Providers Tab -->
      <template v-if="activeTab === 'providers'">
        <div class="flex items-center justify-between">
          <div class="text-sm text-text-muted">{{ providerStatuses.length }} providers configured</div>
          <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/admin/notifications/providers')">
            Manage Providers
          </button>
        </div>

        <div class="grid grid-cols-2 gap-md">
          <div v-for="provider in providerStatuses" :key="`${provider.provider}-${provider.channel}`" class="c-card">
            <div class="c-card-body">
              <div class="flex items-center justify-between mb-sm">
                <div class="flex items-center gap-sm">
                  <span class="text-sm font-semibold text-text-primary">{{ provider.provider }}</span>
                  <span class="text-xs text-text-muted font-mono">({{ provider.channel }})</span>
                </div>
                <StatusBadge :variant="providerStatusVariant(provider.status)" :label="provider.status" size="md" />
              </div>
              <div class="grid grid-cols-3 gap-md text-xs">
                <div>
                  <div class="text-text-muted">Success Rate</div>
                  <div class="text-text-primary font-medium">{{ (provider.successRate * 100).toFixed(1) }}%</div>
                </div>
                <div>
                  <div class="text-text-muted">Avg Latency</div>
                  <div class="text-text-primary font-medium">{{ provider.avgLatencyMs }}ms</div>
                </div>
                <div>
                  <div class="text-text-muted">Last Success</div>
                  <div class="text-text-primary font-medium">{{ formatTime(provider.lastSuccessAt) }}</div>
                </div>
              </div>
              <div v-if="provider.lastFailureAt" class="mt-sm p-xs bg-danger-500/10 rounded text-[10px] text-danger-500">
                Last failure: {{ formatTime(provider.lastFailureAt) }}
              </div>
            </div>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>
