<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { QuotaBillingAPI } from '@/api/admin/quota-billing'
import type { QuotaBucket, TenantUsage, BillingState } from '@/api/admin/quota-billing'

const loading = ref(true)
const tenantId = ref('tenant-1')
const quotas = ref<QuotaBucket[]>([])
const usage = ref<TenantUsage | null>(null)
const billing = ref<BillingState | null>(null)
const revenue = ref<{ total: number; currency?: string } | null>(null)
const commerceEvents = ref<unknown[]>([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [q, u, b, r, e] = await Promise.allSettled([
      QuotaBillingAPI.getQuota(tenantId.value),
      QuotaBillingAPI.getUsage(tenantId.value),
      QuotaBillingAPI.getBillingState(tenantId.value),
      QuotaBillingAPI.getTotalRevenue(tenantId.value),
      QuotaBillingAPI.getRecentCommerceEvents(tenantId.value),
    ])
    if (q.status === 'fulfilled') quotas.value = q.value
    if (u.status === 'fulfilled') usage.value = u.value
    if (b.status === 'fulfilled') billing.value = b.value
    if (r.status === 'fulfilled') revenue.value = r.value
    if (e.status === 'fulfilled') commerceEvents.value = e.value
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function resetQuota() {
  await QuotaBillingAPI.resetQuota(tenantId.value)
  await loadData()
}

function quotaPercent(q: QuotaBucket): number {
  if (!q.limit || q.limit === 0) return 0
  return Math.min(100, Math.round(((q.used || 0) / q.limit) * 100))
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Quota & Billing</h1>
      <div class="flex items-center gap-3">
        <input
          v-model="tenantId"
          type="text"
          class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Tenant ID"
        />
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded" @click="loadData">Refresh</button>
        <button class="px-3 py-1.5 bg-yellow-600/20 text-yellow-300 text-sm rounded" @click="resetQuota">Reset Quota</button>
      </div>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <template v-else>
      <div class="grid grid-cols-2 gap-6">
        <!-- Quota Buckets -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Quota Buckets</h2>
          <div v-if="quotas.length === 0" class="text-xs text-gray-500">No quota data</div>
          <div v-else class="space-y-3">
            <div v-for="q in quotas" :key="q.key">
              <div class="flex items-center justify-between text-xs mb-1">
                <span class="text-gray-300">{{ q.key }}</span>
                <span class="text-gray-500">{{ q.used || 0 }} / {{ q.limit || '∞' }} {{ q.unit || '' }}</span>
              </div>
              <div class="w-full bg-gray-700 rounded-full h-2">
                <div
                  class="h-2 rounded-full transition-all"
                  :class="quotaPercent(q) > 80 ? 'bg-red-500' : quotaPercent(q) > 50 ? 'bg-yellow-500' : 'bg-green-500'"
                  :style="{ width: quotaPercent(q) + '%' }"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Usage -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Usage</h2>
          <div v-if="!usage" class="text-xs text-gray-500">No usage data</div>
          <div v-else class="space-y-2">
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Render Minutes</span>
              <span class="text-white">{{ usage.renderMinutes || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Storage (GB)</span>
              <span class="text-white">{{ usage.storageGb || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">API Calls</span>
              <span class="text-white">{{ usage.apiCalls || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Period</span>
              <span class="text-white">{{ usage.period || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- Billing -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Billing</h2>
          <div v-if="!billing" class="text-xs text-gray-500">No billing data</div>
          <div v-else class="space-y-2">
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Balance</span>
              <span class="text-white font-mono">{{ billing.balance || 0 }} {{ billing.currency || 'USD' }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Status</span>
              <span class="text-green-400">{{ billing.status || 'CURRENT' }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-gray-400">Last Invoice</span>
              <span class="text-white">{{ billing.lastInvoiceAt || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- Revenue -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Revenue</h2>
          <div class="text-2xl font-bold text-green-400">
            {{ revenue?.total?.toFixed(2) || '0.00' }} {{ revenue?.currency || 'USD' }}
          </div>
          <div class="text-xs text-gray-500 mt-1">Total for tenant</div>
        </div>
      </div>

      <!-- Commerce Events -->
      <h2 class="text-base font-semibold mt-8 mb-4">Recent Commerce Events</h2>
      <div v-if="commerceEvents.length === 0" class="text-gray-500 text-sm">No events</div>
      <div v-else class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-700 text-xs text-gray-400">
              <th class="text-left px-3 py-2">Event</th>
              <th class="text-left px-3 py-2">Details</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(evt, idx) in commerceEvents" :key="idx" class="border-b border-gray-700/50">
              <td class="px-3 py-2 text-xs font-mono">{{ JSON.stringify(evt).slice(0, 80) }}...</td>
              <td class="px-3 py-2 text-xs text-gray-400">—</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
