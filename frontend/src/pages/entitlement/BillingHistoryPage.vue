<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { BillingLedgerEntry, Invoice } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import DetailDrawer from '@/components/ui/DetailDrawer.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'


const loading = ref(true)
const entries = ref<BillingLedgerEntry[]>([])
const invoices = ref<Invoice[]>([])
const tab = ref<'ledger' | 'invoices'>('ledger')
const page = ref(0)
const total = ref(0)
const selectedEntry = ref<BillingLedgerEntry | null>(null)
const selectedInvoice = ref<Invoice | null>(null)
const drawerOpen = ref(false)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [history, inv] = await Promise.allSettled([
      MeEntitlementAPI.getBillingHistory(page.value),
      MeEntitlementAPI.getInvoices()
    ])
    if (history.status === 'fulfilled') {
      entries.value = history.value.entries
      total.value = history.value.total
    }
    if (inv.status === 'fulfilled') invoices.value = inv.value
  } catch { /* backend may not be running */ }
  loading.value = false
}

function entryStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'PENDING': return 'warning'
    case 'FAILED': case 'REVERSED': return 'danger'
    default: return 'neutral'
  }
}

function entryTypeVariant(type: string): 'danger' | 'success' | 'info' | 'neutral' {
  switch (type) {
    case 'CHARGE': return 'danger'
    case 'CREDIT': return 'success'
    case 'REFUND': return 'info'
    default: return 'neutral'
  }
}

function invoiceStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'PAID': return 'success'
    case 'ISSUED': case 'DRAFT': return 'warning'
    case 'OVERDUE': case 'VOID': return 'danger'
    default: return 'neutral'
  }
}



const ledgerColumns = [
  { key: 'date', label: 'Date', width: '140px' },
  { key: 'type', label: 'Type', width: '100px' },
  { key: 'description', label: 'Description' },
  { key: 'amount', label: 'Amount', width: '140px', align: 'right' as const },
  { key: 'status', label: 'Status', width: '110px' },
]

