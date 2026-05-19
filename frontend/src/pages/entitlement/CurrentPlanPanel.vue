<script setup lang="ts">
import { computed } from 'vue'
import type { MyCapabilities } from '@/types'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import UpgradeHint from '@/components/ui/UpgradeHint.vue'

const props = defineProps<{
  capabilities: MyCapabilities
}>()

const policy = computed(() => props.capabilities.entitlementPolicy)
const exportCaps = computed(() => props.capabilities.exportCapabilities)

const highestTier = 'ENTERPRISE'
const isHighestTier = computed(() => props.capabilities.tier === highestTier)

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

const features = computed(() => {
  const list: { label: string; enabled: boolean }[] = [
    { label: 'GPU Rendering', enabled: policy.value.gpuAllowed },
    { label: 'Remote Worker', enabled: policy.value.remoteWorkerAllowed },
    { label: 'Custom Fonts', enabled: policy.value.customFontsAllowed },
    { label: 'Watermark-Free', enabled: !policy.value.watermark },
  ]
  return list
})
</script>

<template>
  <div class="c-card h-full">
    <div class="c-card-header">
      <h2 class="section-title">Current Plan</h2>
      <StatusBadge :variant="tierBadgeVariant(capabilities.tier)" :label="capabilities.tier" size="md" />
    </div>
    <div class="c-card-body space-y-lg">
      <!-- Plan features -->
      <div class="space-y-sm">
        <div class="flex justify-between items-center text-sm">
          <span class="text-text-secondary">Render Minutes</span>
          <span class="text-text-primary font-medium">{{ policy.monthlyRenderMinutes }}/mo</span>
        </div>
        <div class="flex justify-between items-center text-sm">
          <span class="text-text-secondary">Max Resolution</span>
          <span class="text-text-primary font-medium">{{ policy.maxResolutionWidth }}x{{ policy.maxResolutionHeight }}</span>
        </div>
        <div class="flex justify-between items-center text-sm">
          <span class="text-text-secondary">Concurrent Jobs</span>
          <span class="text-text-primary font-medium">{{ policy.maxConcurrentJobs }}</span>
        </div>
        <div class="flex justify-between items-center text-sm">
          <span class="text-text-secondary">Subtitle Tracks</span>
          <span class="text-text-primary font-medium">{{ policy.maxSubtitleTracks }}</span>
        </div>
        <div class="flex justify-between items-center text-sm">
          <span class="text-text-secondary">Watermark</span>
          <StatusBadge :variant="policy.watermark ? 'warning' : 'success'" :label="policy.watermark ? 'Required' : 'None'" />
        </div>
      </div>

      <!-- Export formats -->
      <div>
        <div class="text-xs text-text-muted font-medium mb-sm">Export Formats</div>
        <div class="flex flex-wrap gap-xs">
          <FeatureBadge v-for="fmt in exportCaps.allowedFormats" :key="fmt" :feature="fmt" :variant="tierVariant(capabilities.tier)" />
        </div>
      </div>

      <!-- Feature toggles -->
      <div>
        <div class="text-xs text-text-muted font-medium mb-sm">Included Features</div>
        <div class="space-y-xs">
          <div v-for="feat in features" :key="feat.label" class="flex items-center gap-xs text-sm">
            <span :class="feat.enabled ? 'text-success-500' : 'text-text-muted'">{{ feat.enabled ? '✓' : '✗' }}</span>
            <span :class="feat.enabled ? 'text-text-primary' : 'text-text-muted'">{{ feat.label }}</span>
          </div>
        </div>
      </div>

      <!-- Effect Packs -->
      <div v-if="policy.effectPacksAllowed.length">
        <div class="text-xs text-text-muted font-medium mb-sm">Effect Packs</div>
        <div class="flex flex-wrap gap-xs">
          <FeatureBadge v-for="pack in policy.effectPacksAllowed" :key="pack" :feature="pack" variant="beta" />
        </div>
      </div>

      <!-- Upgrade hint -->
      <UpgradeHint v-if="!isHighestTier"
        title="Unlock more features"
        description="Upgrade your plan for higher quotas, GPU rendering, and more."
        @upgrade="() => {}" />
    </div>
  </div>
</template>
