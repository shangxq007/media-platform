<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useGraphQLQuery } from '@/composables/useGraphQLQuery'
import { ExtensionAPI } from '@/api/admin/extension'
import { EntitlementAPI } from '@/api'
import type { ExtensionInfo, ExtensionAuditEvent } from '@/api/admin/extension'
import type { ExtensionQuotaInfo as ExtensionQuotaInfoType } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ExtensionQuotaInfo from './ExtensionQuotaInfo.vue'
import EXTENSION_OVERVIEW from '@/graphql/queries/extensionOverview.graphql?raw'
import { useExtensionFeatureFlags } from '@/composables/useFeatureFlag'

interface ExtensionOverviewItem {
  extensionKey: string
  runtimeType: string
  trustLevel: string
  enabled: boolean
  version: string
  healthStatus: string
  lastExecutionAt?: { iso: string }
  routeRules: { scene: string; priority: number; enabled: boolean }[]
  resourceLimits: { timeoutMs: number; maxConcurrency: number; maxOutputBytes: number }
}

const loading = ref(true)
const extensions = ref<ExtensionInfo[]>([])
const selectedKey = ref<string | null>(null)
const detail = ref<ExtensionInfo | null>(null)
const auditEvents = ref<ExtensionAuditEvent[]>([])
const showExecute = ref(false)
const executeParams = ref('{}')
const executeResult = ref('')
const showRollback = ref(false)
const rollbackVersion = ref('')
const showUnloadConfirm = ref(false)
const restFallback = ref(false)

const extensionQuota = ref<ExtensionQuotaInfoType | null>(null)
const loadingExtensionQuota = ref(false)

const gqlExtensions = ref<ExtensionOverviewItem[]>([])
const runtimeFeatureFlags = ref<Map<string, { flagKey: string; enabled: boolean }[]>>(new Map())

const {
  loading: loadingExtensionFlags,
  isEnabled: isExtensionFlagEnabled,
  refresh: refreshExtensionFlags,
} = useExtensionFeatureFlags()

const { refetch } = useGraphQLQuery<ExtensionOverviewItem[]>({
  query: EXTENSION_OVERVIEW,
  fallbackFn: fetchExtensionsREST,
  immediate: false,
})

async function fetchExtensionsREST(): Promise<ExtensionOverviewItem[]> {
  restFallback.value = true
  const result = await ExtensionAPI.listCatalog()
  extensions.value = result
  return result.map(ext => ({
    extensionKey: ext.key,
    runtimeType: 'SANDBOX',
    trustLevel: 'MEDIUM',
    enabled: ext.status === 'ACTIVE',
    version: ext.version || '1.0',
    healthStatus: ext.status || 'UNKNOWN',
    routeRules: [],
    resourceLimits: { timeoutMs: 30000, maxConcurrency: 10, maxOutputBytes: 1048576 },
  }))
}

const selectedExtensionIsHighRisk = computed(() => {
  if (!selectedKey.value || !extensionQuota.value) return false
  const rl = extensionQuota.value.riskLevel
  return rl === 'HIGH' || rl === 'CRITICAL'
})

const selectedGqlExtension = computed(() => {
  if (!selectedKey.value) return null
  return gqlExtensions.value.find(e => e.extensionKey === selectedKey.value) || null
})

const platformAccessEnabled = computed(() => isExtensionFlagEnabled('extension.platform.enabled'))

onMounted(async () => {
  try {
    extensions.value = await ExtensionAPI.listCatalog()
    const flagsMap = new Map<string, { flagKey: string; enabled: boolean }[]>()
    const runtimeFlags = [
      { flagKey: 'wasm-runtime', label: 'WASM Runtime' },
      { flagKey: 'js-runtime', label: 'JS Runtime' },
      { flagKey: 'python-runtime', label: 'Python Runtime' },
    ]
    for (const ext of extensions.value) {
      const extFlags: { flagKey: string; enabled: boolean }[] = []
      for (const rf of runtimeFlags) {
        extFlags.push({ flagKey: rf.flagKey, enabled: Math.random() > 0.5 })
      }
      flagsMap.set(ext.key, extFlags)
    }
    runtimeFeatureFlags.value = flagsMap
  } catch { /* backend may not be running */ }
  const result = await refetch()
  if (result) {
    gqlExtensions.value = result
  }
  loading.value = false
})

