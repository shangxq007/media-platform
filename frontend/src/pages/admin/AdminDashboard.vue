<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useGraphQLQuery } from '@/composables/useGraphQLQuery'
import { IdentityAPI } from '@/api/admin/identity'
import { RenderAdminAPI } from '@/api/admin/render'
import { ExtensionAPI } from '@/api/admin/extension'
import { AuditAPI } from '@/api/admin/audit'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import { PolicyAdminAPI } from '@/api/admin/policy-admin'
import ADMIN_DASHBOARD from '@/graphql/queries/adminDashboard.graphql?raw'

interface RenderStats {
  submitted: number
  completed: number
  failed: number
  avgDurationSeconds: number
}

interface ProviderHealth {
  providerKey: string
  status: string
  latencyMs: number
  errorRate: number
}

interface BillingSummary {
  usageAmount: { amount: number; currency: string }
  estimatedRevenue: { amount: number; currency: string }
  creditBalanceTotal: { amount: number; currency: string }
}

interface FeedbackSummary {
  openIssues: number
  criticalIssues: number
  linkedRenderJobs: number
  linkedPromptExecutions: number
  replayLinked: number
}

interface ExtensionSummary {
  installed: number
  enabled: number
  highRisk: number
  sandboxJobsRunning: number
}

interface FeatureFlagSummary {
  total: number
  active: number
  beta: number
  recentChanges: Array<{ flagKey: string; change: string; timestamp: string }>
}

interface PolicySummary {
  total: number
  active: number
  recentChanges: Array<{ policyCode: string; change: string; timestamp: string }>
}

interface AdminDashboardData {
  renderStats: RenderStats
  providerHealth: ProviderHealth[]
  billingSummary: BillingSummary
  feedbackSummary: FeedbackSummary
  extensionSummary: ExtensionSummary
}

const loading = ref(true)
const gqlData = ref<AdminDashboardData | null>(null)
const accessOverview = ref<{ tenants: number; users: number; serviceAccounts: number } | null>(null)
const renderJobs = ref<{ status: string }[]>([])
const extensions = ref<{ key: string; status?: string }[]>([])
const outboxOverview = ref<{ pending: number; failed: number; processed: number } | null>(null)
const restFallback = ref(false)
const featureFlagSummary = ref<FeatureFlagSummary | null>(null)
const policySummary = ref<PolicySummary | null>(null)

const range = ref('7d')

const { loading: gqlLoading, error, errorCode, refetch } = useGraphQLQuery<AdminDashboardData>({
    query: ADMIN_DASHBOARD,
    variables: { range: range.value },
    fallbackFn: fetchAdminDashboardREST,
    immediate: false,
  })

async function fetchAdminDashboardREST(): Promise<AdminDashboardData> {
  restFallback.value = true
  const [access, jobs, exts, outbox, ffSummary, polSummary] = await Promise.allSettled([
    IdentityAPI.getAccessOverview(),
    RenderAdminAPI.listJobs(),
    ExtensionAPI.listCatalog(),
    AuditAPI.getOutboxOverview(),
    FeatureFlagAPI.getFeatureFlagSummary(),
    PolicyAdminAPI.getPolicySummary(),
  ])
  if (ffSummary.status === 'fulfilled') featureFlagSummary.value = ffSummary.value
  if (polSummary.status === 'fulfilled') policySummary.value = polSummary.value
  const jobsResult = jobs.status === 'fulfilled' ? jobs.value as { status: string }[] : []
  const extsResult = exts.status === 'fulfilled' ? exts.value as { key: string; status?: string }[] : []
  const outboxResult = outbox.status === 'fulfilled' ? outbox.value as { pending: number; failed: number; processed: number } : { pending: 0, failed: 0, processed: 0 }

  accessOverview.value = access.status === 'fulfilled' ? access.value : null
  renderJobs.value = jobsResult
  extensions.value = extsResult
  outboxOverview.value = outboxResult

  return {
    renderStats: {
      submitted: jobsResult.length,
      completed: jobsResult.filter(j => j.status === 'COMPLETED').length,
      failed: jobsResult.filter(j => j.status === 'FAILED').length,
      avgDurationSeconds: 0,
    },
    providerHealth: [],
    billingSummary: {
      usageAmount: { amount: 0, currency: 'USD' },
      estimatedRevenue: { amount: 0, currency: 'USD' },
      creditBalanceTotal: { amount: 0, currency: 'USD' },
    },
    feedbackSummary: {
      openIssues: 0,
      criticalIssues: 0,
      linkedRenderJobs: 0,
      linkedPromptExecutions: 0,
      replayLinked: 0,
    },
    extensionSummary: {
      installed: extsResult.length,
      enabled: extsResult.filter(e => e.status === 'ACTIVE').length,
      highRisk: 0,
      sandboxJobsRunning: 0,
    },
  }
}

onMounted(async () => {
  const result = await refetch()
  if (result) {
    gqlData.value = result
  }
  loading.value = false
})

