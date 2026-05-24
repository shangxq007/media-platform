<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { DeliveryAPI, type DeliveryDestination, type DeliveryJob } from '@/api/delivery'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

const props = defineProps<{
  tenantId: string
  projectId: string
  jobId: string
  compact?: boolean
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const jobs = ref<DeliveryJob[]>([])
const destinations = ref<DeliveryDestination[]>([])
const selectedDestinationId = ref('')
const busy = ref(false)

onMounted(load)
watch(() => props.jobId, load)

async function load() {
  loading.value = true
  error.value = null
  try {
    const [deliveries, dests] = await Promise.all([
      DeliveryAPI.listDeliveries(props.tenantId, props.projectId, props.jobId),
      DeliveryAPI.listDestinations(props.tenantId),
    ])
    jobs.value = deliveries
    destinations.value = dests.filter(d => d.enabled)
    if (!selectedDestinationId.value && destinations.value.length > 0) {
      selectedDestinationId.value = destinations.value[0].id
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load deliveries'
  } finally {
    loading.value = false
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'QUEUED':
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'neutral'
  }
}

function formatBytes(n?: number | null): string {
  if (n == null || n === 0) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

async function triggerManual() {
  if (!selectedDestinationId.value) return
  busy.value = true
  error.value = null
  try {
    await DeliveryAPI.triggerDeliver(
      props.tenantId,
      props.projectId,
      props.jobId,
      selectedDestinationId.value
    )
    await load()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Manual delivery failed'
  } finally {
    busy.value = false
  }
}

async function retry(job: DeliveryJob) {
  busy.value = true
  error.value = null
  try {
    await DeliveryAPI.retryDelivery(props.tenantId, props.projectId, props.jobId, job.id)
    await load()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Retry failed'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div
    class="rounded border border-border-subtle bg-surface-2/40"
    :class="compact ? 'p-2' : 'p-3 mt-2'"
  >
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs font-medium text-text-primary">Outbound delivery</span>
      <button
        type="button"
        class="text-[10px] text-text-tertiary hover:text-text-primary"
        :disabled="loading"
        @click="load"
      >
        Refresh
      </button>
    </div>

    <LoadingState v-if="loading" message="Loading deliveries..." />

    <p v-else-if="error" class="text-xs text-danger">{{ error }}</p>

    <template v-else>
      <div v-if="jobs.length === 0" class="text-xs text-text-tertiary mb-2">
        No delivery jobs yet. Configure destinations under Settings → Delivery, or trigger manually below.
      </div>

      <div v-else class="space-y-1.5 mb-2 max-h-40 overflow-y-auto">
        <div
          v-for="job in jobs"
          :key="job.id"
          class="flex flex-wrap items-center gap-2 text-[11px] p-1.5 rounded bg-surface-0/50"
        >
          <StatusBadge :variant="statusVariant(job.status)" :label="job.status" size="sm" />
          <span class="font-mono text-text-secondary truncate max-w-[120px]" :title="job.id">{{ job.id.slice(0, 10) }}…</span>
          <span class="text-text-tertiary">{{ formatBytes(job.bytesTransferred) }}</span>
          <span v-if="job.remoteUri" class="text-text-tertiary truncate max-w-[140px]" :title="job.remoteUri">
            {{ job.remoteUri }}
          </span>
          <span v-if="job.errorMessage" class="text-danger truncate max-w-full" :title="job.errorMessage">
            {{ job.errorMessage }}
          </span>
          <button
            v-if="job.status === 'FAILED'"
            type="button"
            class="ml-auto text-[10px] text-info hover:underline"
            :disabled="busy"
            @click="retry(job)"
          >
            Retry
          </button>
        </div>
      </div>

      <div v-if="destinations.length" class="flex flex-wrap items-center gap-2 pt-2 border-t border-border-subtle">
        <select
          v-model="selectedDestinationId"
          class="flex-1 min-w-0 text-xs bg-surface-0 border border-border-default rounded px-2 py-1 text-text-primary"
        >
          <option v-for="d in destinations" :key="d.id" :value="d.id">
            {{ d.name }} ({{ d.protocol }})
          </option>
        </select>
        <button
          type="button"
          class="text-xs px-2 py-1 rounded bg-surface-3 hover:bg-surface-4 text-white disabled:opacity-50"
          :disabled="busy || !selectedDestinationId"
          @click="triggerManual"
        >
          Deliver now
        </button>
      </div>
      <p v-else class="text-[10px] text-text-tertiary pt-1 border-t border-border-subtle">
        <router-link to="/me/delivery-destinations" class="text-info hover:underline">
          Add a delivery destination
        </router-link>
        to push exports to SFTP, WebDAV, or S3.
      </p>
    </template>
  </div>
</template>
