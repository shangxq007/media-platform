<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { UsageRecord } from '@/types'

const loading = ref(true)
const records = ref<UsageRecord[]>([])
const total = ref(0)
const page = ref(0)
const pageSize = 50
const tenantId = ref('')
const tab = ref<'raw' | 'rated'>('raw')

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const result = tab.value === 'rated'
      ? await BillingAdminAPI.getRatedUsage(tenantId.value || undefined, page.value, pageSize)
      : await BillingAdminAPI.getUsageRecords(tenantId.value || undefined, page.value, pageSize)
    records.value = result.records
    total.value = result.total
  } catch { /* backend may not be running */ }
  loading.value = false
}

function prevPage() {
  if (page.value > 0) { page.value--; loadData() }
}

function nextPage() {
  if ((page.value + 1) * pageSize < total.value) { page.value++; loadData() }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Usage Ledger</h1>
      <div class="flex items-center gap-3">
        <input v-model="tenantId" placeholder="Filter by Tenant ID" class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white w-48" />
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded text-white" @click="loadData">Refresh</button>
      </div>
    </div>

    <div class="flex gap-1 border-b border-border-subtle">
      <button class="px-4 py-2 text-sm" :class="tab === 'raw' ? 'text-white border-b-2 border-blue-500' : 'text-text-secondary hover:text-white'" @click="tab = 'raw'; page = 0; loadData()">Raw Usage</button>
      <button class="px-4 py-2 text-sm" :class="tab === 'rated' ? 'text-white border-b-2 border-blue-500' : 'text-text-secondary hover:text-white'" @click="tab = 'rated'; page = 0; loadData()">Rated Usage</button>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <div v-else-if="records.length === 0" class="text-text-tertiary text-sm">No usage records</div>
    <template v-else>
      <div class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-4 py-2">Tenant</th>
              <th class="text-left px-4 py-2">User</th>
              <th class="text-left px-4 py-2">Metric</th>
              <th class="text-right px-4 py-2">Quantity</th>
              <th class="text-right px-4 py-2">Cost</th>
              <th class="text-left px-4 py-2">Recorded</th>
              <th class="text-left px-4 py-2">Rated</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="r.recordId" class="border-b border-border-subtle/50">
              <td class="px-4 py-2 text-xs text-text-primary font-mono">{{ r.tenantId }}</td>
              <td class="px-4 py-2 text-xs text-text-secondary font-mono">{{ r.userId }}</td>
              <td class="px-4 py-2 text-xs text-white">{{ r.metric }}</td>
              <td class="px-4 py-2 text-xs text-right text-text-primary">{{ r.quantity }} {{ r.unit }}</td>
              <td class="px-4 py-2 text-xs text-right font-mono" :class="r.ratedCost > 0 ? 'text-orange-400' : 'text-text-tertiary'">
                {{ r.ratedCost > 0 ? r.ratedCost.toFixed(4) : '—' }} {{ r.currency }}
              </td>
              <td class="px-4 py-2 text-xs text-text-tertiary">{{ r.recordedAt }}</td>
              <td class="px-4 py-2 text-xs text-text-tertiary">{{ r.ratedAt || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="flex items-center justify-between text-xs text-text-tertiary">
        <span>Showing {{ page * pageSize + 1 }}-{{ Math.min((page + 1) * pageSize, total) }} of {{ total }}</span>
        <div class="flex gap-2">
          <button class="px-2 py-1 bg-surface-3 hover:bg-surface-4 rounded text-white" :disabled="page === 0" @click="prevPage">← Prev</button>
          <button class="px-2 py-1 bg-surface-3 hover:bg-surface-4 rounded text-white" :disabled="(page + 1) * pageSize >= total" @click="nextPage">Next →</button>
        </div>
      </div>
    </template>
  </div>
</template>