const invoiceColumns = [
  { key: 'number', label: 'Invoice', width: '140px' },
  { key: 'status', label: 'Status', width: '100px' },
  { key: 'amount', label: 'Amount', width: '140px', align: 'right' as const },
  { key: 'issued', label: 'Issued', width: '120px' },
  { key: 'due', label: 'Due', width: '120px' },
]
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Billing History" subtitle="Ledger entries and invoices">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadData">Refresh</button>
      </template>
    </PageHeader>

    <!-- Tabs -->
    <div class="flex gap-xs border-b border-default">
      <button class="px-lg py-sm text-sm font-medium transition-colors"
        :class="tab === 'ledger' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
        @click="tab = 'ledger'">
        Ledger
      </button>
      <button class="px-lg py-sm text-sm font-medium transition-colors"
        :class="tab === 'invoices' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
        @click="tab = 'invoices'">
        Invoices
      </button>
    </div>

    <LoadingState v-if="loading" message="Loading billing data..." />

    <!-- Ledger Tab -->
    <template v-else-if="tab === 'ledger'">
      <EmptyState v-if="entries.length === 0" title="No ledger entries" description="Your billing ledger is empty." />
      <DataTableShell v-else :columns="ledgerColumns" :total="entries.length" :striped="true" :hoverable="true">
        <template #date="{ row }">
          <span class="text-xs text-text-muted">{{ entries[row]?.createdAt }}</span>
        </template>
        <template #type="{ row }">
          <StatusBadge :variant="entryTypeVariant(entries[row]?.type || '')" :label="entries[row]?.type" />
        </template>
        <template #description="{ row }">
          <span class="text-sm text-text-primary">{{ entries[row]?.description }}</span>
        </template>
        <template #amount="{ row }">
          <span class="text-sm font-mono"
            :class="entries[row]?.type === 'CREDIT' || entries[row]?.type === 'REFUND' ? 'text-success-500' : 'text-text-primary'">
            {{ entries[row]?.type === 'CREDIT' || entries[row]?.type === 'REFUND' ? '+' : '-' }}{{ entries[row]?.amount.toFixed(2) }} {{ entries[row]?.currency }}
          </span>
        </template>
        <template #status="{ row }">
          <StatusBadge :variant="entryStatusVariant(entries[row]?.status || '')" :label="entries[row]?.status" />
        </template>
      </DataTableShell>
    </template>

    <!-- Invoices Tab -->
    <template v-else-if="tab === 'invoices'">
      <EmptyState v-if="invoices.length === 0" title="No invoices" description="No invoices have been generated yet." />
      <DataTableShell v-else :columns="invoiceColumns" :total="invoices.length" :striped="true" :hoverable="true">
        <template #number="{ row }">
          <span class="text-sm font-mono text-text-primary">{{ invoices[row]?.invoiceNumber }}</span>
        </template>
        <template #status="{ row }">
          <StatusBadge :variant="invoiceStatusVariant(invoices[row]?.status || '')" :label="invoices[row]?.status" />
        </template>
        <template #amount="{ row }">
          <span class="text-sm font-bold text-text-primary">{{ invoices[row]?.amount.toFixed(2) }} {{ invoices[row]?.currency }}</span>
        </template>
        <template #issued="{ row }">
          <span class="text-xs text-text-muted">{{ invoices[row]?.issuedAt }}</span>
        </template>
        <template #due="{ row }">
          <span class="text-xs text-text-muted">{{ invoices[row]?.dueAt }}</span>
        </template>
      </DataTableShell>
    </template>

    <!-- Detail Drawer -->
    <DetailDrawer :open="drawerOpen" :title="selectedEntry?.entryId || selectedInvoice?.invoiceNumber || 'Details'" @close="drawerOpen = false">
      <template v-if="selectedEntry">
        <div class="space-y-md">
          <div class="grid grid-cols-2 gap-md">
            <div>
              <div class="text-xs text-text-muted mb-xs">Type</div>
              <StatusBadge :variant="entryTypeVariant(selectedEntry.type)" :label="selectedEntry.type" size="md" />
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Status</div>
              <StatusBadge :variant="entryStatusVariant(selectedEntry.status)" :label="selectedEntry.status" size="md" />
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Amount</div>
              <div class="text-lg font-bold text-text-primary">{{ selectedEntry.amount.toFixed(2) }} {{ selectedEntry.currency }}</div>
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Date</div>
              <div class="text-sm text-text-primary">{{ selectedEntry.createdAt }}</div>
            </div>
          </div>
          <div>
            <div class="text-xs text-text-muted mb-xs">Description</div>
            <div class="text-sm text-text-primary">{{ selectedEntry.description }}</div>
          </div>
          <div v-if="selectedEntry.referenceId">
            <div class="text-xs text-text-muted mb-xs">Reference</div>
            <div class="text-xs font-mono text-text-secondary">{{ selectedEntry.referenceId }}</div>
          </div>
        </div>
      </template>
      <template v-if="selectedInvoice">
        <div class="space-y-md">
          <div class="grid grid-cols-2 gap-md">
            <div>
              <div class="text-xs text-text-muted mb-xs">Status</div>
              <StatusBadge :variant="invoiceStatusVariant(selectedInvoice.status)" :label="selectedInvoice.status" size="md" />
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Amount</div>
              <div class="text-lg font-bold text-text-primary">{{ selectedInvoice.amount.toFixed(2) }} {{ selectedInvoice.currency }}</div>
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Issued</div>
              <div class="text-sm text-text-primary">{{ selectedInvoice.issuedAt }}</div>
            </div>
            <div>
              <div class="text-xs text-text-muted mb-xs">Due</div>
              <div class="text-sm text-text-primary">{{ selectedInvoice.dueAt }}</div>
            </div>
          </div>
          <div v-if="selectedInvoice.paidAt">
            <div class="text-xs text-text-muted mb-xs">Paid</div>
            <div class="text-sm text-success-500">{{ selectedInvoice.paidAt }}</div>
          </div>
          <div v-if="selectedInvoice.lineItems.length" class="pt-sm border-t border-default">
            <div class="text-xs text-text-muted mb-sm">Line Items</div>
            <div class="space-y-sm">
              <div v-for="(li, idx) in selectedInvoice.lineItems" :key="idx" class="flex justify-between text-sm">
                <span class="text-text-secondary">{{ li.description }} x{{ li.quantity }}</span>
                <span class="text-text-primary font-medium">{{ li.total.toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </DetailDrawer>
  </div>
</template>
