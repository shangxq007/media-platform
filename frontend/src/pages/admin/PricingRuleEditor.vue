<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { PricingRule } from '@/types'

const loading = ref(true)
const rules = ref<PricingRule[]>([])
const showCreate = ref(false)
const form = ref({
  name: '',
  description: '',
  metric: '',
  tier: 'PRO',
  unitPrice: 0,
  currency: 'USD',
  minimumQuantity: 0,
  maximumQuantity: 0,
  effectiveFrom: '',
  isActive: true
})
const creating = ref(false)

onMounted(loadRules)

async function loadRules() {
  loading.value = true
  try {
    rules.value = await BillingAdminAPI.getPricingRules()
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function createRule() {
  if (!form.value.name || !form.value.metric) return
  creating.value = true
  try {
    await BillingAdminAPI.createPricingRule(form.value)
    showCreate.value = false
    form.value = { name: '', description: '', metric: '', tier: 'PRO', unitPrice: 0, currency: 'USD', minimumQuantity: 0, maximumQuantity: 0, effectiveFrom: '', isActive: true }
    await loadRules()
  } catch { /* handle error */ }
  creating.value = false
}

async function deleteRule(ruleId: string) {
  await BillingAdminAPI.deletePricingRule(ruleId)
  await loadRules()
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Pricing Rules</h1>
      <div class="flex gap-2">
        <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" @click="showCreate = !showCreate">
          {{ showCreate ? 'Cancel' : '+ New Rule' }}
        </button>
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadRules">Refresh</button>
      </div>
    </div>

    <div v-if="showCreate" class="bg-surface-2 border border-border-subtle rounded-lg p-4 space-y-3">
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-text-secondary block mb-1">Name</label>
          <input v-model="form.name" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div>
          <label class="text-xs text-text-secondary block mb-1">Metric</label>
          <input v-model="form.metric" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
        </div>
      </div>
      <div class="grid grid-cols-3 gap-3">
        <div>
          <label class="text-xs text-text-secondary block mb-1">Tier</label>
          <select v-model="form.tier" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white">
            <option value="FREE">FREE</option>
            <option value="PRO">PRO</option>
            <option value="TEAM">TEAM</option>
            <option value="ENTERPRISE">ENTERPRISE</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-text-secondary block mb-1">Unit Price</label>
          <input v-model.number="form.unitPrice" type="number" step="0.001" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
        </div>
        <div>
          <label class="text-xs text-text-secondary block mb-1">Currency</label>
          <input v-model="form.currency" class="w-full bg-surface-3 border border-border-default rounded px-2 py-1.5 text-sm text-white" />
        </div>
      </div>
      <button class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-sm rounded text-white" :disabled="creating || !form.name || !form.metric" @click="createRule">
        {{ creating ? 'Creating...' : 'Create Rule' }}
      </button>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="rules.length === 0" class="text-text-tertiary text-sm">No pricing rules</div>
    <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-border-subtle text-xs text-text-secondary">
            <th class="text-left px-4 py-2">Name</th>
            <th class="text-left px-4 py-2">Metric</th>
            <th class="text-left px-4 py-2">Tier</th>
            <th class="text-right px-4 py-2">Unit Price</th>
            <th class="text-left px-4 py-2">Effective From</th>
            <th class="text-left px-4 py-2">Status</th>
            <th class="text-left px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rules" :key="r.ruleId" class="border-b border-border-subtle/50">
            <td class="px-4 py-2 text-xs text-white">{{ r.name }}</td>
            <td class="px-4 py-2 text-xs text-text-primary">{{ r.metric }}</td>
            <td class="px-4 py-2 text-xs text-text-secondary">{{ r.tier }}</td>
            <td class="px-4 py-2 text-xs text-right text-white font-mono">{{ r.unitPrice.toFixed(4) }} {{ r.currency }}</td>
            <td class="px-4 py-2 text-xs text-text-secondary">{{ r.effectiveFrom }}</td>
            <td class="px-4 py-2"><span class="px-1.5 py-0.5 rounded text-[10px]" :class="r.isActive ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-secondary'">{{ r.isActive ? 'Active' : 'Inactive' }}</span></td>
            <td class="px-4 py-2"><button class="text-[10px] text-danger hover:text-danger" @click="deleteRule(r.ruleId)">Delete</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
