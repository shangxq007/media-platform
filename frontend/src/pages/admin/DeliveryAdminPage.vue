<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { DeliveryAdminAPI, type AdminDeliveryJob, type AdminDeliveryDestination } from '@/api/admin/delivery'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const jobs = ref<AdminDeliveryJob[]>([])
const destinations = ref<AdminDeliveryDestination[]>([])
const retryingId = ref<string | null>(null)

const filter = reactive({
  tenantId: '',
  status: '',
  page: 0,
})

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = null
  try {
    const [jobList, destList] = await Promise.all([
      DeliveryAdminAPI.listJobs({
        tenantId: filter.tenantId || undefined,
        status: filter.status || undefined,
        page: filter.page,
        size: 50,
      }),
      DeliveryAdminAPI.listDestinations(filter.tenantId || undefined),
    ])
    jobs.value = jobList
    destinations.value = destList
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load delivery data'
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

async function retry(job: AdminDeliveryJob) {
  retryingId.value = job.id
  try {
    await DeliveryAdminAPI.retryJob(job.id)
    await loadAll()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Retry failed'
  } finally {
    retryingId.value = null
  }
}

function applyFilter() {
  filter.page = 0
  loadAll()
}
</script>

<template>
  <div class="space-y-xl">
    <PageHeader
      title="Artifact delivery"
      subtitle="Outbound render deliveries to tenant SFTP, WebDAV, SMB, S3, or HTTPS endpoints"
    >
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadAll">Refresh</button>
      </template>
    </PageHeader>

    <div class="flex flex-wrap gap-md items-end">
      <label class="text-sm">
        <span class="text-text-muted block mb-xs">Tenant ID</span>
        <input v-model="filter.tenantId" class="theme-input min-w-[160px]" placeholder="Filter by tenant ID" />
      </label>
      <label class="text-sm">
        <span class="text-text-muted block mb-xs">Status</span>
        <select v-model="filter.status" class="theme-input">
          <option value="">All</option>
          <option value="QUEUED">QUEUED</option>
          <option value="RUNNING">RUNNING</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="FAILED">FAILED</option>
        </select>
      </label>
      <button class="theme-btn theme-btn-primary theme-btn-sm" @click="applyFilter">Apply</button>
    </div>

    <LoadingState v-if="loading" message="Loading delivery jobs..." />
    <ErrorState v-else-if="error && jobs.length === 0" :description="error" @retry="loadAll" />
    <template v-else>
      <p v-if="error" class="text-sm text-danger-500">{{ error }}</p>

      <PageSection title="Delivery jobs">
        <EmptyState
          v-if="jobs.length === 0"
          icon="package"
          title="No delivery jobs"
          description="Jobs appear when renders complete and policies or manual delivery run."
        />
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-text-muted border-b border-default">
                <th class="py-sm pr-md">ID</th>
                <th class="py-sm pr-md">Tenant</th>
                <th class="py-sm pr-md">Render job</th>
                <th class="py-sm pr-md">Status</th>
                <th class="py-sm pr-md">Bytes</th>
                <th class="py-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in jobs" :key="job.id" class="border-b border-default/50">
                <td class="py-sm pr-md font-mono text-xs">{{ job.id }}</td>
                <td class="py-sm pr-md text-xs">{{ job.tenantId }}</td>
                <td class="py-sm pr-md font-mono text-xs">{{ job.renderJobId }}</td>
                <td class="py-sm pr-md">
                  <StatusBadge :variant="statusVariant(job.status)" :label="job.status" size="sm" />
                </td>
                <td class="py-sm pr-md text-xs">{{ job.bytesTransferred ?? '—' }}</td>
                <td class="py-sm">
                  <button
                    v-if="job.status === 'FAILED'"
                    class="theme-btn theme-btn-secondary theme-btn-sm"
                    :disabled="retryingId === job.id"
                    @click="retry(job)"
                  >
                    {{ retryingId === job.id ? 'Retrying…' : 'Retry' }}
                  </button>
                  <span v-if="job.errorMessage" class="text-xs text-danger-500 ml-sm" :title="job.errorMessage">
                    {{ job.errorMessage.slice(0, 40) }}…
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </PageSection>

      <PageSection title="Destinations (all tenants)">
        <EmptyState v-if="destinations.length === 0" icon="archive" title="No destinations" />
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-text-muted border-b border-default">
                <th class="py-sm pr-md">Name</th>
                <th class="py-sm pr-md">Tenant</th>
                <th class="py-sm pr-md">Protocol</th>
                <th class="py-sm">Enabled</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in destinations" :key="d.id" class="border-b border-default/50">
                <td class="py-sm pr-md">{{ d.name }}</td>
                <td class="py-sm pr-md font-mono text-xs">{{ d.tenantId }}</td>
                <td class="py-sm pr-md">{{ d.protocol }}</td>
                <td class="py-sm">
                  <StatusBadge :variant="d.enabled ? 'success' : 'neutral'" :label="d.enabled ? 'Yes' : 'No'" size="sm" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </PageSection>
    </template>
  </div>
</template>
