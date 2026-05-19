<script setup lang="ts">
import type { EntitlementExplanation } from '@/types'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

defineProps<{
  loading: boolean
  explanation: EntitlementExplanation | null
  featureKey: string | null
}>()

function tierVariant(tier: string): 'default' | 'premium' | 'enterprise' {
  switch (tier) {
    case 'PRO': return 'premium'
    case 'TEAM': return 'enterprise'
    case 'ENTERPRISE': return 'enterprise'
    default: return 'default'
  }
}
</script>

<template>
  <div class="c-card">
    <div class="c-card-body">
      <LoadingState v-if="loading" size="sm" message="Loading..." />
      <div v-else-if="!featureKey" class="c-empty-state py-lg">
        <div class="c-empty-state-title text-sm">Select a feature to see details</div>
      </div>
      <div v-else-if="!explanation" class="c-empty-state py-lg">
        <div class="c-empty-state-title text-sm">No explanation available</div>
      </div>
      <div v-else class="space-y-md">
        <div class="flex items-center gap-md">
          <span class="text-base font-medium text-text-primary">{{ explanation.featureName }}</span>
          <StatusBadge
            :variant="explanation.available ? 'success' : 'danger'"
            :label="explanation.available ? 'Available' : 'Unavailable'"
            size="md"
            dot />
        </div>

        <p class="text-sm text-text-secondary">{{ explanation.reason }}</p>

        <div v-if="explanation.requiredTier" class="flex items-center gap-sm text-sm">
          <span class="text-text-muted">Current:</span>
          <StatusBadge :variant="tierVariant(explanation.currentTier) === 'default' ? 'neutral' : 'info'" :label="explanation.currentTier" />
          <span class="text-text-muted">Required:</span>
          <StatusBadge variant="warning" :label="explanation.requiredTier" />
        </div>

        <div v-if="explanation.violations.length">
          <div class="text-xs text-text-muted font-medium mb-xs">Violations</div>
          <div class="space-y-xs">
            <div v-for="v in explanation.violations" :key="v" class="flex items-center gap-xs text-xs text-danger-500">
              <span>⚠</span>
              <span>{{ v }}</span>
            </div>
          </div>
        </div>

        <div v-if="explanation.upgradeOptions.length">
          <div class="text-xs text-text-muted font-medium mb-xs">Upgrade Options</div>
          <div class="flex flex-wrap gap-xs">
            <FeatureBadge
              v-for="opt in explanation.upgradeOptions"
              :key="opt.targetPlanId"
              :feature="opt.targetTier"
              :variant="tierVariant(opt.targetTier)" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
