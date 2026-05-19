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
      <h1 class="text-xl font-bold">Admin Dashboard</h1>
      <div class="flex items-center gap-3">
        <select v-model="range" class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white" @change="refetch">
          <option value="1d">Last 24h</option>
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
        </select>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="refetch">Refresh</button>
      </div>
    </div>

    <div v-if="restFallback" class="mb-4 px-3 py-2 rounded bg-blue-900/20 border border-blue-700/50 text-xs text-blue-300">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <div v-if="error" class="mb-4 px-3 py-2 rounded bg-red-900/20 border border-red-700/50 text-xs text-red-300">
      <div v-if="errorCode" class="font-mono text-[10px] text-red-400 mb-1">{{ errorCode }}</div>
      {{ error.message }}
    </div>

    <div v-if="loading && gqlLoading" class="text-gray-400 text-sm">Loading...</div>

    <template v-else>
      <!-- Stats Grid -->
      <div class="grid grid-cols-4 gap-4 mb-8">
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Tenants</div>
          <div class="text-2xl font-bold">{{ accessOverview?.tenants ?? '—' }}</div>
        </div>
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Users</div>
          <div class="text-2xl font-bold">{{ accessOverview?.users ?? '—' }}</div>
        </div>
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Extensions</div>
          <div class="text-2xl font-bold">{{ extensions.length }}</div>
        </div>
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Outbox Failed</div>
          <div class="text-2xl font-bold" :class="(outboxOverview?.failed ?? 0) > 0 ? 'text-red-400' : ''">
            {{ outboxOverview?.failed ?? '—' }}
          </div>
        </div>
      </div>

      <!-- Feature Flag & Policy Summary -->
      <div class="grid grid-cols-2 gap-4 mb-8">
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-sm font-semibold text-gray-300">🚩 Feature Flags</h2>
            <router-link to="/admin/feature-flags" class="text-xs text-blue-400 hover:text-blue-300">Manage →</router-link>
          </div>
          <div v-if="!featureFlagSummary" class="text-xs text-gray-500">Loading...</div>
          <div v-else class="grid grid-cols-3 gap-3">
            <div>
              <div class="text-xs text-gray-400">Total</div>
              <div class="text-lg font-bold text-gray-200">{{ featureFlagSummary.total }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Active</div>
              <div class="text-lg font-bold text-green-400">{{ featureFlagSummary.active }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Beta</div>
              <div class="text-lg font-bold text-purple-400">{{ featureFlagSummary.beta }}</div>
            </div>
          </div>
          <div v-if="featureFlagSummary?.recentChanges?.length" class="mt-3 pt-3 border-t border-gray-700">
            <div class="text-xs text-gray-400 mb-1">Recent changes</div>
            <div v-for="(change, idx) in featureFlagSummary.recentChanges.slice(0, 3)" :key="idx" class="flex items-center gap-2 text-[10px] text-gray-400">
              <span class="font-mono text-blue-300">{{ change.flagKey }}</span>
              <span>{{ change.change }}</span>
            </div>
          </div>
        </div>

        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-sm font-semibold text-gray-300">📜 Policies</h2>
            <router-link to="/admin/feature-flags" class="text-xs text-blue-400 hover:text-blue-300">Manage →</router-link>
          </div>
          <div v-if="!policySummary" class="text-xs text-gray-500">Loading...</div>
          <div v-else class="grid grid-cols-2 gap-3">
            <div>
              <div class="text-xs text-gray-400">Total</div>
              <div class="text-lg font-bold text-gray-200">{{ policySummary.total }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Active</div>
              <div class="text-lg font-bold text-green-400">{{ policySummary.active }}</div>
            </div>
          </div>
          <div v-if="policySummary?.recentChanges?.length" class="mt-3 pt-3 border-t border-gray-700">
            <div class="text-xs text-gray-400 mb-1">Recent changes</div>
            <div v-for="(change, idx) in policySummary.recentChanges.slice(0, 3)" :key="idx" class="flex items-center gap-2 text-[10px] text-gray-400">
              <span class="font-mono text-blue-300">{{ change.policyCode }}</span>
              <span>{{ change.change }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Links -->
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 mb-8">
        <h2 class="text-sm font-semibold text-gray-300 mb-3">Quick Links</h2>
        <div class="flex flex-wrap gap-2">
          <router-link to="/admin/feature-flags" class="text-xs px-3 py-1.5 rounded bg-blue-600/20 text-blue-300 hover:bg-blue-600/30 border border-blue-700/30 transition-colors">
            🚩 Feature Flag Management
          </router-link>
          <router-link to="/admin/routes" class="text-xs px-3 py-1.5 rounded bg-green-600/20 text-green-300 hover:bg-green-600/30 border border-green-700/30 transition-colors">
            🔀 Route Management
          </router-link>
          <router-link to="/admin/audit" class="text-xs px-3 py-1.5 rounded bg-purple-600/20 text-purple-300 hover:bg-purple-600/30 border border-purple-700/30 transition-colors">
            📋 Audit & Outbox
          </router-link>
          <router-link to="/admin/monitoring" class="text-xs px-3 py-1.5 rounded bg-yellow-600/20 text-yellow-300 hover:bg-yellow-600/30 border border-yellow-700/30 transition-colors">
            📊 Monitoring
          </router-link>
          <router-link to="/admin/extensions" class="text-xs px-3 py-1.5 rounded bg-orange-600/20 text-orange-300 hover:bg-orange-600/30 border border-orange-700/30 transition-colors">
            🔌 Extensions
          </router-link>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-6">
         <!-- Render Stats (GraphQL) -->
        <div v-if="gqlData" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Render Stats (GraphQL)</h2>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="text-xs text-gray-400">Submitted</div>
              <div class="text-lg font-bold text-gray-200">{{ gqlData.renderStats.submitted }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Completed</div>
              <div class="text-lg font-bold text-green-400">{{ gqlData.renderStats.completed }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Failed</div>
              <div class="text-lg font-bold text-red-400">{{ gqlData.renderStats.failed }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Avg Duration</div>
              <div class="text-lg font-bold text-gray-200">{{ gqlData.renderStats.avgDurationSeconds }}s</div>
            </div>
          </div>
        </div>

        <!-- Render Jobs (REST) -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Render Jobs</h2>
          <div v-if="renderJobs.length === 0" class="text-xs text-gray-500">No jobs</div>
          <div v-else class="space-y-2">
            <div v-for="(count, status) in jobStatusCounts" :key="status" class="flex items-center justify-between">
              <span class="text-xs text-gray-400">{{ status }}</span>
              <span class="text-sm font-mono">{{ count }}</span>
            </div>
          </div>
        </div>

        <!-- Provider Health (GraphQL) -->
        <div v-if="gqlData?.providerHealth?.length" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Provider Health</h2>
          <div class="space-y-1.5">
            <div v-for="provider in gqlData.providerHealth" :key="provider.providerKey" class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full" :class="provider.status === 'HEALTHY' ? 'bg-green-500' : provider.status === 'DEGRADED' ? 'bg-yellow-500' : 'bg-red-500'"></span>
                <span class="text-xs font-mono text-gray-300">{{ provider.providerKey }}</span>
              </div>
              <div class="flex items-center gap-3 text-xs text-gray-400">
                <span>{{ provider.latencyMs }}ms</span>
                <span :class="provider.errorRate > 0.05 ? 'text-red-400' : ''">{{ (provider.errorRate * 100).toFixed(1) }}% err</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Extensions -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Extensions</h2>
          <div v-if="extensions.length === 0" class="text-xs text-gray-500">No extensions loaded</div>
          <div v-else class="space-y-1.5">
            <div v-for="ext in extensions" :key="ext.key" class="flex items-center justify-between">
              <span class="text-xs font-mono text-gray-300">{{ ext.key }}</span>
              <span
                class="text-xs px-1.5 py-0.5 rounded"
                :class="ext.status === 'ACTIVE' ? 'bg-green-600/20 text-green-300' : 'bg-gray-600/20 text-gray-400'"
              >
                {{ ext.status || 'loaded' }}
              </span>
            </div>
          </div>
        </div>

        <!-- Billing Summary (GraphQL) -->
        <div v-if="gqlData" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Billing Summary</h2>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <div class="text-xs text-gray-400">Usage</div>
              <div class="text-lg font-bold text-gray-200">
                ${{ gqlData.billingSummary.usageAmount.amount.toFixed(2) }}
              </div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Revenue</div>
              <div class="text-lg font-bold text-green-400">
                ${{ gqlData.billingSummary.estimatedRevenue.amount.toFixed(2) }}
              </div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Credits</div>
              <div class="text-lg font-bold text-blue-400">
                ${{ gqlData.billingSummary.creditBalanceTotal.amount.toFixed(2) }}
              </div>
            </div>
          </div>
        </div>

        <!-- Feedback Summary (GraphQL) -->
        <div v-if="gqlData" class="bg-gray-800 border rounded-lg p-4" :class="hasCriticalFeedback ? 'border-red-700' : 'border-gray-700'">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Feedback Summary</h2>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="text-xs text-gray-400">Open Issues</div>
              <div class="text-lg font-bold text-gray-200">{{ gqlData.feedbackSummary.openIssues }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Critical</div>
              <div class="text-lg font-bold" :class="hasCriticalFeedback ? 'text-red-400' : ''">
                {{ gqlData.feedbackSummary.criticalIssues }}
              </div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Linked Renders</div>
              <div class="text-lg font-bold text-gray-200">{{ gqlData.feedbackSummary.linkedRenderJobs }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Linked Prompts</div>
              <div class="text-lg font-bold text-gray-200">{{ gqlData.feedbackSummary.linkedPromptExecutions }}</div>
            </div>
          </div>
        </div>

        <!-- Outbox -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 col-span-2">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Outbox</h2>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <div class="text-xs text-gray-400">Pending</div>
              <div class="text-lg font-bold text-yellow-400">{{ outboxOverview?.pending ?? '—' }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Processed</div>
              <div class="text-lg font-bold text-green-400">{{ outboxOverview?.processed ?? '—' }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-400">Failed</div>
              <div class="text-lg font-bold" :class="(outboxOverview?.failed ?? 0) > 0 ? 'text-red-400' : ''">
                {{ outboxOverview?.failed ?? '—' }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
