<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { SubscriptionPlan, BillingLedgerEntry, Invoice } from '@/types'
import type { ActiveSubscription } from '@/api/me'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { formatApiError } from '@/utils/apiError'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const plan = ref<SubscriptionPlan | null>(null)
const activeSubscriptions = ref<ActiveSubscription[]>([])
const effectiveQuota = ref<Record<string, number>>({})
const ledgerEntries = ref<BillingLedgerEntry[]>([])
const invoices = ref<Invoice[]>([])
const page = ref(0)
const total = ref(0)

onMounted(loadBilling)

async function loadBilling() {
  loading.value = true
  error.value = null
  try {
    const [p, subs, quota, h, inv] = await Promise.allSettled([
      MeEntitlementAPI.getCurrentPlan(),
      MeEntitlementAPI.getActiveSubscriptions(),
      MeEntitlementAPI.getEffectiveQuota(),
      MeEntitlementAPI.getBillingHistory(page.value),
      MeEntitlementAPI.getInvoices(),
    ])
    if (p.status === 'fulfilled') plan.value = p.value
    if (subs.status === 'fulfilled') activeSubscriptions.value = subs.value
    if (quota.status === 'fulfilled') effectiveQuota.value = quota.value
    if (h.status === 'fulfilled') {
      ledgerEntries.value = h.value.entries
      total.value = h.value.total
    }
    if (inv.status === 'fulfilled') invoices.value = inv.value
  } catch (e: unknown) {
    error.value = formatApiError(e, 'Failed to load billing data')
  } finally {
    loading.value = false
  }
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
    <PageHeader title="Billing" subtitle="Manage your plan, billing cycle, and invoices">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadBilling">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading billing data..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadBilling" />

    <template v-else>
      <!-- Current Plan & Billing Cycle -->
      <div class="grid grid-cols-3 gap-lg">
        <div v-if="plan" class="c-card">
          <div class="c-card-header">
            <h2 class="section-title">Current Plan</h2>
            <StatusBadge :variant="plan.isActive ? 'success' : 'neutral'" :label="plan.isActive ? 'Active' : 'Inactive'" />
          </div>
          <div class="c-card-body space-y-sm">
            <div class="text-lg font-semibold text-text-primary">{{ plan.name }}</div>
            <div class="text-sm text-text-secondary">{{ plan.description }}</div>
            <div class="text-sm font-medium text-text-primary">
              ${{ plan.monthlyPrice.toFixed(2) }}/mo · ${{ plan.annualPrice.toFixed(2) }}/yr {{ plan.currency }}
            </div>
            <div v-if="'trialDays' in plan && (plan as Record<string, unknown>).trialDays > 0" class="text-xs text-info-500">{{ (plan as any).trialDays }}-day trial available</div>
          </div>
        </div>

        <div class="c-card">
          <div class="c-card-header">
            <h2 class="section-title">Billing Cycle</h2>
          </div>
          <div class="c-card-body space-y-sm">
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">Current Period</span>
              <span class="text-text-primary">Monthly</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">Next Billing</span>
              <span class="text-text-primary">—</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">Payment Method</span>
              <span class="text-text-muted">Not configured</span>
            </div>
          </div>
        </div>

        <div class="c-card">
          <div class="c-card-header">
            <h2 class="section-title">Usage Charges</h2>
          </div>
          <div class="c-card-body space-y-sm">
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">This Period</span>
              <span class="text-text-primary font-medium">$0.00</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">Last Period</span>
              <span class="text-text-primary">$0.00</span>
            </div>
            <div class="flex justify-between text-sm">
              <span class="text-text-secondary">Credits Applied</span>
              <span class="text-success-500">$0.00</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Active subscriptions (base + add-ons) -->
      <PageSection v-if="activeSubscriptions.length > 0" title="Active Subscriptions">
        <div class="space-y-sm">
          <div
            v-for="sub in activeSubscriptions"
            :key="sub.contractId"
            class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default"
          >
            <div>
              <div class="text-sm font-medium text-text-primary">{{ sub.planKey }}</div>
              <div class="text-xs text-text-muted">{{ sub.productCode }} · {{ sub.contractRole }}</div>
            </div>
            <StatusBadge :variant="sub.lifecycleState === 'ACTIVE' ? 'success' : 'neutral'" :label="sub.lifecycleState" />
          </div>
        </div>
      </PageSection>

      <PageSection v-if="Object.keys(effectiveQuota).length > 0" title="Included Quota (merged)">
        <div class="grid grid-cols-2 gap-md">
          <div v-for="(value, meter) in effectiveQuota" :key="meter" class="text-sm flex justify-between p-sm rounded bg-bg-surface border border-default">
            <span class="text-text-secondary">{{ meter }}</span>
            <span class="font-mono text-text-primary">{{ value }}</span>
          </div>
        </div>
      </PageSection>

      <!-- Usage Charges Detail -->
      <PageSection title="Usage Charges This Period">
        <div class="grid grid-cols-2 gap-lg">
          <MetricCard :value="'$0.00'" label="Render Minutes" icon="⏱" />
          <MetricCard :value="'$0.00'" label="GPU Minutes" icon="⚡" />
          <MetricCard :value="'$0.00'" label="Storage" icon="💾" />
          <MetricCard :value="'$0.00'" label="API Calls" icon="🔌" />
        </div>
      </PageSection>

      <!-- Recent Invoices -->
      <PageSection title="Recent Invoices">
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
      </PageSection>

      <!-- Ledger Entries -->
      <PageSection title="Recent Transactions">
        <EmptyState v-if="ledgerEntries.length === 0" title="No transactions" description="No billing transactions recorded." />
        <div v-else class="space-y-sm">
          <div v-for="entry in ledgerEntries" :key="entry.entryId"
            class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default">
            <div class="min-w-0 flex-1">
              <div class="text-sm text-text-primary">{{ entry.description }}</div>
              <div class="text-xs text-text-muted">{{ entry.createdAt }}</div>
            </div>
            <div class="flex items-center gap-sm">
              <StatusBadge :variant="entryTypeVariant(entry.type)" :label="entry.type" />
              <span class="text-sm font-mono"
                :class="entry.type === 'CREDIT' || entry.type === 'REFUND' ? 'text-success-500' : 'text-text-primary'">
                {{ entry.type === 'CREDIT' || entry.type === 'REFUND' ? '+' : '-' }}{{ entry.amount.toFixed(2) }} {{ entry.currency }}
              </span>
              <StatusBadge :variant="entryStatusVariant(entry.status)" :label="entry.status" />
            </div>
          </div>
        </div>
      </PageSection>
    </template>
  </div>
</template>
