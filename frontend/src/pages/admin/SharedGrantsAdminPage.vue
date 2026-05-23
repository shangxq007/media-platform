<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { SharedResourcesAdminAPI, type SharedResourceGrantRow } from '@/api/admin/shared-resources-admin'

const grants = ref<SharedResourceGrantRow[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const includeRevoked = ref(false)
const tenantId = ref('tenant-1')
const granting = ref(false)

const grantForm = ref({
  resourceType: 'project',
  resourceId: '',
  resourceName: '',
  sharedWithUserId: '',
  permission: 'READ',
  sharedByUserId: 'admin',
})

onMounted(loadGrants)

async function loadGrants() {
  loading.value = true
  error.value = null
  try {
    grants.value = await SharedResourcesAdminAPI.listGrants(
      tenantId.value || undefined,
      includeRevoked.value,
    )
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load shared grants'
  } finally {
    loading.value = false
  }
}

async function submitGrant() {
  if (!grantForm.value.resourceId.trim() || !grantForm.value.sharedWithUserId.trim()) {
    error.value = 'Resource ID and recipient user ID are required'
    return
  }
  granting.value = true
  error.value = null
  success.value = null
  try {
    const result = await SharedResourcesAdminAPI.grantSharedResource({
      tenantId: tenantId.value || undefined,
      resourceType: grantForm.value.resourceType,
      resourceId: grantForm.value.resourceId.trim(),
      resourceName: grantForm.value.resourceName.trim() || grantForm.value.resourceId.trim(),
      sharedWithUserId: grantForm.value.sharedWithUserId.trim(),
      permission: grantForm.value.permission,
      sharedByUserId: grantForm.value.sharedByUserId.trim() || 'admin',
    })
    success.value = `Granted ${result.grantId} to ${result.sharedWithUserId}`
    grantForm.value.resourceId = ''
    grantForm.value.resourceName = ''
    grantForm.value.sharedWithUserId = ''
    await loadGrants()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Grant failed'
  } finally {
    granting.value = false
  }
}

async function revoke(grantId: string) {
  if (!confirm(`Revoke grant ${grantId}?`)) return
  error.value = null
  success.value = null
  try {
    await SharedResourcesAdminAPI.revokeGrant(grantId)
    success.value = `Revoked ${grantId}`
    await loadGrants()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Revoke failed'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Shared Resource Grants</h1>
      <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadGrants">
        Refresh
      </button>
    </div>

    <section class="p-4 rounded-lg border border-gray-700 bg-gray-800/40 space-y-4">
      <h2 class="text-sm font-semibold text-white">Grant access</h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        <label class="text-sm text-gray-400">
          Tenant ID
          <input
            v-model="tenantId"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
            placeholder="tenant-1"
          />
        </label>
        <label class="text-sm text-gray-400">
          Resource type
          <select
            v-model="grantForm.resourceType"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
          >
            <option value="project">project</option>
            <option value="export">export</option>
          </select>
        </label>
        <label class="text-sm text-gray-400">
          Resource ID
          <input
            v-model="grantForm.resourceId"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
            placeholder="proj-abc"
          />
        </label>
        <label class="text-sm text-gray-400">
          Display name
          <input
            v-model="grantForm.resourceName"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
            placeholder="Optional label"
          />
        </label>
        <label class="text-sm text-gray-400">
          Shared with (user ID)
          <input
            v-model="grantForm.sharedWithUserId"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
            placeholder="user-2"
          />
        </label>
        <label class="text-sm text-gray-400">
          Permission
          <select
            v-model="grantForm.permission"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
          >
            <option value="READ">READ</option>
            <option value="WRITE">WRITE</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </label>
        <label class="text-sm text-gray-400">
          Shared by (user ID)
          <input
            v-model="grantForm.sharedByUserId"
            class="block mt-1 w-full px-2 py-1 bg-gray-900 border border-gray-600 rounded text-white text-sm"
            placeholder="admin"
          />
        </label>
      </div>
      <button
        class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white disabled:opacity-50"
        :disabled="granting"
        @click="submitGrant"
      >
        {{ granting ? 'Granting…' : 'Grant access' }}
      </button>
    </section>

    <div class="flex flex-wrap gap-3 items-end">
      <label class="flex items-center gap-2 text-sm text-gray-400">
        <input v-model="includeRevoked" type="checkbox" class="rounded" />
        Include revoked
      </label>
      <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadGrants">
        Apply filters
      </button>
    </div>

    <p v-if="success" class="text-green-400 text-sm">{{ success }}</p>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading" class="text-gray-400 text-sm">Loading...</p>

    <table v-else class="w-full text-sm text-left text-gray-300 border border-gray-700 rounded overflow-hidden">
      <thead class="bg-gray-800 text-gray-400 uppercase text-xs">
        <tr>
          <th class="px-3 py-2">Grant</th>
          <th class="px-3 py-2">Resource</th>
          <th class="px-3 py-2">Recipient</th>
          <th class="px-3 py-2">Permission</th>
          <th class="px-3 py-2">Status</th>
          <th class="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="g in grants" :key="g.grantId" class="border-t border-gray-700 hover:bg-gray-800/50">
          <td class="px-3 py-2 font-mono text-xs">{{ g.grantId }}</td>
          <td class="px-3 py-2">{{ g.type }} / {{ g.name || g.id }}</td>
          <td class="px-3 py-2">{{ g.sharedWithUserId }}</td>
          <td class="px-3 py-2">{{ g.permission }}</td>
          <td class="px-3 py-2">{{ g.grantStatus || g.status }}</td>
          <td class="px-3 py-2 text-right">
            <button
              v-if="(g.grantStatus || g.status) === 'ACTIVE'"
              class="text-red-400 hover:text-red-300 text-xs"
              @click="revoke(g.grantId)"
            >
              Revoke
            </button>
          </td>
        </tr>
        <tr v-if="grants.length === 0">
          <td colspan="6" class="px-3 py-4 text-center text-gray-500">No grants found</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
