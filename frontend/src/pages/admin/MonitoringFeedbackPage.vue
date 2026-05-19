<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGraphQLQuery } from '@/composables/useGraphQLQuery'
import MONITORING_FEEDBACK_OVERVIEW from '@/graphql/queries/monitoringFeedbackOverview.graphql?raw'
import { useMonitoringFeatureFlags } from '@/composables/useFeatureFlag'

interface MonitoringStatus {
  sentryEnabled: boolean
  openReplayEnabled: boolean
  lastErrorAt?: { iso: string }
  lastFeedbackAt?: { iso: string }
}

interface FeedbackSummary {
  openIssues: number
  criticalIssues: number
  linkedRenderJobs: number
  linkedPromptExecutions: number
  replayLinked: number
}

interface ProblematicDataSummary {
  total: number
  requireReview: number
  autoFixed: number
  critical: number
}

interface FeatureFlagEvaluationMetrics {
  totalEvaluations: number
  enabledCount: number
  disabledCount: number
  errorRate: number
  topFlags: Array<{ flagKey: string; evaluations: number; errorRate: number }>
  usageByTenant: Array<{ tenant: string; evaluations: number }>
}

interface MonitoringFeedbackData {
  monitoringStatus: MonitoringStatus
  feedbackSummary: FeedbackSummary
  problematicDataSummary: ProblematicDataSummary
  featureFlagMetrics?: FeatureFlagEvaluationMetrics
}

const range = ref('7d')
const monitoringStatus = ref<MonitoringStatus | null>(null)
const feedbackSummary = ref<FeedbackSummary | null>(null)
const problematicData = ref<ProblematicDataSummary | null>(null)
const ffMetrics = ref<FeatureFlagEvaluationMetrics | null>(null)
const restFallback = ref(false)

const {
  loading: loadingMonitoringFlags,
  isEnabled: isMonitoringFlagEnabled,
  refresh: refreshMonitoringFlags,
} = useMonitoringFeatureFlags()

const { loading, error, errorCode, refetch } = useGraphQLQuery<MonitoringFeedbackData>({
  query: MONITORING_FEEDBACK_OVERVIEW,
  variables: { range: range.value },
  fallbackFn: fetchMonitoringFeedbackREST,
  immediate: false,
})

async function fetchMonitoringFeedbackREST(): Promise<MonitoringFeedbackData> {
  restFallback.value = true
  try {
    const resp = await fetch('/api/v1/monitoring/feedback-overview?range=' + encodeURIComponent(range.value))
    if (!resp.ok) throw new Error('Failed to fetch monitoring feedback data')
    return resp.json()
  } catch {
    return {
      monitoringStatus: { sentryEnabled: true, openReplayEnabled: true },
      feedbackSummary: { openIssues: 0, criticalIssues: 0, linkedRenderJobs: 0, linkedPromptExecutions: 0, replayLinked: 0 },
      problematicDataSummary: { total: 0, requireReview: 0, autoFixed: 0, critical: 0 },
      featureFlagMetrics: { totalEvaluations: 0, enabledCount: 0, disabledCount: 0, errorRate: 0, topFlags: [], usageByTenant: [] },
    }
  }
}

onMounted(async () => {
  const data = await refetch()
  if (data?.featureFlagMetrics) {
    ffMetrics.value = data.featureFlagMetrics
  }
})

const hasCriticalIssues = computed(() => (feedbackSummary.value?.criticalIssues ?? 0) > 0)
const hasCriticalData = computed(() => (problematicData.value?.critical ?? 0) > 0)

const monitoringFlagStatuses = computed(() => [
  { key: 'graphql.queryAggregation.enabled', label: 'GraphQL Query Agg', enabled: isMonitoringFlagEnabled('graphql.queryAggregation.enabled') },
  { key: 'graphql.adminDashboard.enabled', label: 'Admin GraphQL', enabled: isMonitoringFlagEnabled('graphql.adminDashboard.enabled') },
  { key: 'monitoring.openReplay.enabled', label: 'OpenReplay', enabled: isMonitoringFlagEnabled('monitoring.openReplay.enabled') },
  { key: 'monitoring.sentryReplay.enabled', label: 'Sentry Replay', enabled: isMonitoringFlagEnabled('monitoring.sentryReplay.enabled') },
  { key: 'feedback.userReport.enabled', label: 'User Feedback', enabled: isMonitoringFlagEnabled('feedback.userReport.enabled') },
])

