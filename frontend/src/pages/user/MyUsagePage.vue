<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { UsageSummary } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'

type Period = 'current' | 'last' | 'custom'

const loading = ref(true)
const error = ref<string | null>(null)
const usage = ref<UsageSummary | null>(null)
const selectedPeriod = ref<Period>('current')

onMounted(loadUsage)

async function loadUsage() {
  loading.value = true
  error.value = null
  try {
    usage.value = await MeEntitlementAPI.getUsageSummary()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load usage'
  } finally {
    loading.value = false
  }
}

function percent(used: number, limit: number): number {
  if (!limit || limit === 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function barClass(pct: number): string {
  if (pct > 80) return 'bg-danger-500'
  if (pct > 50) return 'bg-warning-500'
  return 'bg-success-500'
}

function statusVariant(pct: number): 'success' | 'warning' | 'danger' {
  if (pct > 80) return 'danger'
  if (pct > 50) return 'warning'
  return 'success'
}

interface QuotaItem {
  label: string
  icon: string
  used: number
  limit: number
  unit: string
}

const quotaItems = computed<QuotaItem[]>(() => {
  if (!usage.value) return []
  return [
    { label: 'Render Minutes', icon: '⏱', used: usage.value.renderMinutesUsed, limit: usage.value.renderMinutesLimit, unit: 'min' },
    { label: 'GPU Minutes', icon: '⚡', used: 0, limit: usage.value.renderMinutesLimit, unit: 'min' },
    { label: 'Storage', icon: '💾', used: usage.value.storageGbUsed, limit: usage.value.storageGbLimit, unit: 'GB' },
    { label: 'API Calls', icon: '🔌', used: usage.value.apiCallsUsed, limit: usage.value.apiCallsLimit, unit: 'calls' },
    { label: 'Exports', icon: '📤', used: usage.value.exportsUsed, limit: usage.value.exportsLimit, unit: 'exports' },
    { label: 'Prompt Executions', icon: '🤖', used: 0, limit: 1000, unit: 'runs' },
    { label: 'Extension Executions', icon: '🔧', used: 0, limit: 500, unit: 'runs' },
  ]
})

const periods: { key: Period; label: string }[] = [
  { key: 'current', label: 'Current Month' },
  { key: 'last', label: 'Last Month' },
  { key: 'custom', label: 'Custom Range' },
]
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Usage" subtitle="Track your resource consumption against quotas">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadUsage">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading usage data..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadUsage" />

    <template v-else-if="usage">
      <!-- Period Selector -->
      <div class="flex gap-xs border-b border-default">
        <button v-for="p in periods" :key="p.key"
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="selectedPeriod === p.key ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="selectedPeriod = p.key">
          {{ p.label }}
        </button>
      </div>

      <!-- Summary Cards -->
      <div class="grid-metrics">
        <MetricCard :value="usage.renderMinutesUsed" :label="`of ${usage.renderMinutesLimit} minutes`" icon="⏱" />
        <MetricCard :value="usage.storageGbUsed" :label="`of ${usage.storageGbLimit} GB`" icon="💾" />
        <MetricCard :value="usage.apiCallsUsed" :label="`of ${usage.apiCallsLimit} calls`" icon="🔌" />
        <MetricCard :value="usage.exportsUsed" :label="`of ${usage.exportsLimit} exports`" icon="📤" />
      </div>

      <!-- Quota Details -->
      <PageSection title="Quota Usage">
        <div class="space-y-lg">
          <div v-for="item in quotaItems" :key="item.label" class="c-card">
            <div class="c-card-body">
              <div class="flex items-center justify-between mb-sm">
                <div class="flex items-center gap-sm">
                  <span class="text-lg">{{ item.icon }}</span>
                  <span class="text-sm font-medium text-text-primary">{{ item.label }}</span>
                </div>
                <StatusBadge
                  :variant="statusVariant(percent(item.used, item.limit))"
                  :label="`${percent(item.used, item.limit)}%`" />
              </div>
              <div class="w-full bg-bg-surface rounded-full h-3 mb-sm">
                <div class="h-3 rounded-full transition-all" :class="barClass(percent(item.used, item.limit))"
                  :style="{ width: percent(item.used, item.limit) + '%' }" />
              </div>
              <div class="flex items-center justify-between text-xs text-text-muted">
                <span>{{ item.used }} {{ item.unit }} used</span>
                <span>{{ item.limit }} {{ item.unit }} limit</span>
              </div>
            </div>
          </div>
        </div>
      </PageSection>

      <!-- Period Info -->
      <div class="flex items-center justify-between text-xs text-text-muted pt-sm border-t border-default">
        <span>Period: {{ usage.period }}</span>
        <span>Last updated: {{ usage.lastUpdatedAt || '—' }}</span>
      </div>
    </template>
  </div>
</template>
