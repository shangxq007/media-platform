<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EntitlementAdminAPI } from '@/api/admin/entitlement-admin'
import type { QuotaPolicy } from '@/types'

const policies = ref<QuotaPolicy[]>([])
const loading = ref(true)
const showCreate = ref(false)
const form = ref({
  name: '',
  description: '',
  featureKey: '',
  scope: 'GLOBAL' as 'GLOBAL' | 'TIER' | 'TENANT',
  scopeId: '',
  limit: 0,
  period: 'MONTHLY' as 'DAILY' | 'MONTHLY' | 'YEARLY',
  softLimit: 0,
  action: 'WARN' as 'BLOCK' | 'WARN' | 'THROTTLE'
})
const creating = ref(false)

onMounted(loadPolicies)

async function loadPolicies() {
  loading.value = true
  try {
    policies.value = await EntitlementAdminAPI.getQuotaPolicies()
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function createPolicy() {
  if (!form.value.name || !form.value.featureKey) return
  creating.value = true
  try {
    await EntitlementAdminAPI.createQuotaPolicy(form.value)
    showCreate.value = false
    form.value = { name: '', description: '', featureKey: '', scope: 'GLOBAL', scopeId: '', limit: 0, period: 'MONTHLY', softLimit: 0, action: 'WARN' }
    await loadPolicies()
  } catch { /* handle error */ }
  creating.value = false
}

async function deletePolicy(policyId: string) {
  await EntitlementAdminAPI.deleteQuotaPolicy(policyId)
  await loadPolicies()
}

function actionClass(action: string): string {
  switch (action) {
    case 'BLOCK': return 'bg-red-600/20 text-red-400'
    case 'WARN': return 'bg-yellow-600/20 text-yellow-400'
    case 'THROTTLE': return 'bg-blue-600/20 text-blue-400'
    default: return 'bg-gray-600/20 text-gray-400'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Quota Policies</h1>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="showCreate = !showCreate">
          {{ showCreate ? 'Cancel' : '+ New Policy' }}
        </button>
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadPolicies">Refresh</button>
      </div>
    </div>

    <div v-if="showCreate" class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-3">
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-gray-400 block mb-1">Name</label>
          <input v-model="form.name" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Feature Key</label>
          <input v-model="form.featureKey" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
      </div>
      <div class="grid grid-cols-3 gap-3">
        <div>
          <label class="text-xs text-gray-400 block mb-1">Scope</label>
          <select v-model="form.scope" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white">
            <option value="GLOBAL">GLOBAL</option>
            <option value="TIER">TIER</option>
            <option value="TENANT">TENANT</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Period</label>
          <select v-model="form.period" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white">
            <option value="DAILY">DAILY</option>
            <option value="MONTHLY">MONTHLY</option>
            <option value="YEARLY">YEARLY</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Action</label>
          <select v-model="form.action" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white">
            <option value="WARN">WARN</option>
            <option value="BLOCK">BLOCK</option>
            <option value="THROTTLE">THROTTLE</option>
          </select>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-gray-400 block mb-1">Limit</label>
          <input v-model.number="form.limit" type="number" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div>
          <label class="text-xs text-gray-400 block mb-1">Soft Limit</label>
          <input v-model.number="form.softLimit" type="number" class="w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        </div>
      </div>
      <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" :disabled="creating || !form.name || !form.featureKey" @click="createPolicy">
        {{ creating ? 'Creating...' : 'Create Policy' }}
      </button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="policies.length === 0" class="text-gray-500 text-sm">No quota policies</div>
    <div v-else class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-700 text-xs text-gray-400">
            <th class="text-left px-4 py-2">Name</th>
            <th class="text-left px-4 py-2">Feature</th>
            <th class="text-left px-4 py-2">Scope</th>
            <th class="text-left px-4 py-2">Limit</th>
            <th class="text-left px-4 py-2">Period</th>
            <th class="text-left px-4 py-2">Action</th>
            <th class="text-left px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in policies" :key="p.policyId" class="border-b border-gray-700/50">
            <td class="px-4 py-2 text-xs text-white">{{ p.name }}</td>
            <td class="px-4 py-2 text-xs text-gray-300">{{ p.featureKey }}</td>
            <td class="px-4 py-2 text-xs text-gray-400">{{ p.scope }} {{ p.scopeId ? `(${p.scopeId})` : '' }}</td>
            <td class="px-4 py-2 text-xs text-white">{{ p.limit }}</td>
            <td class="px-4 py-2 text-xs text-gray-400">{{ p.period }}</td>
            <td class="px-4 py-2"><span class="px-1.5 py-0.5 rounded text-[10px]" :class="actionClass(p.action)">{{ p.action }}</span></td>
            <td class="px-4 py-2"><button class="text-[10px] text-red-400 hover:text-red-300" @click="deletePolicy(p.policyId)">Delete</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