function formatIso(iso?: { iso: string }): string {
  if (!iso?.iso) return '—'
  try {
    return new Date(iso.iso).toLocaleString()
  } catch {
    return iso.iso
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-xl font-bold">Monitoring & Feedback</h1>
        <p class="text-sm text-gray-400 mt-1">Error tracking, session replay, and data quality</p>
      </div>
      <div class="flex items-center gap-3">
        <select v-model="range" class="bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white" @change="refetch">
          <option value="1d">Last 24h</option>
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
        </select>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="refetch">Refresh</button>
      </div>
    </div>

    <div v-if="restFallback" class="px-3 py-2 rounded bg-blue-900/20 border border-blue-700/50 text-xs text-blue-300">
      Using REST fallback — GraphQL endpoint unavailable
    </div>

    <div v-if="error" class="px-3 py-2 rounded bg-red-900/20 border border-red-700/50 text-xs text-red-300">
      <div v-if="errorCode" class="font-mono text-[10px] text-red-400 mb-1">{{ errorCode }}</div>
      {{ error.message }}
    </div>

    <!-- Feature Flag Status -->
    <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-sm font-semibold text-gray-300">🚩 Feature Flag Status</h2>
        <button class="text-[10px] text-blue-400 hover:text-blue-300" @click="refreshMonitoringFlags()">
          {{ loadingMonitoringFlags ? 'Loading...' : 'Refresh' }}
        </button>
      </div>
      <div class="grid grid-cols-5 gap-3">
        <div v-for="flag in monitoringFlagStatuses" :key="flag.key" class="flex items-center justify-between p-3 bg-gray-900/50 rounded">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full" :class="flag.enabled ? 'bg-green-500' : 'bg-gray-500'"></span>
            <span class="text-xs text-gray-300">{{ flag.label }}</span>
          </div>
          <span class="text-[10px] px-1.5 py-0.5 rounded"
            :class="flag.enabled ? 'bg-green-600/20 text-green-300' : 'bg-gray-600/20 text-gray-400'">
            {{ flag.enabled ? 'ON' : 'OFF' }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>

    <template v-else>
      <!-- Monitoring Status -->
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <h2 class="text-sm font-semibold mb-3 text-gray-300">Monitoring Status</h2>
        <div class="grid grid-cols-2 gap-4">
          <div class="flex items-center justify-between p-3 bg-gray-900/50 rounded">
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full" :class="monitoringStatus?.sentryEnabled && isMonitoringFlagEnabled('monitoring.sentryReplay.enabled') ? 'bg-green-500' : 'bg-gray-500'"></span>
              <span class="text-xs text-gray-400">Sentry</span>
            </div>
            <span class="text-xs font-medium" :class="monitoringStatus?.sentryEnabled && isMonitoringFlagEnabled('monitoring.sentryReplay.enabled') ? 'text-green-400' : 'text-gray-500'">
              {{ monitoringStatus?.sentryEnabled && isMonitoringFlagEnabled('monitoring.sentryReplay.enabled') ? 'Enabled' : 'Disabled' }}
            </span>
          </div>
          <div class="flex items-center justify-between p-3 bg-gray-900/50 rounded">
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full" :class="monitoringStatus?.openReplayEnabled && isMonitoringFlagEnabled('monitoring.openReplay.enabled') ? 'bg-green-500' : 'bg-gray-500'"></span>
              <span class="text-xs text-gray-400">OpenReplay</span>
            </div>
            <span class="text-xs font-medium" :class="monitoringStatus?.openReplayEnabled && isMonitoringFlagEnabled('monitoring.openReplay.enabled') ? 'text-green-400' : 'text-gray-500'">
              {{ monitoringStatus?.openReplayEnabled && isMonitoringFlagEnabled('monitoring.openReplay.enabled') ? 'Enabled' : 'Disabled' }}
            </span>
          </div>
          <div class="text-xs text-gray-500">
            Last error: <span class="text-gray-300">{{ formatIso(monitoringStatus?.lastErrorAt) }}</span>
          </div>
          <div class="text-xs text-gray-500">
            Last feedback: <span class="text-gray-300">{{ formatIso(monitoringStatus?.lastFeedbackAt) }}</span>
          </div>
        </div>
      </div>

      <!-- Stats Grid -->
      <div class="grid grid-cols-4 gap-4">
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Open Issues</div>
          <div class="text-2xl font-bold">{{ feedbackSummary?.openIssues ?? '—' }}</div>
        </div>
        <div class="bg-gray-800 border rounded-lg p-4" :class="hasCriticalIssues ? 'border-red-700' : 'border-gray-700'">
          <div class="text-xs text-gray-400 mb-1">Critical Issues</div>
          <div class="text-2xl font-bold" :class="hasCriticalIssues ? 'text-red-400' : ''">
            {{ feedbackSummary?.criticalIssues ?? '—' }}
          </div>
        </div>
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Linked Render Jobs</div>
          <div class="text-2xl font-bold">{{ feedbackSummary?.linkedRenderJobs ?? '—' }}</div>
        </div>
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <div class="text-xs text-gray-400 mb-1">Linked Executions</div>
          <div class="text-2xl font-bold">{{ feedbackSummary?.linkedPromptExecutions ?? '—' }}</div>
        </div>
      </div>

      <!-- Feature Flag Evaluation Metrics -->
      <div v-if="ffMetrics" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <h2 class="text-sm font-semibold mb-3 text-gray-300">🚩 Feature Flag Evaluation Metrics</h2>
        <div class="grid grid-cols-4 gap-4 mb-4">
          <div>
            <div class="text-xs text-gray-400">Total Evaluations</div>
            <div class="text-lg font-bold text-gray-200">{{ ffMetrics.totalEvaluations.toLocaleString() }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Enabled</div>
            <div class="text-lg font-bold text-green-400">{{ ffMetrics.enabledCount.toLocaleString() }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Disabled</div>
            <div class="text-lg font-bold text-gray-400">{{ ffMetrics.disabledCount.toLocaleString() }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Error Rate</div>
            <div class="text-lg font-bold" :class="ffMetrics.errorRate > 0.05 ? 'text-red-400' : 'text-green-400'">
              {{ (ffMetrics.errorRate * 100).toFixed(1) }}%
            </div>
          </div>
        </div>
        <div v-if="ffMetrics.topFlags.length > 0" class="grid grid-cols-2 gap-4">
          <div>
            <div class="text-xs text-gray-400 mb-2">Top Flags</div>
            <div class="space-y-1">
              <div v-for="flag in ffMetrics.topFlags" :key="flag.flagKey" class="flex items-center justify-between p-2 bg-gray-900/50 rounded text-xs">
                <span class="font-mono text-blue-300">{{ flag.flagKey }}</span>
                <span class="text-gray-400">{{ flag.evaluations.toLocaleString() }} evals</span>
                <span :class="flag.errorRate > 0.05 ? 'text-red-400' : 'text-green-400'">{{ (flag.errorRate * 100).toFixed(1) }}% err</span>
              </div>
            </div>
          </div>
          <div>
            <div class="text-xs text-gray-400 mb-2">Usage by Tenant</div>
            <div class="space-y-1">
              <div v-for="t in ffMetrics.usageByTenant" :key="t.tenant" class="flex items-center justify-between p-2 bg-gray-900/50 rounded text-xs">
                <span class="font-mono text-gray-300">{{ t.tenant }}</span>
                <span class="text-gray-400">{{ t.evaluations.toLocaleString() }} evals</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Problematic Data -->
       <div class="bg-gray-800 border rounded-lg p-4" :class="hasCriticalData ? 'border-red-700' : 'border-gray-700'">
        <h2 class="text-sm font-semibold mb-3 text-gray-300">Problematic Data</h2>
        <div class="grid grid-cols-4 gap-4">
          <div>
            <div class="text-xs text-gray-400">Total</div>
            <div class="text-lg font-bold text-gray-200">{{ problematicData?.total ?? '—' }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Require Review</div>
            <div class="text-lg font-bold text-yellow-400">{{ problematicData?.requireReview ?? '—' }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Auto-Fixed</div>
            <div class="text-lg font-bold text-green-400">{{ problematicData?.autoFixed ?? '—' }}</div>
          </div>
          <div>
            <div class="text-xs text-gray-400">Critical</div>
            <div class="text-lg font-bold" :class="hasCriticalData ? 'text-red-400' : ''">
              {{ problematicData?.critical ?? '—' }}
            </div>
          </div>
        </div>
      </div>

      <!-- Replay Linked -->
      <div v-if="(feedbackSummary?.replayLinked ?? 0) > 0" class="bg-gray-800 border border-gray-700 rounded-lg p-4">
        <h2 class="text-sm font-semibold mb-2 text-gray-300">Session Replay</h2>
        <p class="text-xs text-gray-400">
          {{ feedbackSummary?.replayLinked }} feedback items linked to session replays for debugging.
        </p>
      </div>
    </template>
  </div>
</template>
