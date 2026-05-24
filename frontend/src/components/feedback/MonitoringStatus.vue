<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { isSentryInitialized, getSentryReplayId } from '@/utils/sentry'
import { isOpenReplayInitialized, getOpenReplaySessionId, getOpenReplaySessionUrl } from '@/utils/openreplay'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useMonitoringFeatureFlags } from '@/composables/useFeatureFlag'

const sentryStatus = ref<'active' | 'inactive' | 'error'>('inactive')
const openReplayStatus = ref<'active' | 'inactive' | 'error'>('inactive')
const sentryReplayId = ref<string | null>(null)
const openReplaySessionId = ref<string | null>(null)
const openReplaySessionUrl = ref<string | null>(null)
const showDetails = ref(false)
const copied = ref(false)

const {
  isEnabled: isMonitoringFlagEnabled,
} = useMonitoringFeatureFlags()

onMounted(() => {
  try {
    const sentryReady = isSentryInitialized()
    const orReady = isOpenReplayInitialized()
    sentryStatus.value = (sentryReady && sentryReady.value) ? 'active' : 'inactive'
    openReplayStatus.value = (orReady && orReady.value) ? 'active' : 'inactive'
  } catch {
    sentryStatus.value = 'inactive'
    openReplayStatus.value = 'inactive'
  }
  try { sentryReplayId.value = getSentryReplayId() } catch { /* noop */ }
  try { openReplaySessionId.value = getOpenReplaySessionId() } catch { /* noop */ }
  try { openReplaySessionUrl.value = getOpenReplaySessionUrl() } catch { /* noop */ }
})

function statusVariant(status: string): 'success' | 'danger' | 'neutral' {
  switch (status) {
    case 'active': return 'success'
    case 'error': return 'danger'
    default: return 'neutral'
  }
}

const diagnosticText = computed(() => {
  const parts: string[] = []
  if (sentryReplayId.value) parts.push(`Sentry Replay: ${sentryReplayId.value}`)
  if (openReplaySessionId.value) parts.push(`OpenReplay Session: ${openReplaySessionId.value}`)
  return parts.join('\n')
})

async function copyDiagnosticInfo() {
  try {
    await navigator.clipboard.writeText(diagnosticText.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    /* clipboard unavailable */
  }
}

const monitoringFlagStatuses = computed(() => [
  { key: 'graphql.queryAggregation.enabled', label: 'GraphQL Agg', enabled: isMonitoringFlagEnabled('graphql.queryAggregation.enabled') },
  { key: 'graphql.adminDashboard.enabled', label: 'Admin GQL', enabled: isMonitoringFlagEnabled('graphql.adminDashboard.enabled') },
  { key: 'monitoring.openReplay.enabled', label: 'OpenReplay', enabled: isMonitoringFlagEnabled('monitoring.openReplay.enabled') },
  { key: 'monitoring.sentryReplay.enabled', label: 'Sentry', enabled: isMonitoringFlagEnabled('monitoring.sentryReplay.enabled') },
  { key: 'feedback.userReport.enabled', label: 'Feedback', enabled: isMonitoringFlagEnabled('feedback.userReport.enabled') },
])

const effectiveSentryActive = computed(() =>
  sentryStatus.value === 'active' && isMonitoringFlagEnabled('monitoring.sentryReplay.enabled')
)

const effectiveOpenReplayActive = computed(() =>
  openReplayStatus.value === 'active' && isMonitoringFlagEnabled('monitoring.openReplay.enabled')
)
</script>

<template>
  <div class="c-card">
    <div class="c-card-body space-y-md">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium text-text-primary">Monitoring Status</span>
        <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="showDetails = !showDetails">
          {{ showDetails ? 'Hide' : 'Show' }}
        </button>
      </div>

      <div class="flex items-center gap-lg">
        <div class="flex items-center gap-sm">
          <StatusBadge :variant="statusVariant(effectiveSentryActive ? 'active' : sentryStatus)" :label="effectiveSentryActive ? 'Active' : sentryStatus === 'error' ? 'Error' : 'Inactive'" dot />
          <span class="text-sm text-text-primary">Sentry</span>
          <span v-if="!isMonitoringFlagEnabled('monitoring.sentryReplay.enabled')" class="text-[8px] px-1 py-0 rounded bg-yellow-600/20 text-warning">FF OFF</span>
        </div>
        <div class="flex items-center gap-sm">
          <StatusBadge :variant="statusVariant(effectiveOpenReplayActive ? 'active' : openReplayStatus)" :label="effectiveOpenReplayActive ? 'Active' : openReplayStatus === 'error' ? 'Error' : 'Inactive'" dot />
          <span class="text-sm text-text-primary">OpenReplay</span>
          <span v-if="!isMonitoringFlagEnabled('monitoring.openReplay.enabled')" class="text-[8px] px-1 py-0 rounded bg-yellow-600/20 text-warning">FF OFF</span>
        </div>
      </div>

      <!-- Feature Flag States -->
      <div class="space-y-1.5 pt-2 border-t border-default">
        <div class="text-[10px] text-text-tertiary font-medium">Feature Flags</div>
        <div class="flex flex-wrap gap-2">
          <span v-for="flag in monitoringFlagStatuses" :key="flag.key"
            class="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded"
            :class="flag.enabled ? 'bg-green-600/15 text-success' : 'bg-surface-4/15 text-text-tertiary'">
            <span class="w-1.5 h-1.5 rounded-full" :class="flag.enabled ? 'bg-green-500' : 'bg-surface-4'"></span>
            {{ flag.label }}
          </span>
        </div>
      </div>

      <div v-if="showDetails" class="space-y-sm pt-sm border-t border-default">
        <div v-if="sentryReplayId" class="flex items-center justify-between text-xs">
          <span class="text-text-muted">Sentry Replay:</span>
          <code class="text-text-secondary font-mono">{{ sentryReplayId.substring(0, 16) }}...</code>
        </div>
        <div v-if="openReplaySessionId" class="flex items-center justify-between text-xs">
          <span class="text-text-muted">OR Session:</span>
          <code class="text-text-secondary font-mono">{{ openReplaySessionId.substring(0, 16) }}...</code>
        </div>

        <div class="flex gap-sm pt-xs">
          <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="copyDiagnosticInfo">
            {{ copied ? '✓ Copied!' : 'Copy Diagnostic Info' }}
          </button>
          <a v-if="openReplaySessionUrl" :href="openReplaySessionUrl" target="_blank"
            class="theme-btn theme-btn-primary theme-btn-sm">
            View Session Replay →
          </a>
        </div>
      </div>
    </div>
  </div>
</template>
