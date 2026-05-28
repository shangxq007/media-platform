<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { QuotaBillingAPI } from '@/api/admin/quota-billing'
import type { QuotaBucket, TenantUsage, BillingState } from '@/api/admin/quota-billing'
import { useAdminTenantSelection } from '@/composables/useAdminTenantSelection'

const loading = ref(true)
const { tenants: _tenants, selectedTenantId, loading: _tenantsLoading } = useAdminTenantSelection()
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
      QuotaBillingAPI.getQuota(selectedTenantId.value),
      QuotaBillingAPI.getUsage(selectedTenantId.value),
      QuotaBillingAPI.getBillingState(selectedTenantId.value),
      QuotaBillingAPI.getTotalRevenue(selectedTenantId.value),
      QuotaBillingAPI.getRecentCommerceEvents(selectedTenantId.value),
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
    await QuotaBillingAPI.resetQuota(selectedTenantId.value)
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
          v-model="selectedTenantId"
          type="text"
          class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Tenant ID"
        />
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="loadData">Refresh</button>
        <button class="px-3 py-1.5 bg-yellow-600/20 text-warning text-sm rounded" @click="resetQuota">Reset Quota</button>
      </div>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>
    <template v-else>
      <div class="grid grid-cols-2 gap-6">
        <!-- Quota Buckets -->
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-text-primary">Quota Buckets</h2>
          <div v-if="quotas.length === 0" class="text-xs text-text-tertiary">No quota data</div>
          <div v-else class="space-y-3">
            <div v-for="q in quotas" :key="q.key">
              <div class="flex items-center justify-between text-xs mb-1">
                <span class="text-text-primary">{{ q.key }}</span>
                <span class="text-text-tertiary">{{ q.used || 0 }} / {{ q.limit || '∞' }} {{ q.unit || '' }}</span>
              </div>
              <div class="w-full bg-surface-3 rounded-full h-2">
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
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-text-primary">Usage</h2>
          <div v-if="!usage" class="text-xs text-text-tertiary">No usage data</div>
          <div v-else class="space-y-2">
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Render Minutes</span>
              <span class="text-white">{{ usage.renderMinutes || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Storage (GB)</span>
              <span class="text-white">{{ usage.storageGb || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">API Calls</span>
              <span class="text-white">{{ usage.apiCalls || 0 }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Period</span>
              <span class="text-white">{{ usage.period || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- Billing -->
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-text-primary">Billing</h2>
          <div v-if="!billing" class="text-xs text-text-tertiary">No billing data</div>
          <div v-else class="space-y-2">
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Balance</span>
              <span class="text-white font-mono">{{ billing.balance || 0 }} {{ billing.currency || 'USD' }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Status</span>
              <span class="text-success">{{ billing.status || 'CURRENT' }}</span>
            </div>
            <div class="flex justify-between text-xs">
              <span class="text-text-secondary">Last Invoice</span>
              <span class="text-white">{{ billing.lastInvoiceAt || '—' }}</span>
            </div>
          </div>
        </div>

        <!-- Revenue -->
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-text-primary">Revenue</h2>
          <div class="text-2xl font-bold text-success">
            {{ revenue?.total?.toFixed(2) || '0.00' }} {{ revenue?.currency || 'USD' }}
          </div>
          <div class="text-xs text-text-tertiary mt-1">Total for tenant</div>
        </div>
      </div>

      <!-- Commerce Events -->
      <h2 class="text-base font-semibold mt-8 mb-4">Recent Commerce Events</h2>
      <div v-if="commerceEvents.length === 0" class="text-text-tertiary text-sm">No events</div>
      <div v-else class="bg-surface-2 border border-border-subtle rounded-lg overflow-hidden">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-xs text-text-secondary">
              <th class="text-left px-3 py-2">Event</th>
              <th class="text-left px-3 py-2">Details</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(evt, idx) in commerceEvents" :key="idx" class="border-b border-border-subtle/50">
              <td class="px-3 py-2 text-xs font-mono">{{ JSON.stringify(evt).slice(0, 80) }}...</td>
              <td class="px-3 py-2 text-xs text-text-secondary">—</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
