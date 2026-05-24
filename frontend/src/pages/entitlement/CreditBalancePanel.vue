<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { CreditWallet, CreditTransaction } from '@/types'
import MetricCard from '@/components/ui/MetricCard.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const loading = ref(true)
const wallet = ref<CreditWallet | null>(null)
const transactions = ref<CreditTransaction[]>([])
const page = ref(0)
const total = ref(0)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [w, tx] = await Promise.allSettled([
      MeEntitlementAPI.getCreditBalance(),
      MeEntitlementAPI.getCreditTransactions(page.value)
    ])
    if (w.status === 'fulfilled') wallet.value = w.value
    if (tx.status === 'fulfilled') {
      transactions.value = tx.value.transactions
      total.value = tx.value.total
    }
  } catch { /* backend may not be running */ }
  loading.value = false
}

function txTypeVariant(type: string): 'success' | 'danger' | 'warning' | 'neutral' {
  switch (type) {
    case 'TOP_UP': case 'REFUND': case 'RELEASE': return 'success'
    case 'DEDUCTION': return 'danger'
    case 'HOLD': return 'warning'
    default: return 'neutral'
  }
}

const txColumns = [
  { key: 'type', label: 'Type', width: '110px' },
  { key: 'description', label: 'Description' },
  { key: 'amount', label: 'Amount', width: '100px', align: 'right' as const },
  { key: 'date', label: 'Date', width: '120px' },
]
</script>

<template>
  <div class="c-card">
    <div class="c-card-header">
      <h2 class="section-title">Credit Wallet</h2>
      <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="loadData">Refresh</button>
    </div>
    <div class="c-card-body">
      <div v-if="loading" class="c-loading-state">
        <div class="c-spinner c-spinner-sm" />
        <p class="text-sm text-text-secondary mt-sm">Loading...</p>
      </div>
      <div v-else-if="!wallet" class="c-empty-state">
        <div class="c-empty-state-title text-sm">No wallet data</div>
      </div>
      <div v-else class="space-y-lg">
        <div class="grid grid-cols-2 gap-md">
          <MetricCard :value="`${wallet.balance.toFixed(2)} ${wallet.currency}`" label="Available Balance" icon="dollar-sign" />
          <MetricCard :value="`${wallet.heldBalance.toFixed(2)} ${wallet.currency}`" label="Held Balance" icon="lock" />
        </div>

        <div v-if="transactions.length">
          <div class="text-xs text-text-muted font-medium mb-sm">Recent Transactions</div>
          <DataTableShell :columns="txColumns" :total="transactions.length" :striped="true" :hoverable="true" :show-pagination="false">
            <template #type="{ row }">
              <StatusBadge :variant="txTypeVariant(transactions[row]?.type || '')" :label="transactions[row]?.type" />
            </template>
            <template #description="{ row }">
              <span class="text-sm text-text-primary truncate-text">{{ transactions[row]?.description }}</span>
            </template>
            <template #amount="{ row }">
              <span class="text-sm font-mono"
                :class="transactions[row]?.type === 'TOP_UP' || transactions[row]?.type === 'REFUND' || transactions[row]?.type === 'RELEASE' ? 'text-success-500' : 'text-danger-500'">
                {{ transactions[row]?.type === 'TOP_UP' || transactions[row]?.type === 'REFUND' || transactions[row]?.type === 'RELEASE' ? '+' : '-' }}{{ transactions[row]?.amount.toFixed(2) }}
              </span>
            </template>
            <template #date="{ row }">
              <span class="text-xs text-text-muted">{{ transactions[row]?.createdAt }}</span>
            </template>
          </DataTableShell>
          <div v-if="total > transactions.length" class="text-xs text-text-muted mt-sm">
            Showing {{ transactions.length }} of {{ total }}
          </div>
        </div>
        <div v-else class="text-sm text-text-muted">No transactions</div>
      </div>
    </div>
  </div>
</template>