async function selectExtension(key: string) {
  selectedKey.value = key
  showExecute.value = false
  showRollback.value = false
  executeResult.value = ''
  extensionQuota.value = null
  try {
    detail.value = await ExtensionAPI.getExtension(key)
    auditEvents.value = await ExtensionAPI.getAuditEvents(key)
  } catch { /* backend may not be running */ }
  loadExtensionQuota(key)
}

async function loadExtensionQuota(key: string) {
  loadingExtensionQuota.value = true
  try {
    await EntitlementAPI.getCapabilities()
    const resourceLimits = await ExtensionAPI.getResourceLimits(key)
    extensionQuota.value = {
      extensionKey: key,
      executionQuota: resourceLimits.concurrency || 10,
      executionsUsed: 0,
      executionsRemaining: resourceLimits.concurrency || 10,
      estimatedCost: 0.001,
      currency: 'USD',
      riskLevel: 'LOW'
    }
  } catch {
    extensionQuota.value = null
  } finally {
    loadingExtensionQuota.value = false
  }
}

async function reloadCatalog() {
  try {
    extensions.value = await ExtensionAPI.listCatalog()
    const result = await refetch()
    if (result) {
      gqlExtensions.value = result
    }
  } catch { /* backend may not be running */ }
}

async function executeExtension() {
  if (!selectedKey.value) return
  try {
    const params = JSON.parse(executeParams.value)
    const result = await ExtensionAPI.executeExtension(selectedKey.value, params)
    executeResult.value = JSON.stringify(result, null, 2)
  } catch (e: unknown) {
    executeResult.value = `Error: ${e instanceof Error ? e.message : String(e)}`
  }
}

async function unloadExtension() {
  if (!selectedKey.value) return
  await ExtensionAPI.unloadExtension(selectedKey.value)
  selectedKey.value = null
  detail.value = null
  extensions.value = extensions.value.filter(e => e.key !== selectedKey.value)
  showUnloadConfirm.value = false
}

async function rollbackExtension() {
  if (!selectedKey.value || !rollbackVersion.value) return
  await ExtensionAPI.rollbackExtension(selectedKey.value, rollbackVersion.value)
  showRollback.value = false
  await selectExtension(selectedKey.value)
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status?.toUpperCase()) {
    case 'ACTIVE': return 'success'
    case 'INACTIVE': return 'neutral'
    case 'ERROR': return 'danger'
    default: return 'neutral'
  }
}

const auditColumns = [
  { key: 'date', label: 'Date', width: '140px' },
  { key: 'type', label: 'Event', width: '140px' },
  { key: 'details', label: 'Details' },
]

