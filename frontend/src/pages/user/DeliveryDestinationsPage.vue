<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { DeliveryAPI, type DeliveryDestination, type DeliveryPolicy } from '@/api/delivery'
import { ProjectAPI } from '@/api'
import type { Project } from '@/types'
import { getTenantId } from '@/utils/tenant'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const tenantId = getTenantId()

const loading = ref(true)
const error = ref<string | null>(null)
const destinations = ref<DeliveryDestination[]>([])
const projects = ref<Project[]>([])
const policies = ref<DeliveryPolicy[]>([])
const selectedProjectId = ref('')
const probingId = ref<string | null>(null)
const probeMessage = ref<Record<string, string>>({})
const saving = ref(false)

const showCreate = ref(false)
const createForm = reactive({
  name: '',
  protocol: 'SFTP' as string,
  host: '',
  port: '22',
  basePath: '/uploads',
  baseUrl: '',
  bucket: '',
  keyPrefix: '',
  username: '',
  password: '',
  enabled: true,
})

const policyForm = reactive({
  destinationId: '',
  pathTemplate: '{tenantId}/{projectId}/{jobId}/output.mp4',
  triggerMode: 'AUTO' as 'AUTO' | 'MANUAL',
})

onMounted(async () => {
  await loadDestinations()
  try {
    projects.value = await ProjectAPI.list()
    if (projects.value.length > 0) {
      selectedProjectId.value = projects.value[0].id
      await loadPolicies()
    }
  } catch {
    /* projects optional */
  }
})

async function loadDestinations() {
  loading.value = true
  error.value = null
  try {
    destinations.value = await DeliveryAPI.listDestinations(tenantId)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load destinations'
  } finally {
    loading.value = false
  }
}

async function loadPolicies() {
  if (!selectedProjectId.value) {
    policies.value = []
    return
  }
  try {
    policies.value = await DeliveryAPI.listPolicies(tenantId, selectedProjectId.value)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load policies'
  }
}

const protocolOptions = ['SFTP', 'WEBDAV', 'S3_MIRROR', 'SMB', 'HTTPS_PUT'] as const

const createFormExtra = reactive({
  share: '',
  domain: '',
  uploadUrl: '',
})

const createConfig = computed(() => {
  if (createForm.protocol === 'WEBDAV') {
    return { baseUrl: createForm.baseUrl, basePath: createForm.basePath || 'renders' }
  }
  if (createForm.protocol === 'S3_MIRROR') {
    return { bucket: createForm.bucket, keyPrefix: createForm.keyPrefix || '' }
  }
  if (createForm.protocol === 'SMB') {
    return {
      host: createForm.host,
      share: createFormExtra.share,
      port: parseInt(createForm.port, 10) || 445,
      basePath: createForm.basePath || '',
      domain: createFormExtra.domain || undefined,
    }
  }
  if (createForm.protocol === 'HTTPS_PUT') {
    return { uploadUrl: createFormExtra.uploadUrl }
  }
  return {
    host: createForm.host,
    port: parseInt(createForm.port, 10) || 22,
    basePath: createForm.basePath || '/uploads',
  }
})

const createCredentials = computed(() => {
  const creds: Record<string, string> = {}
  if (createForm.username) creds.username = createForm.username
  if (createForm.password) creds.password = createForm.password
  return Object.keys(creds).length ? creds : undefined
})

async function submitDestination() {
  if (!createForm.name.trim()) return
  saving.value = true
  error.value = null
  try {
    await DeliveryAPI.createDestination(tenantId, {
      name: createForm.name.trim(),
      protocol: createForm.protocol,
      config: createConfig.value,
      credentials: createCredentials.value,
      enabled: createForm.enabled,
    })
    showCreate.value = false
    createForm.name = ''
    createForm.password = ''
    await loadDestinations()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to create destination'
  } finally {
    saving.value = false
  }
}

async function probeDestination(dest: DeliveryDestination) {
  probingId.value = dest.id
  probeMessage.value[dest.id] = ''
  try {
    const result = await DeliveryAPI.probeDestination(tenantId, dest.id)
    probeMessage.value[dest.id] = result.ok
      ? 'Connection OK'
      : result.message || 'Probe failed'
  } catch (e: unknown) {
    probeMessage.value[dest.id] = e instanceof Error ? e.message : 'Probe failed'
  } finally {
    probingId.value = null
  }
}

