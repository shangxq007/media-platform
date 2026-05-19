<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

interface AuditLogEntry {
  queryId: string
  userId: string
  tenantId: string
  action: string
  status: string
  rowCount: number
  durationMs: number
  riskLevel: string
  createdAt: string
}

const loading = ref(true)
const error = ref<string | null>(null)
const auditLogs = ref<AuditLogEntry[]>([])

onMounted(loadAuditLogs)

async function loadAuditLogs() {
  loading.value = true
  error.value = null
  try {
    // In a real implementation, this would call a backend audit log API
    // For now, showing placeholder data structure
    auditLogs.value = []
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load audit logs'
  } finally {
    loading.value = false
  }
}

function riskVariant(level: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (level) {
    case 'LOW': return 'success'
    case 'MEDIUM': return 'warning'
    case 'HIGH': case 'CRITICAL': return 'danger'
    default: return 'neutral'
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status?.toLowerCase()) {
    case 'success': case 'completed': return 'success'
    case 'failed': case 'error': return 'danger'
    case 'pending': case 'running': return 'warning'
    default: return 'neutral'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Query Audit Logs" subtitle="View audit trail for all NLQ operations">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadAuditLogs">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading audit logs..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadAuditLogs" />

    <template v-else>
      <PageSection title="Audit Entries">
        <EmptyState v-if="auditLogs.length === 0" title="No audit entries" description="Audit logs will appear here as users interact with the NLQ system." />
        <div v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-default">
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Timestamp</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">User</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Action</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Status</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Risk</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Rows</th>
                <th class="text-left px-sm py-xs text-text-muted font-medium text-xs">Duration</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in auditLogs" :key="log.queryId" class="border-b border-default hover:bg-bg-surface">
                <td class="px-sm py-xs text-text-secondary text-xs">{{ new Date(log.createdAt).toLocaleString() }}</td>
                <td class="px-sm py-xs text-text-secondary text-xs font-mono">{{ log.userId }}</td>
                <td class="px-sm py-xs text-text-secondary text-xs">{{ log.action }}</td>
                <td class="px-sm py-xs"><StatusBadge :variant="statusVariant(log.status)" :label="log.status" /></td>
                <td class="px-sm py-xs"><StatusBadge :variant="riskVariant(log.riskLevel)" :label="log.riskLevel" /></td>
                <td class="px-sm py-xs text-text-secondary text-xs">{{ log.rowCount }}</td>
                <td class="px-sm py-xs text-text-secondary text-xs">{{ log.durationMs }}ms</td>
              </tr>
            </tbody>
          </table>
        </div>
      </PageSection>
    </template>
  </div>
</template>
