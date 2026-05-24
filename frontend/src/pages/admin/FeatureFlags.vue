<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { AdminFeatureFlag } from '@/api/admin/feature-flags'

type Tab = 'entitlement' | 'unleash'

const loading = ref(true)
const activeTab = ref<Tab>('entitlement')
const tenantId = ref('tenant-1')

// Entitlement feature flags
const capabilities = ref<{
  tier?: string
  featureFlags?: AdminFeatureFlag[]
  entitlementPolicy?: Record<string, unknown>
  exportCapabilities?: Record<string, unknown>
  providerAccess?: Record<string, unknown>
} | null>(null)

// Unleash / policy governance
const governanceOverview = ref<{
  module?: string
  status?: string
  description?: string
  unleashEnabled?: boolean
  policyCount?: number
} | null>(null)

const tiers = ['FREE', 'PRO', 'TEAM', 'ENTERPRISE', 'EXPERIMENTAL']
const selectedTier = ref('FREE')


onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [caps, gov] = await Promise.allSettled([
      FeatureFlagAPI.getCapabilities(tenantId.value),
      FeatureFlagAPI.getPolicyGovernanceOverview(),
    ])
    if (caps.status === 'fulfilled') capabilities.value = caps.value
    if (gov.status === 'fulfilled') governanceOverview.value = gov.value
  } catch { /* backend may not be running */ }
  loading.value = false
}

const tierFlags = computed(() => {
  if (!capabilities.value?.featureFlags) return []
  return capabilities.value.featureFlags.filter(f => f.targetTier === selectedTier.value)
})

const groupedFlags = computed(() => {
  const groups: Record<string, AdminFeatureFlag[]> = {}
  for (const f of (capabilities.value?.featureFlags || [])) {
    if (!groups[f.flagKey]) groups[f.flagKey] = []
    groups[f.flagKey].push(f)
  }
  return groups
})

const flagKeys = computed(() => Object.keys(groupedFlags.value))