async function submitPolicy() {
  if (!selectedProjectId.value || !policyForm.destinationId) return
  saving.value = true
  error.value = null
  try {
    await DeliveryAPI.createPolicy(tenantId, selectedProjectId.value, {
      destinationId: policyForm.destinationId,
      pathTemplate: policyForm.pathTemplate,
      triggerMode: policyForm.triggerMode,
      artifactSelector: 'FINAL_ONLY',
    })
    await loadPolicies()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to create policy'
  } finally {
    saving.value = false
  }
}

function destinationName(id: string): string {
  return destinations.value.find(d => d.id === id)?.name ?? id
}

async function toggleDestination(dest: DeliveryDestination) {
  saving.value = true
  try {
    await DeliveryAPI.updateDestination(tenantId, dest.id, { enabled: !dest.enabled })
    await loadDestinations()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Update failed'
  } finally {
    saving.value = false
  }
}

async function removeDestination(dest: DeliveryDestination) {
  if (!confirm(`Delete destination "${dest.name}"?`)) return
  saving.value = true
  try {
    await DeliveryAPI.deleteDestination(tenantId, dest.id)
    await loadDestinations()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Delete failed'
  } finally {
    saving.value = false
  }
}

async function removePolicy(pol: DeliveryPolicy) {
  if (!selectedProjectId.value || !confirm('Delete this delivery policy?')) return
  saving.value = true
  try {
    await DeliveryAPI.deletePolicy(tenantId, selectedProjectId.value, pol.id)
    await loadPolicies()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Delete policy failed'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader
      title="Delivery destinations"
      subtitle="Push completed renders to SFTP, WebDAV, or customer S3 buckets"
    >
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadDestinations">Refresh</button>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="showCreate = !showCreate">
          {{ showCreate ? 'Cancel' : 'Add destination' }}
        </button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading destinations..." />
    <ErrorState v-else-if="error && destinations.length === 0" :description="error" @retry="loadDestinations" />

    <template v-else>
      <p v-if="error" class="text-sm text-danger-500">{{ error }}</p>

      <PageSection v-if="showCreate" title="New destination">
        <div class="grid gap-md max-w-xl">
          <label class="block text-sm">
            <span class="text-text-muted">Name</span>
            <input v-model="createForm.name" class="theme-input w-full mt-xs" placeholder="Enterprise SFTP" />
          </label>
          <label class="block text-sm">
            <span class="text-text-muted">Protocol</span>
            <select v-model="createForm.protocol" class="theme-input w-full mt-xs">
              <option v-for="p in protocolOptions" :key="p" :value="p">{{ p }}</option>
            </select>
          </label>
          <template v-if="createForm.protocol === 'SFTP'">
            <label class="block text-sm">
              <span class="text-text-muted">Host</span>
              <input v-model="createForm.host" class="theme-input w-full mt-xs" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Port</span>
              <input v-model="createForm.port" class="theme-input w-full mt-xs" type="number" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Base path</span>
              <input v-model="createForm.basePath" class="theme-input w-full mt-xs" />
            </label>
          </template>
          <template v-else-if="createForm.protocol === 'SMB'">
            <label class="block text-sm">
              <span class="text-text-muted">Host</span>
              <input v-model="createForm.host" class="theme-input w-full mt-xs" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Share name</span>
              <input v-model="createFormExtra.share" class="theme-input w-full mt-xs" placeholder="exports" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Base path (optional)</span>
              <input v-model="createForm.basePath" class="theme-input w-full mt-xs" />
            </label>
          </template>
          <template v-else-if="createForm.protocol === 'HTTPS_PUT'">
            <label class="block text-sm">
              <span class="text-text-muted">Upload URL template</span>
              <input v-model="createFormExtra.uploadUrl" class="theme-input w-full mt-xs"
                placeholder="https://cdn.example.com/{remotePath}" />
            </label>
          </template>
          <template v-else-if="createForm.protocol === 'WEBDAV'">
            <label class="block text-sm">
              <span class="text-text-muted">Base URL</span>
              <input v-model="createForm.baseUrl" class="theme-input w-full mt-xs" placeholder="https://nas.example.com/dav" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Base path</span>
              <input v-model="createForm.basePath" class="theme-input w-full mt-xs" />
            </label>
          </template>
          <template v-else>
            <label class="block text-sm">
              <span class="text-text-muted">Bucket</span>
              <input v-model="createForm.bucket" class="theme-input w-full mt-xs" />
            </label>
            <label class="block text-sm">
              <span class="text-text-muted">Key prefix</span>
              <input v-model="createForm.keyPrefix" class="theme-input w-full mt-xs" />
            </label>
          </template>
          <label class="block text-sm">
            <span class="text-text-muted">Username</span>
            <input v-model="createForm.username" class="theme-input w-full mt-xs" autocomplete="off" />
          </label>
          <label class="block text-sm">
            <span class="text-text-muted">Password / secret</span>
            <input v-model="createForm.password" type="password" class="theme-input w-full mt-xs" autocomplete="new-password" />
          </label>
          <button class="theme-btn theme-btn-primary theme-btn-sm w-fit" :disabled="saving" @click="submitDestination">
            {{ saving ? 'Saving…' : 'Save destination' }}
          </button>
        </div>
      </PageSection>

      <PageSection title="Destinations">
        <EmptyState
          v-if="destinations.length === 0"
          icon="📦"
          title="No destinations"
          description="Add SFTP, WebDAV, or S3 mirror targets for automatic export delivery."
        />
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-text-muted border-b border-default">
                <th class="py-sm pr-md">Name</th>
                <th class="py-sm pr-md">Protocol</th>
                <th class="py-sm pr-md">Status</th>
                <th class="py-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dest in destinations" :key="dest.id" class="border-b border-default/50">
                <td class="py-sm pr-md font-medium">{{ dest.name }}</td>
                <td class="py-sm pr-md font-mono text-xs">{{ dest.protocol }}</td>
                <td class="py-sm pr-md">
                  <StatusBadge :variant="dest.enabled ? 'success' : 'neutral'" :label="dest.enabled ? 'Enabled' : 'Disabled'" size="sm" />
                </td>
                <td class="py-sm flex flex-wrap gap-xs items-center">
                  <button
                    class="theme-btn theme-btn-secondary theme-btn-sm"
                    :disabled="probingId === dest.id"
                    @click="probeDestination(dest)"
                  >
                    {{ probingId === dest.id ? 'Probing…' : 'Probe' }}
                  </button>
                  <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="saving" @click="toggleDestination(dest)">
                    {{ dest.enabled ? 'Disable' : 'Enable' }}
                  </button>
                  <button class="theme-btn theme-btn-ghost theme-btn-sm text-danger-500" :disabled="saving" @click="removeDestination(dest)">
                    Delete
                  </button>
                  <span v-if="probeMessage[dest.id]" class="text-xs" :class="probeMessage[dest.id] === 'Connection OK' ? 'text-success-500' : 'text-text-muted'">
                    {{ probeMessage[dest.id] }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </PageSection>

      <PageSection title="Project auto-delivery">
        <p class="text-sm text-text-muted mb-md">
          When a render completes, matching AUTO policies enqueue delivery jobs to the configured destination.
        </p>
        <div class="flex flex-wrap gap-md mb-md items-end">
          <label class="text-sm">
            <span class="text-text-muted block mb-xs">Project</span>
            <select
              v-model="selectedProjectId"
              class="theme-input min-w-[200px]"
              @change="loadPolicies"
            >
              <option value="">Select project</option>
              <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </label>
          <label v-if="destinations.length" class="text-sm">
            <span class="text-text-muted block mb-xs">Destination</span>
            <select v-model="policyForm.destinationId" class="theme-input min-w-[200px]">
              <option value="">Select destination</option>
              <option v-for="d in destinations" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
          </label>
          <label class="text-sm">
            <span class="text-text-muted block mb-xs">Trigger</span>
            <select v-model="policyForm.triggerMode" class="theme-input">
              <option value="AUTO">AUTO (on render complete)</option>
              <option value="MANUAL">MANUAL</option>
            </select>
          </label>
          <button
            class="theme-btn theme-btn-primary theme-btn-sm"
            :disabled="saving || !selectedProjectId || !policyForm.destinationId"
            @click="submitPolicy"
          >
            Add policy
          </button>
        </div>

        <EmptyState
          v-if="selectedProjectId && policies.length === 0"
          icon="📋"
          title="No policies for this project"
          description="Add a policy to deliver exports automatically when renders finish."
        />
        <ul v-else-if="policies.length" class="space-y-sm text-sm">
          <li
            v-for="pol in policies"
            :key="pol.id"
            class="c-card c-card-body flex flex-wrap gap-md items-center justify-between"
          >
            <span>{{ destinationName(pol.destinationId) }}</span>
            <span class="font-mono text-xs text-text-muted">{{ pol.pathTemplate }}</span>
            <StatusBadge :variant="pol.triggerMode === 'AUTO' ? 'info' : 'neutral'" :label="pol.triggerMode" size="sm" />
            <button class="theme-btn theme-btn-ghost theme-btn-sm text-danger-500" :disabled="saving" @click="removePolicy(pol)">
              Delete
            </button>
          </li>
        </ul>
      </PageSection>
    </template>
  </div>
</template>
