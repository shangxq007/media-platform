<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { CreditWallet, CreditTransaction } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const wallet = ref<CreditWallet | null>(null)
const transactions = ref<CreditTransaction[]>([])
const page = ref(0)
const total = ref(0)
const showTopUpDialog = ref(false)
const topUpAmount = ref('')

onMounted(loadCredits)

async function loadCredits() {
  loading.value = true
  error.value = null
  try {
    const [w, tx] = await Promise.allSettled([
      MeEntitlementAPI.getCreditBalance(),
      MeEntitlementAPI.getCreditTransactions(page.value),
    ])
    if (w.status === 'fulfilled') wallet.value = w.value
    if (tx.status === 'fulfilled') {
      transactions.value = tx.value.transactions
      total.value = tx.value.total
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load credits'
  } finally {
    loading.value = false
  }
}

function txTypeVariant(type: string): 'success' | 'danger' | 'warning' | 'neutral' {
  switch (type) {
    case 'TOP_UP': case 'REFUND': case 'RELEASE': return 'success'
    case 'DEDUCTION': return 'danger'
    case 'HOLD': return 'warning'
    default: return 'neutral'
  }
}

const isLowBalance = computed(() => {
  if (!wallet.value) return false
  return wallet.value.balance < 10
})

const earnedTx = computed(() => transactions.value.filter(t => t.type === 'TOP_UP' || t.type === 'REFUND' || t.type === 'RELEASE'))
const spentTx = computed(() => transactions.value.filter(t => t.type === 'DEDUCTION' || t.type === 'HOLD'))

const txColumns = [
  { key: 'type', label: 'Type', width: '120px' },
  { key: 'description', label: 'Description' },
  { key: 'amount', label: 'Amount', width: '100px', align: 'right' as const },
  { key: 'balance', label: 'Balance After', width: '120px', align: 'right' as const },
  { key: 'date', label: 'Date', width: '120px' },
]

async function handleTopUp() {
  const amount = parseFloat(topUpAmount.value)
  if (isNaN(amount) || amount <= 0) return
  try {
    await MeEntitlementAPI.topUpCredits(amount)
    showTopUpDialog.value = false
    topUpAmount.value = ''
    await loadCredits()
  } catch { /* handled by API layer */ }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Credits" subtitle="Manage your credit balance and view transaction history">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="showTopUpDialog = true">+ Add Credits</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadCredits">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading credits..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadCredits" />

    <template v-else>
      <!-- Low Balance Warning -->
      <div v-if="isLowBalance" class="bg-warning-500/10 border border-warning-500/30 rounded-lg p-md flex items-center justify-between">
        <div class="flex items-center gap-md">
          <span class="text-lg">⚠️</span>
          <div>
            <div class="text-sm font-medium text-warning-600">Low Balance</div>
            <div class="text-xs text-text-secondary">Your credit balance is running low. Add credits to avoid service interruption.</div>
          </div>
        </div>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="showTopUpDialog = true">Add Credits</button>
      </div>

      <!-- Balance Overview -->
      <div class="grid grid-cols-3 gap-lg">
        <MetricCard v-if="wallet" :value="`${wallet.balance.toFixed(2)} ${wallet.currency}`" label="Available Balance" icon="💰" />
        <MetricCard v-if="wallet" :value="`${wallet.heldBalance.toFixed(2)} ${wallet.currency}`" label="Held Balance" icon="🔒" />
        <MetricCard :value="`${wallet ? (wallet.balance + wallet.heldBalance).toFixed(2) : '0.00'}`" label="Total Balance" icon="💎" />
      </div>

      <!-- Credits Over Time -->
      <PageSection title="Credits Earned vs Spent">
        <div class="grid grid-cols-2 gap-lg">
          <div class="c-card">
            <div class="c-card-body">
              <div class="flex items-center gap-sm mb-sm">
                <span class="text-lg">📈</span>
                <span class="text-sm font-medium text-text-primary">Earned</span>
              </div>
              <div class="text-2xl font-bold text-success-500">
                +{{ earnedTx.reduce((sum, t) => sum + t.amount, 0).toFixed(2) }}
              </div>
              <div class="text-xs text-text-muted mt-xs">{{ earnedTx.length }} transactions</div>
            </div>
          </div>
          <div class="c-card">
            <div class="c-card-body">
              <div class="flex items-center gap-sm mb-sm">
                <span class="text-lg">📉</span>
                <span class="text-sm font-medium text-text-primary">Spent</span>
              </div>
              <div class="text-2xl font-bold text-danger-500">
                -{{ spentTx.reduce((sum, t) => sum + t.amount, 0).toFixed(2) }}
              </div>
              <div class="text-xs text-text-muted mt-xs">{{ spentTx.length }} transactions</div>
            </div>
          </div>
        </div>
      </PageSection>

      <!-- Transaction History -->
      <PageSection title="Transaction History">
        <EmptyState v-if="transactions.length === 0" title="No transactions" description="Your transaction history will appear here." />
        <DataTableShell v-else :columns="txColumns" :total="transactions.length" :striped="true" :hoverable="true" :show-pagination="false">
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
          <template #balance="{ row }">
            <span class="text-sm font-mono text-text-secondary">{{ transactions[row]?.balanceAfter.toFixed(2) }}</span>
          </template>
          <template #date="{ row }">
            <span class="text-xs text-text-muted">{{ transactions[row]?.createdAt }}</span>
          </template>
        </DataTableShell>
        <div v-if="total > transactions.length" class="text-xs text-text-muted mt-sm">
          Showing {{ transactions.length }} of {{ total }}
        </div>
      </PageSection>
    </template>

    <!-- Top Up Dialog -->
    <Teleport to="body">
      <div v-if="showTopUpDialog" class="c-dialog-overlay" @click.self="showTopUpDialog = false">
        <div class="c-dialog">
          <div class="c-dialog-header">
            <h3 class="text-lg font-semibold text-text-primary">Add Credits</h3>
          </div>
          <div class="c-dialog-body">
            <div class="c-form-group">
              <label class="c-form-label">Amount (USD)</label>
              <input v-model="topUpAmount" type="number" min="1" step="0.01" class="theme-input w-full" placeholder="Enter amount" />
              <div class="c-form-hint">Minimum $1.00</div>
            </div>
          </div>
          <div class="c-dialog-footer">
            <button class="theme-btn theme-btn-secondary" @click="showTopUpDialog = false">Cancel</button>
            <button class="theme-btn theme-btn-primary" :disabled="!topUpAmount || parseFloat(topUpAmount) <= 0" @click="handleTopUp">Add Credits</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
