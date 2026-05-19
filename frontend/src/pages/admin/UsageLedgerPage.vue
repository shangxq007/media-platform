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
        <input v-model="tenantId" placeholder="Filter by Tenant ID" class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white w-48" />
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadData">Refresh</button>
      </div>
    </div>

    <div class="flex gap-1 border-b border-gray-700">
      <button class="px-4 py-2 text-sm" :class="tab === 'raw' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="tab = 'raw'; page = 0; loadData()">Raw Usage</button>
      <button class="px-4 py-2 text-sm" :class="tab === 'rated' ? 'text-white border-b-2 border-blue-500' : 'text-gray-400 hover:text-white'" @click="tab = 'rated'; page = 0; loadData()">Rated Usage</button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="records.length === 0" class="text-gray-500 text-sm">No usage records</div>
    <template v-else>
      <div class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-700 text-xs text-gray-400">
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
            <tr v-for="r in records" :key="r.recordId" class="border-b border-gray-700/50">
              <td class="px-4 py-2 text-xs text-gray-300 font-mono">{{ r.tenantId }}</td>
              <td class="px-4 py-2 text-xs text-gray-400 font-mono">{{ r.userId }}</td>
              <td class="px-4 py-2 text-xs text-white">{{ r.metric }}</td>
              <td class="px-4 py-2 text-xs text-right text-gray-300">{{ r.quantity }} {{ r.unit }}</td>
              <td class="px-4 py-2 text-xs text-right font-mono" :class="r.ratedCost > 0 ? 'text-orange-400' : 'text-gray-500'">
                {{ r.ratedCost > 0 ? r.ratedCost.toFixed(4) : '—' }} {{ r.currency }}
              </td>
              <td class="px-4 py-2 text-xs text-gray-500">{{ r.recordedAt }}</td>
              <td class="px-4 py-2 text-xs text-gray-500">{{ r.ratedAt || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="flex items-center justify-between text-xs text-gray-500">
        <span>Showing {{ page * pageSize + 1 }}-{{ Math.min((page + 1) * pageSize, total) }} of {{ total }}</span>
        <div class="flex gap-2">
          <button class="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-white" :disabled="page === 0" @click="prevPage">← Prev</button>
          <button class="px-2 py-1 bg-gray-700 hover:bg-gray-600 rounded text-white" :disabled="(page + 1) * pageSize >= total" @click="nextPage">Next →</button>
        </div>
      </div>
    </template>
  </div>
</template>