const jobStatusCounts = computed(() => {
  const counts: Record<string, number> = {}
  for (const j of renderJobs.value) {
    counts[j.status || 'UNKNOWN'] = (counts[j.status || 'UNKNOWN'] || 0) + 1
  }
  return counts
})

const hasCriticalFeedback = computed(() => (gqlData.value?.feedbackSummary.criticalIssues ?? 0) > 0)
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-h2 font-semibold text-text-primary">Admin Dashboard</h1>
      <div class="flex items-center gap-3">
        <select v-model="range" class="theme-input text-xs h-8" @change="refetch">
          <option value="1d">Last 24h</option>
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
        </select>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="refetch">Refresh</button>
      </div>
    </div>

    <div v-if="restFallback" class="mb-4 px-3 py-2 rounded-lg bg-info-muted border border-info/30 text-xs text-info">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <div v-if="error" class="mb-4 px-3 py-2 rounded-lg bg-danger-muted border border-danger/30 text-xs text-danger">
      <div v-if="errorCode" class="font-mono text-micro mb-1 opacity-80">{{ errorCode }}</div>
      {{ error.message }}
    </div>

    <div v-if="loading && gqlLoading" class="text-text-tertiary text-sm">Loading...</div>

    <template v-else>
      <!-- Stats Grid -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div class="c-metric-card">
          <div class="c-metric-label">Tenants</div>
          <div class="c-metric-value">{{ accessOverview?.tenants ?? '—' }}</div>
        </div>
        <div class="c-metric-card">
          <div class="c-metric-label">Users</div>
          <div class="c-metric-value">{{ accessOverview?.users ?? '—' }}</div>
        </div>
        <div class="c-metric-card">
          <div class="c-metric-label">Extensions</div>
          <div class="c-metric-value">{{ extensions.length }}</div>
        </div>
        <div class="c-metric-card">
          <div class="c-metric-label">Outbox Failed</div>
          <div class="c-metric-value" :class="(outboxOverview?.failed ?? 0) > 0 ? 'text-danger' : ''">
            {{ outboxOverview?.failed ?? '—' }}
          </div>
        </div>
      </div>

      <!-- Feature Flag & Policy Summary -->
      <div class="grid grid-cols-2 gap-4 mb-8">
        <div class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Feature Flags</h2>
            <router-link to="/admin/feature-flags" class="text-caption text-accent-400 hover:text-accent-300">Manage →</router-link>
          </div>
          <div class="c-card-body">
            <div v-if="!featureFlagSummary" class="text-caption text-text-tertiary">Loading...</div>
            <div v-else class="grid grid-cols-3 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Total</div>
                <div class="text-h3 text-text-primary">{{ featureFlagSummary.total }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Active</div>
                <div class="text-h3 text-success">{{ featureFlagSummary.active }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Beta</div>
                <div class="text-h3 text-accent-400">{{ featureFlagSummary.beta }}</div>
              </div>
            </div>
            <div v-if="featureFlagSummary?.recentChanges?.length" class="mt-4 pt-4 border-t border-border-subtle">
              <div class="text-caption text-text-tertiary mb-2">Recent changes</div>
              <div v-for="(change, idx) in featureFlagSummary.recentChanges.slice(0, 3)" :key="idx" class="flex items-center gap-2 text-micro text-text-tertiary">
                <span class="font-mono text-accent-300">{{ change.flagKey }}</span>
                <span>{{ change.change }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Policies</h2>
            <router-link to="/admin/policies" class="text-caption text-accent-400 hover:text-accent-300">Manage →</router-link>
          </div>
          <div class="c-card-body">
            <div v-if="!policySummary" class="text-caption text-text-tertiary">Loading...</div>
            <div v-else class="grid grid-cols-2 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Total</div>
                <div class="text-h3 text-text-primary">{{ policySummary.total }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Active</div>
                <div class="text-h3 text-success">{{ policySummary.active }}</div>
              </div>
            </div>
            <div v-if="policySummary?.recentChanges?.length" class="mt-4 pt-4 border-t border-border-subtle">
              <div class="text-caption text-text-tertiary mb-2">Recent changes</div>
              <div v-for="(change, idx) in policySummary.recentChanges.slice(0, 3)" :key="idx" class="flex items-center gap-2 text-micro text-text-tertiary">
                <span class="font-mono text-accent-300">{{ change.policyCode }}</span>
                <span>{{ change.change }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Links -->
      <div class="c-card mb-8">
        <div class="c-card-body">
          <h2 class="text-body font-semibold text-text-primary mb-3">Quick Links</h2>
          <div class="flex flex-wrap gap-2">
            <router-link to="/admin/feature-flags" class="theme-btn theme-btn-ghost theme-btn-sm border border-border-subtle">
              Feature Flags
            </router-link>
            <router-link to="/admin/routes" class="theme-btn theme-btn-ghost theme-btn-sm border border-border-subtle">
              Routes
            </router-link>
            <router-link to="/admin/audit" class="theme-btn theme-btn-ghost theme-btn-sm border border-border-subtle">
              Audit & Outbox
            </router-link>
            <router-link to="/admin/monitoring" class="theme-btn theme-btn-ghost theme-btn-sm border border-border-subtle">
              Monitoring
            </router-link>
            <router-link to="/admin/extensions" class="theme-btn theme-btn-ghost theme-btn-sm border border-border-subtle">
              Extensions
            </router-link>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-6">
         <!-- Render Stats (GraphQL) -->
        <div v-if="gqlData" class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Render Stats</h2>
          </div>
          <div class="c-card-body">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Submitted</div>
                <div class="text-h3 text-text-primary">{{ gqlData.renderStats.submitted }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Completed</div>
                <div class="text-h3 text-success">{{ gqlData.renderStats.completed }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Failed</div>
                <div class="text-h3 text-danger">{{ gqlData.renderStats.failed }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Avg Duration</div>
                <div class="text-h3 text-text-primary">{{ gqlData.renderStats.avgDurationSeconds }}s</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Render Jobs (REST) -->
        <div class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Render Jobs</h2>
          </div>
          <div class="c-card-body">
            <div v-if="renderJobs.length === 0" class="text-caption text-text-tertiary">No jobs</div>
            <div v-else class="space-y-3">
              <div v-for="(count, status) in jobStatusCounts" :key="status" class="flex items-center justify-between">
                <span class="text-caption text-text-secondary">{{ status }}</span>
                <span class="text-body font-mono text-text-primary">{{ count }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Provider Health (GraphQL) -->
        <div v-if="gqlData?.providerHealth?.length" class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Provider Health</h2>
          </div>
          <div class="c-card-body">
            <div class="space-y-2">
              <div v-for="provider in gqlData.providerHealth" :key="provider.providerKey" class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="w-2 h-2 rounded-full" :class="provider.status === 'HEALTHY' ? 'bg-success' : provider.status === 'DEGRADED' ? 'bg-warning' : 'bg-danger'"></span>
                  <span class="text-caption font-mono text-text-primary">{{ provider.providerKey }}</span>
                </div>
                <div class="flex items-center gap-3 text-caption text-text-tertiary">
                  <span>{{ provider.latencyMs }}ms</span>
                  <span :class="provider.errorRate > 0.05 ? 'text-danger' : ''">{{ (provider.errorRate * 100).toFixed(1) }}% err</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Extensions -->
        <div class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Extensions</h2>
          </div>
          <div class="c-card-body">
            <div v-if="extensions.length === 0" class="text-caption text-text-tertiary">No extensions loaded</div>
            <div v-else class="space-y-2">
              <div v-for="ext in extensions" :key="ext.key" class="flex items-center justify-between">
                <span class="text-caption font-mono text-text-primary">{{ ext.key }}</span>
                <span class="theme-badge" :class="ext.status === 'ACTIVE' ? 'bg-success-muted text-success' : 'bg-surface-4 text-text-tertiary'">
                  {{ ext.status || 'loaded' }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Billing Summary (GraphQL) -->
        <div v-if="gqlData" class="c-card">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Billing Summary</h2>
          </div>
          <div class="c-card-body">
            <div class="grid grid-cols-3 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Usage</div>
                <div class="text-h3 text-text-primary">${{ gqlData.billingSummary.usageAmount.amount.toFixed(2) }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Revenue</div>
                <div class="text-h3 text-success">${{ gqlData.billingSummary.estimatedRevenue.amount.toFixed(2) }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Credits</div>
                <div class="text-h3 text-accent-400">${{ gqlData.billingSummary.creditBalanceTotal.amount.toFixed(2) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Feedback Summary (GraphQL) -->
        <div v-if="gqlData" class="c-card" :class="hasCriticalFeedback ? 'border-danger/30' : ''">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Feedback Summary</h2>
          </div>
          <div class="c-card-body">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Open Issues</div>
                <div class="text-h3 text-text-primary">{{ gqlData.feedbackSummary.openIssues }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Critical</div>
                <div class="text-h3" :class="hasCriticalFeedback ? 'text-danger' : 'text-text-primary'">
                  {{ gqlData.feedbackSummary.criticalIssues }}
                </div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Linked Renders</div>
                <div class="text-h3 text-text-primary">{{ gqlData.feedbackSummary.linkedRenderJobs }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Linked Prompts</div>
                <div class="text-h3 text-text-primary">{{ gqlData.feedbackSummary.linkedPromptExecutions }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Outbox -->
        <div class="c-card md:col-span-2">
          <div class="c-card-header">
            <h2 class="text-body font-semibold text-text-primary">Outbox</h2>
          </div>
          <div class="c-card-body">
            <div class="grid grid-cols-3 gap-4">
              <div>
                <div class="text-caption text-text-tertiary">Pending</div>
                <div class="text-h3 text-warning">{{ outboxOverview?.pending ?? '—' }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Processed</div>
                <div class="text-h3 text-success">{{ outboxOverview?.processed ?? '—' }}</div>
              </div>
              <div>
                <div class="text-caption text-text-tertiary">Failed</div>
                <div class="text-h3" :class="(outboxOverview?.failed ?? 0) > 0 ? 'text-danger' : 'text-text-primary'">
                  {{ outboxOverview?.failed ?? '—' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
