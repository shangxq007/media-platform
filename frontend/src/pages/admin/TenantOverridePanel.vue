<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EntitlementAdminAPI } from '@/api/admin/entitlement-admin'
import type { TenantOverride } from '@/types'

const overrides = ref<TenantOverride[]>([])
const loading = ref(true)
const showCreate = ref(false)
const form = ref({
  tenantId: '',
  featureKey: '',
  overrideType: 'GRANT' as 'GRANT' | 'DENY' | 'QUOTA',
  quotaValue: 0,
  reason: '',
  expiresAt: ''
})
const creating = ref(false)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const result = await EntitlementAdminAPI.getTenantOverrides()
    overrides.value = result
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function createOverride() {
  if (!form.value.tenantId || !form.value.featureKey) return
  creating.value = true
  try {
    await EntitlementAdminAPI.createTenantOverride({
      ...form.value,
      tenantName: '',
      featureName: form.value.featureKey,
      createdBy: 'admin',
      expiresAt: form.value.expiresAt || undefined
    })
    showCreate.value = false
    form.value = { tenantId: '', featureKey: '', overrideType: 'GRANT', quotaValue: 0, reason: '', expiresAt: '' }
    await loadData()
  } catch { /* handle error */ }
  creating.value = false
}

async function deleteOverride(overrideId: string) {
  await EntitlementAdminAPI.deleteTenantOverride(overrideId)
  await loadData()
}

function typeClass(type: string): string {
  switch (type) {
    case 'GRANT': return 'bg-success-muted text-success'
    case 'DENY': return 'bg-danger-muted text-danger'
    case 'QUOTA': return 'bg-yellow-600/20 text-warning'
    default: return 'bg-surface-4/20 text-text-secondary'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Tenant Overrides</h1>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="showCreate = !showCreate">
          {{ showCreate ? 'Cancel' : '+ New Override' }}
        </button>
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadData">Refresh</button>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>

    <template v-else>
      <div v-if="showCreate" class="bg-surface-2 border border-border-subtle rounded-lg p-4 space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-text-secondary block mb-1">Tenant ID</label>
            <input v-model="form.tenantId" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
          </div>
          <div>
            <label class="text-xs text-text-secondary block mb-1">Feature Key</label>
            <input v-model="form.featureKey" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-text-secondary block mb-1">Type</label>
            <select v-model="form.overrideType" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white">
              <option value="GRANT">GRANT</option>
              <option value="DENY">DENY</option>
              <option value="QUOTA">QUOTA</option>
            </select>
          </div>
          <div v-if="form.overrideType === 'QUOTA'">
            <label class="text-xs text-text-secondary block mb-1">Quota Value</label>
            <input v-model.number="form.quotaValue" type="number" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
          </div>
        </div>
        <div>
          <label class="text-xs text-text-secondary block mb-1">Reason</label>
          <input v-model="form.reason" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" :disabled="creating || !form.tenantId || !form.featureKey" @click="createOverride">
          {{ creating ? 'Creating...' : 'Create Override' }}
        </button>
      </div>

      <div v-if="overrides.length === 0" class="text-text-tertiary text-sm">No tenant overrides</div>
      <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-4 py-2">Tenant</th>
              <th class="text-left px-4 py-2">Feature</th>
              <th class="text-left px-4 py-2">Type</th>
              <th class="text-left px-4 py-2">Reason</th>
              <th class="text-left px-4 py-2">Expires</th>
              <th class="text-left px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ov in overrides" :key="ov.overrideId" class="border-b border-border-subtle/50">
              <td class="px-4 py-2 text-xs text-white font-mono">{{ ov.tenantId }}</td>
              <td class="px-4 py-2 text-xs text-text-primary">{{ ov.featureKey }}</td>
              <td class="px-4 py-2"><span class="px-1.5 py-0.5 rounded text-[10px]" :class="typeClass(ov.overrideType)">{{ ov.overrideType }}</span></td>
              <td class="px-4 py-2 text-xs text-text-secondary">{{ ov.reason }}</td>
              <td class="px-4 py-2 text-xs text-text-tertiary">{{ ov.expiresAt || '—' }}</td>
              <td class="px-4 py-2"><button class="text-[10px] text-danger hover:text-danger" @click="deleteOverride(ov.overrideId)">Delete</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
