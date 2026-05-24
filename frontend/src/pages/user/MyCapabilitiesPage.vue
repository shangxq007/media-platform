<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { MyCapabilities, UsageSummary, CreditWallet, SubscriptionPlan } from '@/types'
import type { ActiveSubscription } from '@/api/me'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import HelpTooltip from '@/components/ui/HelpTooltip.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import UpgradeHint from '@/components/ui/UpgradeHint.vue'
import CurrentPlanPanel from '@/pages/entitlement/CurrentPlanPanel.vue'
import UsageSummaryPanel from '@/pages/entitlement/UsageSummaryPanel.vue'
import UpgradeSuggestionPanel from '@/pages/entitlement/UpgradeSuggestionPanel.vue'
import { formatApiError } from '@/utils/apiError'

const loading = ref(true)
const capabilities = ref<MyCapabilities | null>(null)
const usage = ref<UsageSummary | null>(null)
const credits = ref<CreditWallet | null>(null)
const plan = ref<SubscriptionPlan | null>(null)
const activeSubscriptions = ref<ActiveSubscription[]>([])
const effectiveQuota = ref<Record<string, number>>({})
const error = ref<string | null>(null)

onMounted(loadCapabilities)

async function loadCapabilities() {
  loading.value = true
  error.value = null
  try {
    const [caps, use, cred, p, subs, quota] = await Promise.allSettled([
      MeEntitlementAPI.getMyCapabilities(),
      MeEntitlementAPI.getUsageSummary(),
      MeEntitlementAPI.getCreditBalance(),
      MeEntitlementAPI.getCurrentPlan(),
      MeEntitlementAPI.getActiveSubscriptions(),
      MeEntitlementAPI.getEffectiveQuota(),
    ])
    if (caps.status === 'fulfilled') capabilities.value = caps.value
    if (use.status === 'fulfilled') usage.value = use.value
    if (cred.status === 'fulfilled') credits.value = cred.value
    if (p.status === 'fulfilled') plan.value = p.value
    if (subs.status === 'fulfilled') activeSubscriptions.value = subs.value
    if (quota.status === 'fulfilled') effectiveQuota.value = quota.value
  } catch (e: unknown) {
    error.value = formatApiError(e, 'Failed to load capabilities')
  } finally {
    loading.value = false
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

function percent(used: number, limit: number): number {
  if (!limit || limit === 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Capabilities" subtitle="Your plan, feature access, and entitlement details">
      <template #actions>
        <StatusBadge v-if="capabilities" :variant="capabilities.tier === 'ENTERPRISE' ? 'warning' : capabilities.tier === 'FREE' ? 'neutral' : 'success'" :label="capabilities.tier" />
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadCapabilities">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading capabilities..." />
    <ErrorState v-else-if="error" title="Unable to load capabilities" :description="error" @retry="loadCapabilities" />

    <template v-else-if="capabilities">
      <!-- Plan / Usage / Upgrade panels -->
      <div class="grid grid-cols-3 gap-lg">
        <CurrentPlanPanel :capabilities="capabilities" />
        <UsageSummaryPanel />
        <UpgradeSuggestionPanel />
      </div>

      <!-- Plan Overview -->
      <PageSection title="Plan Overview">
        <div class="grid-metrics">
          <MetricCard :value="capabilities.tier" label="Current Tier" icon="◆" />
          <MetricCard :value="capabilities.entitlementPolicy.monthlyRenderMinutes" label="Render Minutes / mo" icon="⏱" />
          <MetricCard :value="capabilities.entitlementPolicy.maxConcurrentJobs" label="Concurrent Jobs" icon="⚡" />
          <MetricCard :value="capabilities.exportCapabilities.allowedFormats.length" label="Export Formats" icon="package" />
          <MetricCard v-if="credits" :value="`${credits.balance.toFixed(2)}`" label="Credits" icon="dollar-sign" />
          <MetricCard v-if="plan" :value="`$${plan.monthlyPrice.toFixed(2)}/mo`" label="Plan Price" icon="credit-card" />
        </div>
      </PageSection>

      <PageSection v-if="activeSubscriptions.length > 0" title="Active Subscriptions">
        <div class="flex flex-wrap gap-sm">
          <FeatureBadge
            v-for="sub in activeSubscriptions"
            :key="sub.contractId"
            :label="`${sub.planKey} (${sub.contractRole})`"
            :enabled="sub.lifecycleState === 'ACTIVE'"
          />
        </div>
      </PageSection>

      <PageSection v-if="Object.keys(effectiveQuota).length > 0" title="Merged Included Quota">
        <div class="grid grid-cols-2 gap-sm">
          <div v-for="(value, meter) in effectiveQuota" :key="meter" class="text-sm flex justify-between p-sm rounded bg-bg-surface border border-default">
            <span class="text-text-secondary">{{ meter }}</span>
            <span class="font-mono">{{ value }}</span>
          </div>
        </div>
      </PageSection>

      <PageSection v-if="activeSubscriptions.length > 0" title="Active Subscriptions">
        <div class="flex flex-wrap gap-sm">
          <span
            v-for="sub in activeSubscriptions"
            :key="sub.contractId"
            class="text-xs px-sm py-xs rounded border border-default bg-bg-surface"
          >
            {{ sub.planKey }}
            <span class="text-text-muted">({{ sub.contractRole }})</span>
          </span>
        </div>
      </PageSection>

      <PageSection v-if="Object.keys(effectiveQuota).length > 0" title="Merged Included Quota">
        <div class="grid grid-cols-2 gap-sm">
          <div
            v-for="(value, meter) in effectiveQuota"
            :key="meter"
            class="text-xs flex justify-between p-sm rounded bg-bg-surface border border-default"
          >
            <span class="text-text-secondary">{{ meter }}</span>
            <span class="font-mono text-text-primary">{{ value }}</span>
          </div>
        </div>
      </PageSection>

      <!-- Usage vs Quota -->
      <PageSection title="Quota Usage" description="Current period usage against your plan limits">
        <div class="space-y-md">
          <div v-for="item in [
            { label: 'Render Minutes', used: usage?.renderMinutesUsed || 0, limit: capabilities.entitlementPolicy.monthlyRenderMinutes, unit: 'min' },
            { label: 'Storage', used: usage?.storageGbUsed || 0, limit: 10, unit: 'GB' },
            { label: 'API Calls', used: usage?.apiCallsUsed || 0, limit: 10000, unit: 'calls' },
            { label: 'Exports', used: usage?.exportsUsed || 0, limit: capabilities.exportCapabilities.maxConcurrentExports * 10, unit: 'exports' },
          ]" :key="item.label">
            <div class="flex items-center justify-between text-xs mb-xs">
              <span class="text-text-secondary font-medium">{{ item.label }}</span>
              <span class="text-text-muted">{{ item.used }} / {{ item.limit }} {{ item.unit }} ({{ percent(item.used, item.limit) }}%)</span>
            </div>
            <div class="w-full bg-bg-surface rounded-full h-2">
              <div class="h-2 rounded-full transition-all"
                :class="percent(item.used, item.limit) > 80 ? 'bg-danger-500' : percent(item.used, item.limit) > 50 ? 'bg-warning-500' : 'bg-success-500'"
                :style="{ width: percent(item.used, item.limit) + '%' }" />
            </div>
          </div>
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

      <div class="grid grid-cols-2 gap-lg">
        <!-- Entitlement Policy -->
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

        <!-- Export Capabilities -->
        <PageSection title="Export Capabilities">
          <div class="space-y-sm text-sm">
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Formats</span>
              <div class="flex gap-xs flex-wrap">
                <FeatureBadge v-for="fmt in capabilities.exportCapabilities.allowedFormats" :key="fmt" :feature="fmt" :variant="tierVariant(capabilities.tier)" />
              </div>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-text-secondary">Presets</span>
              <div class="flex gap-xs flex-wrap">
                <FeatureBadge v-for="preset in capabilities.exportCapabilities.allowedPresets" :key="preset" :feature="preset" :variant="tierVariant(capabilities.tier)" />
              </div>
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

      <!-- Provider Access -->
      <PageSection title="Provider Access" description="Rendering providers available for your tier">
        <div class="flex flex-wrap gap-sm">
          <FeatureBadge v-for="provider in capabilities.providerAccess.allowedProviders" :key="provider" :feature="provider" :variant="tierVariant(capabilities.tier)" />
        </div>
      </PageSection>

      <!-- Billing Status -->
      <PageSection title="Billing Status" description="Your current subscription and billing information">
        <div class="grid grid-cols-3 gap-lg">
          <div v-if="plan" class="c-card">
            <div class="c-card-body">
              <div class="text-xs text-text-muted mb-xs">Current Plan</div>
              <div class="text-lg font-semibold text-text-primary">{{ plan.name }}</div>
              <div class="text-sm text-text-secondary">${{ plan.monthlyPrice.toFixed(2) }}/mo · ${{ plan.annualPrice.toFixed(2) }}/yr</div>
              <StatusBadge :variant="plan.isActive ? 'success' : 'neutral'" :label="plan.isActive ? 'Active' : 'Inactive'" class="mt-sm" />
            </div>
          </div>
          <div v-if="credits" class="c-card">
            <div class="c-card-body">
              <div class="text-xs text-text-muted mb-xs">Credit Balance</div>
              <div class="text-lg font-semibold text-text-primary">{{ credits.balance.toFixed(2) }} {{ credits.currency }}</div>
              <div class="text-sm text-text-secondary">Held: {{ credits.heldBalance.toFixed(2) }}</div>
            </div>
          </div>
          <div class="c-card">
            <div class="c-card-body">
              <div class="text-xs text-text-muted mb-xs">Usage This Period</div>
              <div class="text-lg font-semibold text-text-primary">{{ usage?.renderMinutesUsed || 0 }} min</div>
              <div class="text-sm text-text-secondary">of {{ capabilities.entitlementPolicy.monthlyRenderMinutes }} min limit</div>
            </div>
          </div>
        </div>
      </PageSection>

      <UpgradeHint v-if="capabilities.tier !== 'ENTERPRISE'"
        title="Need more capabilities?"
        description="Upgrade your plan to unlock higher quotas and advanced features."
        @upgrade="() => {}" />
    </template>
  </div>
</template>
