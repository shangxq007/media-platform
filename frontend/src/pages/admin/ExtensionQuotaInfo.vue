<script setup lang="ts">
import { computed } from 'vue'
import type { ExtensionQuotaInfo } from '@/types'
import MetricCard from '@/components/ui/MetricCard.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import HelpTooltip from '@/components/ui/HelpTooltip.vue'

const props = defineProps<{
  quota: ExtensionQuotaInfo
  loading?: boolean
}>()

const usagePercent = computed(() => {
  if (!props.quota.executionQuota) return 0
  return Math.min(100, Math.round((props.quota.executionsUsed / props.quota.executionQuota) * 100))
})

const barColorClass = computed(() => {
  if (usagePercent.value > 80) return 'bg-danger-500'
  if (usagePercent.value > 50) return 'bg-warning-500'
  return 'bg-success-500'
})

const riskLevel = computed(() => {
  const rl = props.quota.riskLevel.toLowerCase()
  if (rl === 'low' || rl === 'medium' || rl === 'high' || rl === 'critical') return rl as 'low' | 'medium' | 'high' | 'critical'
  return 'low' as const
})
</script>

<template>
  <div class="c-card">
    <div class="c-card-header">
      <div class="flex items-center gap-sm">
        <h3 class="text-sm font-semibold text-text-primary">Execution Quota & Cost</h3>
        <HelpTooltip content="Execution quota limits and estimated costs for this extension" />
      </div>
      <RiskBadge :level="riskLevel" :label="`${quota.riskLevel} Risk`" />
    </div>
    <div class="c-card-body space-y-lg">
      <div class="grid grid-cols-4 gap-md">
        <MetricCard :value="quota.executionQuota" label="Execution Quota" icon="⚡" :loading="loading" />
        <MetricCard :value="quota.executionsUsed" label="Executions Used" icon="bar-chart-3" :loading="loading" />
        <MetricCard :value="quota.executionsRemaining" label="Remaining" icon="check" :loading="loading" />
        <MetricCard :value="`$${quota.estimatedCost.toFixed(4)}`" :label="`Est. Cost / ${quota.currency}`" icon="dollar-sign" :loading="loading" />
      </div>

      <!-- Usage bar -->
      <div>
        <div class="flex items-center justify-between text-xs mb-xs">
          <span class="text-text-secondary">Quota Usage</span>
          <span class="text-text-muted">{{ quota.executionsUsed }} / {{ quota.executionQuota }} ({{ usagePercent }}%)</span>
        </div>
        <div class="w-full bg-bg-surface rounded-full h-2">
          <div class="h-2 rounded-full transition-all" :class="barColorClass"
            :style="{ width: usagePercent + '%' }" />
        </div>
      </div>

      <!-- Permission restrictions -->
      <div v-if="quota.riskLevel === 'HIGH' || quota.riskLevel === 'CRITICAL'" class="p-sm rounded bg-warning-500/10 border border-warning-200">
        <div class="flex items-center gap-xs text-xs text-warning-600">
          <span>alert-triangle</span>
          <span>Elevated risk level — execution is rate-limited and audited</span>
        </div>
      </div>
    </div>
  </div>
</template>
