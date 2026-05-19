<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { UpgradeOption } from '@/types'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'

const loading = ref(true)
const options = ref<UpgradeOption[]>([])

onMounted(loadOptions)

async function loadOptions() {
  loading.value = true
  try {
    options.value = await MeEntitlementAPI.getUpgradeOptions()
  } catch { /* backend may not be running */ }
  loading.value = false
}

function tierVariant(tier: string): 'default' | 'premium' | 'enterprise' {
  switch (tier) {
    case 'PRO': return 'premium'
    case 'TEAM': return 'enterprise'
    case 'ENTERPRISE': return 'enterprise'
    default: return 'default'
  }
}

function tierBadgeVariant(tier: string): 'success' | 'warning' | 'info' | 'neutral' {
  switch (tier) {
    case 'PRO': return 'info'
    case 'TEAM': return 'warning'
    case 'ENTERPRISE': return 'warning'
    default: return 'neutral'
  }
}
</script>

<template>
  <div class="c-card h-full">
    <div class="c-card-header">
      <h2 class="section-title">Upgrade Options</h2>
      <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="loadOptions">Refresh</button>
    </div>
    <div class="c-card-body">
      <div v-if="loading" class="c-loading-state">
        <div class="c-spinner c-spinner-sm" />
        <p class="text-sm text-text-secondary mt-sm">Loading...</p>
      </div>
      <div v-else-if="options.length === 0" class="c-empty-state">
        <div class="c-empty-state-title text-sm">No upgrade options</div>
        <div class="c-empty-state-description">You're on the highest plan or no upgrades are available.</div>
      </div>
      <div v-else class="space-y-md">
        <div v-for="opt in options" :key="opt.targetPlanId"
          class="c-card border transition-colors"
          :class="opt.recommended ? 'border-primary-200 bg-primary-500/5' : 'border-default'">
          <div class="c-card-body">
            <div class="flex items-center justify-between mb-sm">
              <div class="flex items-center gap-sm">
                <StatusBadge :variant="tierBadgeVariant(opt.targetTier)" :label="opt.targetTier" size="md" />
                <span class="text-sm font-medium text-text-primary">{{ opt.targetPlanName }}</span>
              </div>
              <span v-if="opt.recommended" class="theme-badge bg-primary-500/10 text-primary-500 text-xs">Recommended</span>
            </div>
            <div class="text-sm text-text-secondary mb-sm">
              ${{ opt.monthlyPrice.toFixed(2) }}/mo · ${{ opt.annualPrice.toFixed(2) }}/yr {{ opt.currency }}
            </div>
            <div v-if="opt.additionalFeatures.length" class="flex flex-wrap gap-xs mb-md">
              <FeatureBadge v-for="feat in opt.additionalFeatures" :key="feat" :feature="feat" :variant="tierVariant(opt.targetTier)" />
            </div>
            <button class="theme-btn theme-btn-primary theme-btn-sm w-full">
              Upgrade to {{ opt.targetTier }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
