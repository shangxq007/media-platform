<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { BillingQuote } from '@/types'

const loading = ref(true)
const quotes = ref<BillingQuote[]>([])
const generating = ref(false)
const newTenantId = ref('')
const newTier = ref('PRO')

onMounted(loadQuotes)

async function loadQuotes() {
  loading.value = true
  try {
    quotes.value = await BillingAdminAPI.getBillingQuotes()
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function generateQuote() {
  if (!newTenantId.value) return
  generating.value = true
  try {
    await BillingAdminAPI.createBillingQuote(newTenantId.value, newTier.value)
    newTenantId.value = ''
    await loadQuotes()
  } catch { /* handle error */ }
  generating.value = false
}
</script>

<template>
  <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-text-primary">Billing Quotes</h3>
      <button class="text-[10px] text-info hover:text-info" @click="loadQuotes">Refresh</button>
    </div>

    <div class="flex gap-2">
      <input v-model="newTenantId" placeholder="Tenant ID" class="flex-1 bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white" />
      <select v-model="newTier" class="bg-surface-3 border border-border-default rounded px-2 py-1 text-xs text-white">
        <option value="FREE">FREE</option>
        <option value="PRO">PRO</option>
        <option value="TEAM">TEAM</option>
        <option value="ENTERPRISE">ENTERPRISE</option>
      </select>
      <button class="px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded" :disabled="generating || !newTenantId" @click="generateQuote">
        {{ generating ? '...' : 'Generate' }}
      </button>
    </div>

    <div v-if="loading" class="text-text-tertiary text-xs">Loading...</div>
    <div v-else-if="quotes.length === 0" class="text-text-tertiary text-xs">No quotes</div>
    <div v-else class="space-y-2">
      <div v-for="quote in quotes" :key="quote.quoteId" class="p-3 rounded bg-surface-3/20">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <span class="text-xs text-white font-mono">{{ quote.tenantId }}</span>
            <span class="px-1.5 py-0.5 rounded bg-surface-3 text-[10px] text-text-primary">{{ quote.tier }}</span>
          </div>
          <span class="text-xs text-text-tertiary">Valid until: {{ quote.validUntil }}</span>
        </div>
        <div class="grid grid-cols-2 gap-2 text-xs mb-2">
          <div><span class="text-text-secondary">Monthly:</span> <span class="text-white font-mono">${{ quote.monthlyEstimate.toFixed(2) }}</span></div>
          <div><span class="text-text-secondary">Annual:</span> <span class="text-white font-mono">${{ quote.annualEstimate.toFixed(2) }}</span></div>
        </div>
        <div v-if="quote.breakdown.length" class="space-y-0.5">
          <div v-for="(li, idx) in quote.breakdown" :key="idx" class="flex justify-between text-[10px]">
            <span class="text-text-secondary">{{ li.description }} x{{ li.quantity }}</span>
            <span class="text-text-primary">${{ li.total.toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
