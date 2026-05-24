<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { MyCapabilities, EntitlementExplanation } from '@/types'
import UsageSummaryPanel from './UsageSummaryPanel.vue'
import CurrentPlanPanel from './CurrentPlanPanel.vue'
import UpgradeSuggestionPanel from './UpgradeSuggestionPanel.vue'
import EntitlementExplanationPanel from './EntitlementExplanationPanel.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import HelpTooltip from '@/components/ui/HelpTooltip.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageSection from '@/components/ui/PageSection.vue'

const loading = ref(true)
const capabilities = ref<MyCapabilities | null>(null)
const error = ref<string | null>(null)
const selectedFeature = ref<string | null>(null)
const explanation = ref<EntitlementExplanation | null>(null)
const loadingExplanation = ref(false)

onMounted(loadCapabilities)

async function loadCapabilities() {
  loading.value = true
  error.value = null
  try {
    capabilities.value = await MeEntitlementAPI.getMyCapabilities()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load capabilities'
  } finally {
    loading.value = false
  }
}

async function showExplanation(featureKey: string) {
  selectedFeature.value = featureKey
  loadingExplanation.value = true
  try {
    explanation.value = await MeEntitlementAPI.getEntitlementExplanation(featureKey)
  } catch {
    explanation.value = null
  } finally {
    loadingExplanation.value = false
  }
}

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
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="My Capabilities" subtitle="Your plan, usage, and feature access">
      <template #actions>
        <StatusBadge v-if="capabilities" :variant="capabilities.tier === 'ENTERPRISE' ? 'warning' : capabilities.tier === 'FREE' ? 'neutral' : 'success'" :label="capabilities.tier" />
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadCapabilities">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading capabilities..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadCapabilities" />

    <template v-else-if="capabilities">
      <div class="grid grid-cols-3 gap-lg">
        <CurrentPlanPanel :capabilities="capabilities" />
        <UsageSummaryPanel />
        <UpgradeSuggestionPanel />
      </div>

      <!-- Key Metrics -->
      <PageSection title="Plan Overview">
        <div class="grid-metrics">
          <MetricCard :value="capabilities.tier" label="Current Tier" icon="◆" />
          <MetricCard :value="capabilities.entitlementPolicy.monthlyRenderMinutes" label="Render Minutes / mo" icon="⏱" />
          <MetricCard :value="capabilities.entitlementPolicy.maxConcurrentJobs" label="Concurrent Jobs" icon="⚡" />
          <MetricCard :value="capabilities.exportCapabilities.allowedFormats.length" label="Export Formats" icon="package" />
        </div>
      </PageSection>

      <!-- Feature Flags -->
      <PageSection title="Feature Flags" description="Features enabled for your account">
        <div class="grid grid-cols-2 gap-sm">
          <div v-for="flag in capabilities.featureFlags" :key="flag.flagKey"
            class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default">
            <div class="min-w-0">
              <div class="text-sm text-text-primary truncate">{{ flag.displayName }}</div>
              <div class="text-xs text-text-muted truncate">{{ flag.description }}</div>
            </div>
            <StatusBadge :variant="flag.enabled ? 'success' : 'neutral'" :label="flag.enabled ? 'ON' : 'OFF'" size="md" />
          </div>
        </div>
      </PageSection>

      <!-- Entitlements & Grant Sources -->
      <div class="grid grid-cols-2 gap-lg">
        <PageSection title="Entitlement Policy">
          <div class="space-y-sm text-sm">
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Max Resolution</span>
              <span class="text-text-primary font-medium">{{ capabilities.entitlementPolicy.maxResolutionWidth }}x{{ capabilities.entitlementPolicy.maxResolutionHeight }}</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Monthly Render Minutes</span>
              <span class="text-text-primary font-medium">{{ capabilities.entitlementPolicy.monthlyRenderMinutes }}</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Max Concurrent Jobs</span>
              <span class="text-text-primary font-medium">{{ capabilities.entitlementPolicy.maxConcurrentJobs }}</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary flex items-center gap-xs">GPU Allowed
                <HelpTooltip content="Hardware-accelerated rendering" />
              </span>
              <StatusBadge :variant="capabilities.entitlementPolicy.gpuAllowed ? 'success' : 'danger'" :label="capabilities.entitlementPolicy.gpuAllowed ? 'Yes' : 'No'" />
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary flex items-center gap-xs">Remote Worker
                <HelpTooltip content="Distributed rendering on remote machines" />
              </span>
              <StatusBadge :variant="capabilities.entitlementPolicy.remoteWorkerAllowed ? 'success' : 'danger'" :label="capabilities.entitlementPolicy.remoteWorkerAllowed ? 'Yes' : 'No'" />
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Custom Fonts</span>
              <StatusBadge :variant="capabilities.entitlementPolicy.customFontsAllowed ? 'success' : 'danger'" :label="capabilities.entitlementPolicy.customFontsAllowed ? 'Yes' : 'No'" />
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Watermark</span>
              <StatusBadge :variant="capabilities.entitlementPolicy.watermark ? 'warning' : 'success'" :label="capabilities.entitlementPolicy.watermark ? 'Required' : 'None'" />
            </div>
          </div>
        </PageSection>

        <PageSection title="Export Capabilities">
          <div class="space-y-sm text-sm">
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Formats</span>
              <div class="flex gap-xs">
                <FeatureBadge v-for="fmt in capabilities.exportCapabilities.allowedFormats" :key="fmt" :feature="fmt" :variant="tierVariant(capabilities.tier)" />
              </div>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Presets</span>
              <span class="text-text-primary font-medium">{{ capabilities.exportCapabilities.allowedPresets.length }} available</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Max Resolution</span>
              <span class="text-text-primary font-medium">{{ capabilities.exportCapabilities.maxResolutionWidth }}x{{ capabilities.exportCapabilities.maxResolutionHeight }}</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">GPU Export</span>
              <StatusBadge :variant="capabilities.exportCapabilities.gpuExportAllowed ? 'success' : 'danger'" :label="capabilities.exportCapabilities.gpuExportAllowed ? 'Yes' : 'No'" />
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Concurrent Exports</span>
              <span class="text-text-primary font-medium">{{ capabilities.exportCapabilities.maxConcurrentExports }}</span>
            </div>
          </div>
        </PageSection>
      </div>

      <!-- Feature Availability -->
      <PageSection title="Feature Availability" description="Click a preset to see entitlement details">
        <div class="flex flex-wrap gap-sm mb-md">
          <button v-for="preset in capabilities.exportCapabilities.allowedPresets" :key="preset"
            class="theme-btn theme-btn-sm capitalize"
            :class="selectedFeature === preset ? 'theme-btn-primary' : 'theme-btn-secondary'"
            @click="showExplanation(preset)">
            {{ preset }}
          </button>
        </div>
        <EntitlementExplanationPanel
          :loading="loadingExplanation"
          :explanation="explanation"
          :feature-key="selectedFeature" />
      </PageSection>
    </template>
  </div>
</template>
