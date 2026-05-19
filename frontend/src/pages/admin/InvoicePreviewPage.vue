<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BillingAdminAPI } from '@/api/admin/billing-admin'
import type { Invoice } from '@/types'

const loading = ref(true)
const invoices = ref<Invoice[]>([])
const previewTenantId = ref('')
const preview = ref<Invoice | null>(null)
const generating = ref(false)
const filterStatus = ref('')

onMounted(loadInvoices)

async function loadInvoices() {
  loading.value = true
  try {
    invoices.value = await BillingAdminAPI.getInvoices(undefined, filterStatus.value || undefined)
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function generatePreview() {
  if (!previewTenantId.value) return
  generating.value = true
  try {
    preview.value = await BillingAdminAPI.getInvoicePreview(previewTenantId.value)
  } catch { /* handle error */ }
  generating.value = false
}

async function issueInvoice(invoiceId: string) {
  await BillingAdminAPI.issueInvoice(invoiceId)
  await loadInvoices()
}

async function voidInvoice(invoiceId: string) {
  await BillingAdminAPI.voidInvoice(invoiceId)
  await loadInvoices()
}

function statusClass(status: string): string {
  switch (status) {
    case 'PAID': return 'bg-green-600/20 text-green-400'
    case 'ISSUED': return 'bg-blue-600/20 text-blue-400'
    case 'DRAFT': return 'bg-yellow-600/20 text-yellow-400'
    case 'OVERDUE': return 'bg-red-600/20 text-red-400'
    case 'VOID': return 'bg-gray-600/20 text-gray-400'
    default: return 'bg-gray-600/20 text-gray-400'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-xl font-bold text-white">Invoices</h1>
      <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded text-white" @click="loadInvoices">Refresh</button>
    </div>

    <!-- Preview Generator -->
    <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 space-y-3">
      <h3 class="text-sm font-semibold text-gray-300">Preview Invoice</h3>
      <div class="flex gap-2">
        <input v-model="previewTenantId" placeholder="Tenant ID" class="flex-1 bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" />
        <button class="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-sm rounded text-white" :disabled="generating || !previewTenantId" @click="generatePreview">
          {{ generating ? 'Generating...' : 'Preview' }}
        </button>
      </div>
      <div v-if="preview" class="p-3 rounded bg-gray-700/30 border border-gray-600">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-mono text-white">{{ preview.invoiceNumber }}</span>
          <span class="px-1.5 py-0.5 rounded text-[10px]" :class="statusClass(preview.status)">{{ preview.status }}</span>
        </div>
        <div class="text-lg font-bold text-white mb-2">{{ preview.amount.toFixed(2) }} {{ preview.currency }}</div>
        <div class="space-y-1">
          <div v-for="(li, idx) in preview.lineItems" :key="idx" class="flex justify-between text-xs">
            <span class="text-gray-400">{{ li.description }} x{{ li.quantity }}</span>
            <span class="text-white">${{ li.total.toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter -->
    <div class="flex gap-2">
      <select v-model="filterStatus" class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white" @change="loadInvoices">
        <option value="">All Status</option>
        <option value="DRAFT">DRAFT</option>
        <option value="ISSUED">ISSUED</option>
        <option value="PAID">PAID</option>
        <option value="OVERDUE">OVERDUE</option>
        <option value="VOID">VOID</option>
      </select>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="invoices.length === 0" class="text-gray-500 text-sm">No invoices</div>
    <div v-else class="space-y-3">
      <div v-for="inv in invoices" :key="inv.invoiceId" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-3">
            <span class="text-sm font-mono text-white">{{ inv.invoiceNumber }}</span>
            <span class="px-1.5 py-0.5 rounded text-[10px]" :class="statusClass(inv.status)">{{ inv.status }}</span>
          </div>
          <span class="text-lg font-bold text-white">{{ inv.amount.toFixed(2) }} {{ inv.currency }}</span>
        </div>
        <div class="flex gap-4 text-xs text-gray-500 mb-2">
          <span>Issued: {{ inv.issuedAt }}</span>
          <span>Due: {{ inv.dueAt }}</span>
          <span v-if="inv.paidAt">Paid: {{ inv.paidAt }}</span>
        </div>
        <div class="flex gap-2">
          <button v-if="inv.status === 'DRAFT'" class="text-[10px] text-blue-400 hover:text-blue-300" @click="issueInvoice(inv.invoiceId)">Issue</button>
          <button v-if="inv.status !== 'VOID' && inv.status !== 'PAID'" class="text-[10px] text-red-400 hover:text-red-300" @click="voidInvoice(inv.invoiceId)">Void</button>
        </div>
      </div>
    </div>
  </div>
</template>
