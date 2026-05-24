<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { UsageSummary } from '@/types'
import MetricCard from '@/components/ui/MetricCard.vue'

const loading = ref(true)
const usage = ref<UsageSummary | null>(null)

onMounted(loadUsage)

async function loadUsage() {
  loading.value = true
  try {
    usage.value = await MeEntitlementAPI.getUsageSummary()
  } catch { /* backend may not be running */ }
  loading.value = false
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

interface QuotaItem {
  label: string
  used: number
  limit: number
}

function quotaItems(): QuotaItem[] {
  if (!usage.value) return []
  return [
    { label: 'Render Minutes', used: usage.value.renderMinutesUsed, limit: usage.value.renderMinutesLimit },
    { label: 'Storage (GB)', used: usage.value.storageGbUsed, limit: usage.value.storageGbLimit },
    { label: 'API Calls', used: usage.value.apiCallsUsed, limit: usage.value.apiCallsLimit },
    { label: 'Exports', used: usage.value.exportsUsed, limit: usage.value.exportsLimit },
  ]
}
</script>

<template>
  <div class="c-card">
    <div class="c-card-header">
      <h2 class="section-title">Usage Summary</h2>
      <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="loadUsage">Refresh</button>
    </div>
    <div class="c-card-body">
      <div v-if="loading" class="c-loading-state">
        <div class="c-spinner c-spinner-sm" />
        <p class="text-sm text-text-secondary mt-sm">Loading...</p>
      </div>
      <div v-else-if="!usage" class="c-empty-state">
        <div class="c-empty-state-title text-sm">No usage data</div>
      </div>
      <div v-else class="space-y-lg">
        <div class="grid grid-cols-2 gap-md">
          <MetricCard
            :value="usage.renderMinutesUsed"
            :label="`of ${usage.renderMinutesLimit} minutes`"
            icon="⏱" />
          <MetricCard
            :value="usage.storageGbUsed"
            :label="`of ${usage.storageGbLimit} GB`"
            icon="hard-drive" />
        </div>

        <div class="space-y-md">
          <div v-for="item in quotaItems()" :key="item.label">
            <div class="flex items-center justify-between text-xs mb-xs">
              <span class="text-text-secondary font-medium">{{ item.label }}</span>
              <span class="text-text-muted">{{ item.used }} / {{ item.limit }} ({{ percent(item.used, item.limit) }}%)</span>
            </div>
            <div class="w-full bg-bg-surface rounded-full h-2">
              <div class="h-2 rounded-full transition-all" :class="barClass(percent(item.used, item.limit))"
                :style="{ width: percent(item.used, item.limit) + '%' }" />
            </div>
          </div>
        </div>

        <div class="text-xs text-text-muted flex gap-md">
          <span>Period: {{ usage.period }}</span>
          <span>Updated: {{ usage.lastUpdatedAt || '—' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