function tierClass(tier: string, enabled: boolean): string {
  if (!enabled) return 'bg-surface-3/30 text-text-tertiary'
  switch (tier) {
    case 'FREE': return 'bg-success-muted text-success'
    case 'PRO': return 'bg-blue-600/20 text-info'
    case 'TEAM': return 'bg-accent-500/10 text-purple-300'
    case 'ENTERPRISE': return 'bg-yellow-600/20 text-yellow-300'
    case 'EXPERIMENTAL': return 'bg-red-600/20 text-danger'
    default: return 'bg-surface-4/20 text-text-primary'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">Feature Flags</h1>
      <div class="flex items-center gap-3">
        <input
          v-model="tenantId"
          type="text"
          class="bg-surface-2 border border-border-default rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Tenant ID"
        />
        <button class="px-3 py-1.5 bg-surface-3 hover:bg-surface-4 text-sm rounded" @click="loadData">Refresh</button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex border-b border-border-subtle mb-6">
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'entitlement' ? 'text-info border-b-2 border-blue-400' : 'text-text-secondary hover:text-white'"
        @click="activeTab = 'entitlement'"
      >
        Tier-based Flags
      </button>
      <button
        class="px-4 py-2 text-sm"
        :class="activeTab === 'unleash' ? 'text-info border-b-2 border-blue-400' : 'text-text-secondary hover:text-white'"
        @click="activeTab = 'unleash'"
      >
        OpenFeature / Unleash
      </button>
    </div>

    <div v-if="loading" class="text-text-secondary text-sm">Loading...</div>

    <!-- ===== Tier-based Flags ===== -->
    <template v-else-if="activeTab === 'entitlement'">
      <!-- Current Tier Info -->
      <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 mb-6">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-sm font-semibold text-text-primary">Current Tenant Tier</h2>
          <span class="text-xs px-2 py-1 rounded bg-blue-600/20 text-info font-mono">
            {{ capabilities?.tier || 'FREE' }}
          </span>
        </div>

        <!-- Tier selector -->
        <div class="flex gap-2 mb-4">
          <button
            v-for="tier in tiers"
            :key="tier"
            class="text-xs px-2.5 py-1 rounded border"
            :class="selectedTier === tier
              ? 'bg-blue-600/30 border-blue-500 text-info'
              : 'border-border-default text-text-secondary hover:text-white'"
            @click="selectedTier = tier"
          >
            {{ tier }}
          </button>
        </div>

        <!-- Flags matrix table -->
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-border-subtle text-xs text-text-secondary">
                <th class="text-left px-3 py-2">Feature</th>
                <th class="text-left px-3 py-2">Scope</th>
                <th v-for="tier in tiers" :key="tier" class="text-center px-2 py-2">{{ tier }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="key in flagKeys" :key="key" class="border-b border-border-subtle/50 hover:bg-surface-3/20">
                <td class="px-3 py-2">
                  <div class="text-xs font-mono text-info">{{ key }}</div>
                  <div class="text-xs text-text-tertiary mt-0.5">{{ groupedFlags[key][0]?.description }}</div>
                </td>
                <td class="px-3 py-2 text-xs text-text-secondary">{{ groupedFlags[key][0]?.scope }}</td>
                <td v-for="tier in tiers" :key="tier" class="text-center px-2 py-2">
                  <span
                    v-for="f in groupedFlags[key].filter(f => f.targetTier === tier)"
                    :key="f.targetTier"
                    class="text-xs px-1.5 py-0.5 rounded"
                    :class="tierClass(tier, f.enabled)"
                  >
                    {{ f.enabled ? 'ON' : 'OFF' }}
                  </span>
                  <span
                    v-if="!groupedFlags[key].some(f => f.targetTier === tier)"
                    class="text-xs text-text-tertiary"
                  >—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Tier detail -->
      <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
        <h3 class="text-sm font-semibold text-text-primary mb-3">{{ selectedTier }} Tier — Enabled Features</h3>
        <div v-if="tierFlags.length === 0" class="text-xs text-text-tertiary">No flags for this tier</div>
        <div v-else class="grid grid-cols-2 gap-2">
          <div
            v-for="f in tierFlags"
            :key="f.flagKey"
            class="flex items-center gap-2 p-2 rounded border"
            :class="f.enabled ? 'border-green-700/30 bg-green-900/10' : 'border-border-subtle bg-surface-2/50'"
          >
            <span
              class="w-2 h-2 rounded-full shrink-0"
              :class="f.enabled ? 'bg-green-400' : 'bg-surface-4'"
            />
            <div class="flex-1 min-w-0">
              <div class="text-xs text-white">{{ f.displayName }}</div>
              <div class="text-xs text-text-tertiary font-mono">{{ f.flagKey }}</div>
            </div>
            <span
              class="text-xs px-1.5 py-0.5 rounded shrink-0"
              :class="f.enabled ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-tertiary'"
            >
              {{ f.enabled ? 'ON' : 'OFF' }}
            </span>
          </div>
        </div>
      </div>

      <!-- Export Capabilities & Provider Access -->
      <div class="grid grid-cols-2 gap-6 mt-6">
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h3 class="text-sm font-semibold text-text-primary mb-3">Export Capabilities ({{ capabilities?.tier || 'FREE' }})</h3>
          <pre v-if="capabilities?.exportCapabilities" class="text-xs text-text-secondary bg-surface-0 rounded p-3 overflow-x-auto max-h-64">{{ JSON.stringify(capabilities.exportCapabilities, null, 2) }}</pre>
          <div v-else class="text-xs text-text-tertiary">No data</div>
        </div>
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h3 class="text-sm font-semibold text-text-primary mb-3">Provider Access ({{ capabilities?.tier || 'FREE' }})</h3>
          <pre v-if="capabilities?.providerAccess" class="text-xs text-text-secondary bg-surface-0 rounded p-3 overflow-x-auto max-h-64">{{ JSON.stringify(capabilities.providerAccess, null, 2) }}</pre>
          <div v-else class="text-xs text-text-tertiary">No data</div>
        </div>
      </div>
    </template>

    <!-- ===== OpenFeature / Unleash ===== -->
    <template v-else>
      <div class="grid grid-cols-2 gap-6">
        <!-- Provider Status -->
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold text-text-primary mb-3">Provider Status</h2>
          <div v-if="!governanceOverview" class="text-xs text-text-tertiary">No data</div>
          <div v-else class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs text-text-secondary">Module</span>
              <span class="text-xs font-mono">{{ governanceOverview.module }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-xs text-text-secondary">Status</span>
              <span class="text-xs px-1.5 py-0.5 rounded bg-success-muted text-success">{{ governanceOverview.status }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-xs text-text-secondary">Unleash Enabled</span>
              <span
                class="text-xs px-1.5 py-0.5 rounded"
                :class="governanceOverview.unleashEnabled ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-secondary'"
              >
                {{ governanceOverview.unleashEnabled ? 'YES' : 'NO (InMemory)' }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-xs text-text-secondary">Policies</span>
              <span class="text-xs font-mono">{{ governanceOverview.policyCount || 0 }}</span>
            </div>
            <p class="text-xs text-text-tertiary mt-2 pt-2 border-t border-border-subtle">{{ governanceOverview.description }}</p>
          </div>
        </div>

        <!-- Configuration -->
        <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
          <h2 class="text-sm font-semibold text-text-primary mb-3">Configuration</h2>
          <div class="space-y-3 text-xs">
            <div class="p-3 bg-surface-0 rounded font-mono text-text-secondary">
              <div class="text-text-tertiary mb-1"># application.yml</div>
              <div>app.features.unleash.enabled: <span :class="governanceOverview?.unleashEnabled ? 'text-success' : 'text-warning'">{{ governanceOverview?.unleashEnabled || false }}</span></div>
              <div>app.features.unleash.api-url: http://localhost:4242/api/</div>
              <div>app.features.unleash.app-name: media-platform</div>
              <div>app.features.unleash.instance-id: singleton</div>
            </div>
            <div class="text-text-tertiary">
              <p v-if="!governanceOverview?.unleashEnabled">
                ⚠️ Unleash is disabled. Using InMemoryProvider — flags return default values only.
                Enable by setting <code class="bg-surface-3 px-1 rounded">app.features.unleash.enabled: true</code>
              </p>
              <p v-else>
                ✅ Unleash is connected. Feature flags are managed dynamically via Unleash dashboard.
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Dynamic Flags Info -->
      <div class="bg-surface-2 border border-border-subtle rounded-lg p-4 mt-6">
        <h2 class="text-sm font-semibold text-text-primary mb-3">Dynamic Flag Usage</h2>
        <div class="text-xs text-text-secondary space-y-2">
          <p>OpenFeature flags are evaluated at runtime in the backend. Current usage:</p>
          <div class="bg-surface-0 rounded p-3 font-mono text-text-primary">
            <div class="text-text-tertiary">// RenderActivitiesImpl.java — Pipeline routing</div>
            <div>featureFlagEvaluator.isEnabled(</div>
            <div class="pl-4">"render-pipeline-v2",</div>
            <div class="pl-4">tenantId,</div>
            <div class="pl-4">Map.of("tenantId", tenantId, "renderJobId", jobId),</div>
            <div class="pl-4">false</div>
            <div>)</div>
          </div>
          <p class="text-text-tertiary mt-2">
            To manage dynamic flags, configure them in your Unleash dashboard at
            <code class="bg-surface-3 px-1 rounded">http://localhost:4242</code>
          </p>
        </div>
      </div>
    </template>
  </div>
</template>
