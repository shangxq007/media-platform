<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MeEntitlementAPI } from '@/api/me'
import type { MyCapabilities, UsageSummary, CreditWallet, Project, RenderJobDetailed } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import UpgradeHint from '@/components/ui/UpgradeHint.vue'

const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const capabilities = ref<MyCapabilities | null>(null)
const usage = ref<UsageSummary | null>(null)
const credits = ref<CreditWallet | null>(null)
const recentProjects = ref<Project[]>([])
const recentExports = ref<RenderJobDetailed[]>([])

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  error.value = null
  try {
    const [caps, use, cred] = await Promise.allSettled([
      MeEntitlementAPI.getMyCapabilities(),
      MeEntitlementAPI.getUsageSummary(),
      MeEntitlementAPI.getCreditBalance(),
    ])
    if (caps.status === 'fulfilled') capabilities.value = caps.value
    if (use.status === 'fulfilled') usage.value = use.value
    if (cred.status === 'fulfilled') credits.value = cred.value
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load dashboard'
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

const betaFeatures = computed(() => {
  if (!capabilities.value) return []
  return capabilities.value.featureFlags.filter(f => f.enabled)
})

function projectStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'active': return 'success'
    case 'archived': return 'neutral'
    case 'error': return 'danger'
    default: return 'warning'
  }
}

function exportStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'completed': return 'success'
    case 'running': case 'queued': return 'warning'
    case 'failed': case 'cancelled': return 'danger'
    default: return 'neutral'
  }
}

const quotaItems = computed(() => {
  if (!usage.value) return []
  return [
    { label: 'Render Minutes', used: usage.value.renderMinutesUsed, limit: usage.value.renderMinutesLimit },
    { label: 'Storage (GB)', used: usage.value.storageGbUsed, limit: usage.value.storageGbLimit },
    { label: 'API Calls', used: usage.value.apiCallsUsed, limit: usage.value.apiCallsLimit },
    { label: 'Exports', used: usage.value.exportsUsed, limit: usage.value.exportsLimit },
  ]
})

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Dashboard" subtitle="Overview of your workspace, usage, and credits">
      <template #actions>
        <StatusBadge v-if="capabilities" :variant="capabilities.tier === 'ENTERPRISE' ? 'warning' : capabilities.tier === 'FREE' ? 'neutral' : 'success'" :label="capabilities.tier" />
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadDashboard">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading dashboard..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadDashboard" />

    <template v-else>
      <!-- Quick Actions -->
      <PageSection title="Quick Actions">
        <div class="flex flex-wrap gap-md">
          <button class="c-card flex-1 min-w-48 text-left hover:border-primary-200 transition-colors cursor-pointer" @click="navigateTo('/project/new')">
            <div class="c-card-body flex items-center gap-md">
              <span class="text-2xl">➕</span>
              <div>
                <div class="text-sm font-medium text-text-primary">New Project</div>
                <div class="text-xs text-text-muted">Start a new editing project</div>
              </div>
            </div>
          </button>
          <button class="c-card flex-1 min-w-48 text-left hover:border-primary-200 transition-colors cursor-pointer" @click="navigateTo('/')">
            <div class="c-card-body flex items-center gap-md">
              <span class="text-2xl">📁</span>
              <div>
                <div class="text-sm font-medium text-text-primary">Upload Media</div>
                <div class="text-xs text-text-muted">Import media files to your library</div>
              </div>
            </div>
          </button>
          <button class="c-card flex-1 min-w-48 text-left hover:border-primary-200 transition-colors cursor-pointer" @click="navigateTo('/?demo=true')">
            <div class="c-card-body flex items-center gap-md">
              <span class="text-2xl">🎬</span>
              <div>
                <div class="text-sm font-medium text-text-primary">Try Demo</div>
                <div class="text-xs text-text-muted">Explore with a sample project</div>
              </div>
            </div>
          </button>
        </div>
      </PageSection>

      <!-- Workspace & Credits Overview -->
      <div class="grid grid-cols-3 gap-lg">
        <MetricCard :value="capabilities?.tier || '—'" label="Current Tier" icon="◆" />
        <MetricCard :value="credits ? `${credits.balance.toFixed(2)} ${credits.currency}` : '—'" label="Credits Balance" icon="💰" />
        <MetricCard :value="capabilities?.entitlementPolicy.monthlyRenderMinutes || 0" label="Render Minutes / mo" icon="⏱" />
      </div>

      <!-- Usage Summary -->
      <PageSection title="Usage Summary" description="Current period usage against your quota">
        <div v-if="usage" class="space-y-md">
          <div v-for="item in quotaItems" :key="item.label">
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
        <div v-else class="text-sm text-text-muted">No usage data available</div>
      </PageSection>

      <!-- Recent Projects & Exports -->
      <div class="grid grid-cols-2 gap-lg">
        <PageSection title="Recent Projects">
          <EmptyState v-if="recentProjects.length === 0" title="No projects yet" description="Create your first project to get started.">
            <template #action>
              <button class="theme-btn theme-btn-primary theme-btn-sm" @click="navigateTo('/project/new')">New Project</button>
            </template>
          </EmptyState>
          <div v-else class="space-y-sm">
            <div v-for="proj in recentProjects" :key="proj.id"
              class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default hover:border-primary-200 transition-colors cursor-pointer"
              @click="navigateTo(`/project/${proj.id}`)">
              <div class="min-w-0 flex-1">
                <div class="text-sm text-text-primary truncate-text">{{ proj.name }}</div>
                <div class="text-xs text-text-muted">{{ proj.createdAt }}</div>
              </div>
              <StatusBadge :variant="projectStatusVariant(proj.status)" :label="proj.status" />
            </div>
          </div>
        </PageSection>

        <PageSection title="Recent Exports">
          <EmptyState v-if="recentExports.length === 0" title="No exports yet" description="Export a project to see it here.">
            <template #action>
              <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="navigateTo('/')">Open Editor</button>
            </template>
          </EmptyState>
          <div v-else class="space-y-sm">
            <div v-for="exp in recentExports" :key="exp.id"
              class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default">
              <div class="min-w-0 flex-1">
                <div class="text-sm text-text-primary truncate-text">{{ exp.format }} · {{ exp.resolution }}</div>
                <div class="text-xs text-text-muted">{{ exp.createdAt }}</div>
              </div>
              <StatusBadge :variant="exportStatusVariant(exp.status)" :label="exp.status" />
            </div>
          </div>
        </PageSection>
      </div>

      <!-- Beta Features -->
      <PageSection v-if="betaFeatures.length" title="Beta Features Available">
        <div class="flex flex-wrap gap-sm">
          <FeatureBadge v-for="flag in betaFeatures" :key="flag.flagKey" :feature="flag.displayName" variant="beta" />
        </div>
      </PageSection>

      <!-- Upgrade Hint -->
      <UpgradeHint v-if="capabilities && capabilities.tier !== 'ENTERPRISE'"
        title="Unlock more with an upgrade"
        description="Get higher quotas, GPU rendering, and priority support."
        @upgrade="navigateTo('/me/capabilities')" />
    </template>
  </div>
</template>