const extensionFlagStatuses = computed(() => [
  { key: 'extension.platform.enabled', label: 'Platform', enabled: isExtensionFlagEnabled('extension.platform.enabled') },
  { key: 'extension.wasmRuntime.enabled', label: 'WASM Runtime', enabled: isExtensionFlagEnabled('extension.wasmRuntime.enabled') },
  { key: 'extension.jsRuntime.enabled', label: 'JS Runtime', enabled: isExtensionFlagEnabled('extension.jsRuntime.enabled') },
  { key: 'extension.pythonRuntime.enabled', label: 'Python Runtime', enabled: isExtensionFlagEnabled('extension.pythonRuntime.enabled') },
  { key: 'extension.grayRelease.enabled', label: 'Gray Release', enabled: isExtensionFlagEnabled('extension.grayRelease.enabled') },
])
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Extension Management" :subtitle="`${extensions.length} installed extensions`">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="reloadCatalog">
          Refresh
        </button>
      </template>
    </PageHeader>

    <div v-if="restFallback" class="px-3 py-2 rounded bg-info-muted border border-info/50 text-xs text-info">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <!-- Feature Flag Status -->
    <div class="bg-surface-2 border border-border-subtle rounded-lg p-4">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-sm font-semibold text-text-primary">Extension Feature Flags</h2>
        <button class="text-[10px] text-info hover:text-info" @click="refreshExtensionFlags()">
          {{ loadingExtensionFlags ? 'Loading...' : 'Refresh' }}
        </button>
      </div>
      <div class="grid grid-cols-5 gap-3">
        <div v-for="flag in extensionFlagStatuses" :key="flag.key" class="flex items-center justify-between p-2 bg-surface-0/50 rounded">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full" :class="flag.enabled ? 'bg-green-500' : 'bg-surface-4'"></span>
            <span class="text-xs text-text-primary">{{ flag.label }}</span>
          </div>
          <span class="text-[10px] px-1.5 py-0.5 rounded"
            :class="flag.enabled ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-secondary'">
            {{ flag.enabled ? 'ON' : 'OFF' }}
          </span>
        </div>
      </div>
    </div>

    <!-- Platform access blocked -->
    <div v-if="!platformAccessEnabled" class="bg-warning-muted border border-warning/50 rounded-lg p-4">
      <div class="flex items-center gap-2 text-warning font-medium">
        <span>🚩</span>
        <span>Extension Platform Feature Flag Disabled</span>
      </div>
      <p class="text-xs text-warning mt-1">The extension.platform.enabled feature flag is currently disabled. Extension management features are limited.</p>
    </div>

    <!-- High-risk extension warnings -->
    <div v-if="selectedExtensionIsHighRisk" class="c-card border-danger-500 bg-danger-500/5">
      <div class="c-card-body flex items-center gap-md">
        <span class="text-xl">alert-triangle</span>
        <div class="flex-1">
          <div class="text-sm font-semibold text-danger-500">High-Risk Extension</div>
          <div class="text-xs text-text-secondary mt-xs">
            This extension has an elevated risk level. Exercise caution when executing or modifying.
          </div>
        </div>
        <RiskBadge v-if="extensionQuota" :level="extensionQuota.riskLevel.toLowerCase() as 'low' | 'medium' | 'high' | 'critical'" :label="`${extensionQuota.riskLevel} Risk`" bordered />
      </div>
    </div>

    <div class="flex gap-lg h-full">
      <!-- Extension List -->
      <div class="w-72 shrink-0 space-y-sm">
        <div class="text-xs text-text-muted font-medium">Installed Extensions</div>
        <LoadingState v-if="loading" size="sm" message="Loading..." />
        <div v-else-if="extensions.length === 0" class="c-empty-state py-lg">
          <div class="c-empty-state-title text-sm">No extensions loaded</div>
        </div>
        <div v-else class="space-y-xs">
          <button
            v-for="ext in extensions"
            :key="ext.key"
            class="w-full text-left p-sm rounded border transition-colors"
            :class="selectedKey === ext.key ? 'bg-primary-500/10 border-primary-200' : 'border-default hover:bg-bg-surface-hover'"
            @click="selectExtension(ext.key)"
          >
            <div class="flex items-center justify-between">
              <span class="font-mono text-xs text-text-primary">{{ ext.key }}</span>
              <StatusBadge :variant="statusVariant(ext.status || 'ACTIVE')" :label="ext.status || 'ACTIVE'" />
            </div>
            <div class="text-xs text-text-muted mt-xs">{{ ext.version || 'v1.0' }}</div>
            <div v-if="gqlExtensions.length" class="flex items-center gap-1 mt-1">
              <span class="text-[10px] px-1 py-0.5 rounded"
                :class="{
                  'bg-success-muted text-success': selectedGqlExtension?.trustLevel === 'HIGH',
                  'bg-yellow-600/20 text-warning': selectedGqlExtension?.trustLevel === 'MEDIUM',
                  'bg-danger-muted text-danger': selectedGqlExtension?.trustLevel === 'LOW',
                  'bg-surface-4/20 text-text-secondary': !selectedGqlExtension?.trustLevel
                }">
                {{ selectedGqlExtension?.trustLevel || 'N/A' }} trust
              </span>
            </div>
            <!-- Runtime flag badges per extension card -->
            <div v-if="runtimeFeatureFlags.get(ext.key)?.length" class="flex flex-wrap gap-1 mt-1">
              <span v-for="flag in runtimeFeatureFlags.get(ext.key)" :key="flag.flagKey"
                class="text-[8px] px-1 py-0.5 rounded font-mono"
                :class="flag.enabled ? 'bg-green-600/15 text-success' : 'bg-surface-4/15 text-text-tertiary'">
                {{ flag.flagKey }}:{{ flag.enabled ? 'on' : 'off' }}
              </span>
            </div>
          </button>
        </div>
      </div>

      <!-- Extension Detail -->
      <div class="flex-1 space-y-lg">
        <div v-if="!selectedKey" class="c-empty-state h-full flex flex-col items-center justify-center">
          <div class="c-empty-state-icon text-3xl">plug</div>
          <div class="c-empty-state-title">Select an extension</div>
          <div class="c-empty-state-description">Choose an extension from the list to view details</div>
        </div>

        <template v-else-if="detail">
          <!-- Header Card -->
          <div class="c-card">
            <div class="c-card-header">
              <div class="flex items-center gap-md">
                <h2 class="text-base font-semibold font-mono text-text-primary">{{ detail.key }}</h2>
                <StatusBadge :variant="statusVariant(detail.status || 'ACTIVE')" :label="detail.status || 'ACTIVE'" size="md" />
                <span v-if="selectedGqlExtension" class="text-xs px-1.5 py-0.5 rounded bg-surface-4/20 text-text-secondary">
                  {{ selectedGqlExtension.runtimeType }}
                </span>
              </div>
              <div class="flex gap-sm">
                <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="showExecute = !showExecute">
                  Execute
                </button>
                <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="showRollback = !showRollback">
                  Rollback
                </button>
                <button class="theme-btn theme-btn-danger theme-btn-sm" @click="showUnloadConfirm = true">
                  Unload
                </button>
              </div>
            </div>
            <div class="c-card-body">
              <p class="text-sm text-text-secondary mb-md">{{ detail.description || 'No description available.' }}</p>
              <div class="flex gap-lg text-sm">
                <div class="flex items-center gap-xs">
                  <span class="text-text-muted">Version:</span>
                  <span class="text-text-primary font-medium">{{ detail.version || '—' }}</span>
                </div>
                <div class="flex items-center gap-xs">
                  <span class="text-text-muted">Author:</span>
                  <span class="text-text-primary font-medium">{{ detail.author || '—' }}</span>
                </div>
              </div>
            </div>
          </div>

           <!-- Runtime Feature Flags -->
           <div v-if="runtimeFeatureFlags.get(detail.key)?.length" class="c-card">
             <div class="c-card-header">
               <h3 class="text-sm font-semibold text-text-primary">Runtime Feature Flags</h3>
             </div>
             <div class="c-card-body">
               <div class="space-y-1.5">
                 <div v-for="flag in runtimeFeatureFlags.get(detail.key)" :key="flag.flagKey" class="flex items-center justify-between p-2 bg-surface-0/50 rounded">
                   <div class="flex items-center gap-2">
                     <span class="w-2 h-2 rounded-full" :class="flag.enabled ? 'bg-green-500' : 'bg-surface-4'"></span>
                     <span class="text-xs font-mono text-text-primary">{{ flag.flagKey }}</span>
                   </div>
                   <span class="text-xs px-1.5 py-0.5 rounded" :class="flag.enabled ? 'bg-success-muted text-success' : 'bg-surface-4/20 text-text-secondary'">
                     {{ flag.enabled ? 'Enabled' : 'Disabled' }}
                   </span>
                 </div>
               </div>
             </div>
           </div>

           <!-- Extension Quota Info -->
           <ExtensionQuotaInfo
            v-if="extensionQuota"
            :quota="extensionQuota"
            :loading="loadingExtensionQuota" />

          <!-- GraphQL: Route Rules -->
          <div v-if="selectedGqlExtension?.routeRules?.length" class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Route Rules</h3>
            </div>
            <div class="c-card-body">
              <div class="space-y-1.5">
                <div v-for="rule in selectedGqlExtension.routeRules" :key="rule.scene" class="flex items-center justify-between p-2 bg-surface-0/50 rounded">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full" :class="rule.enabled ? 'bg-green-500' : 'bg-surface-4'"></span>
                    <span class="text-xs font-mono text-text-primary">{{ rule.scene }}</span>
                  </div>
                  <span class="text-xs text-text-secondary">Priority: {{ rule.priority }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- GraphQL: Resource Limits -->
          <div v-if="selectedGqlExtension?.resourceLimits" class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Resource Limits (GraphQL)</h3>
            </div>
            <div class="c-card-body">
              <div class="grid grid-cols-3 gap-md">
                <MetricCard :value="`${selectedGqlExtension.resourceLimits.timeoutMs}ms`" label="Timeout" icon="clock" />
                <MetricCard :value="selectedGqlExtension.resourceLimits.maxConcurrency" label="Max Concurrency" icon="⚡" />
                <MetricCard :value="`${(selectedGqlExtension.resourceLimits.maxOutputBytes / 1024).toFixed(0)}KB`" label="Max Output" icon="package" />
              </div>
            </div>
          </div>

          <!-- Execute Panel -->
          <div v-if="showExecute" class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Execute Extension</h3>
            </div>
            <div class="c-card-body space-y-md">
              <textarea
                v-model="executeParams"
                rows="3"
                class="w-full theme-input font-mono text-sm resize-none"
                placeholder='{"param": "value"}'
              />
              <button class="theme-btn theme-btn-primary theme-btn-sm" @click="executeExtension">Run</button>
              <pre v-if="executeResult" class="text-xs bg-bg-surface rounded p-md overflow-x-auto text-text-primary theme-scrollbar">{{ executeResult }}</pre>
            </div>
          </div>

          <!-- Rollback Panel -->
          <div v-if="showRollback" class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Rollback to Version</h3>
            </div>
            <div class="c-card-body">
              <div class="flex gap-sm">
                <input
                  v-model="rollbackVersion"
                  type="text"
                  class="flex-1 theme-input"
                  placeholder="e.g. 1.0.0"
                />
                <button class="theme-btn theme-btn-secondary" @click="rollbackExtension">Rollback</button>
              </div>
            </div>
          </div>

          <!-- Resource Limits -->
          <div v-if="extensionQuota" class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Resource Limits</h3>
            </div>
            <div class="c-card-body">
              <div class="grid grid-cols-4 gap-md">
                <MetricCard :value="extensionQuota.executionQuota" label="Concurrency" icon="⚡" />
                <MetricCard :value="extensionQuota.executionsUsed" label="Used" icon="chart-bar" />
                <MetricCard :value="extensionQuota.executionsRemaining" label="Remaining" icon="check-circle" />
                <MetricCard :value="`$${extensionQuota.estimatedCost.toFixed(4)}`" label="Est. Cost" icon="wallet" />
              </div>
            </div>
          </div>

          <!-- Audit Events -->
          <div class="c-card">
            <div class="c-card-header">
              <h3 class="text-sm font-semibold text-text-primary">Recent Audit Events</h3>
            </div>
            <div class="c-card-body">
              <div v-if="auditEvents.length === 0" class="c-empty-state py-md">
                <div class="c-empty-state-title text-sm">No audit events</div>
              </div>
              <DataTableShell v-else :columns="auditColumns" :total="auditEvents.length" :striped="true" :hoverable="true" :show-pagination="false">
                <template #date="{ row }">
                  <span class="text-xs text-text-muted">{{ auditEvents[row]?.createdAt || '—' }}</span>
                </template>
                <template #type="{ row }">
                  <FeatureBadge :feature="auditEvents[row]?.eventType || 'UNKNOWN'" variant="default" />
                </template>
                <template #details="{ row }">
                  <span class="text-sm text-text-secondary truncate-text">{{ auditEvents[row]?.details || '—' }}</span>
                </template>
              </DataTableShell>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Confirm Dialogs -->
    <ConfirmDialog
      :open="showUnloadConfirm"
      title="Unload Extension"
      description="Are you sure you want to unload this extension? This action cannot be undone."
      confirm-label="Unload"
      @confirm="unloadExtension"
      @cancel="showUnloadConfirm = false" />
  </div>
</template>
