<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ReportAPI } from '@/api/analytics'
import type { ReportInfo, ReportExecutionResponse } from '@/api/analytics'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const reports = ref<ReportInfo[]>([])
const executingReportId = ref<string | null>(null)
const executionResults = ref<Record<string, ReportExecutionResponse>>({})
const showArchiveDialog = ref(false)
const archivingReportId = ref<string | null>(null)

onMounted(loadReports)

async function loadReports() {
  loading.value = true
  error.value = null
  try {
    const response = await ReportAPI.listReports()
    reports.value = response.reports
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load reports'
  } finally {
    loading.value = false
  }
}

async function handleExecute(reportId: string) {
  executingReportId.value = reportId
  try {
    const result = await ReportAPI.executeReport(reportId)
    executionResults.value[reportId] = result
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to execute report'
  } finally {
    executingReportId.value = null
  }
}

function confirmArchive(reportId: string) {
  archivingReportId.value = reportId
  showArchiveDialog.value = true
}

async function handleArchive() {
  if (!archivingReportId.value) return
  showArchiveDialog.value = false
  try {
    await ReportAPI.archiveReport(archivingReportId.value)
    reports.value = reports.value.filter(r => r.reportId !== archivingReportId.value)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to archive report'
  } finally {
    archivingReportId.value = null
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status?.toLowerCase()) {
    case 'success': case 'completed': return 'success'
    case 'running': case 'pending': return 'warning'
    case 'failed': case 'error': return 'danger'
    default: return 'neutral'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="My Reports" subtitle="Manage and execute your saved reports" />

    <LoadingState v-if="loading" message="Loading reports..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadReports" />

    <template v-else>
      <PageSection title="Saved Reports">
        <EmptyState v-if="reports.length === 0" title="No reports yet" description="Create a report from the Analytics Assistant to get started." />
        <div v-else class="space-y-md">
          <div v-for="report in reports" :key="report.reportId"
            class="p-md bg-bg-surface rounded border border-default">
            <div class="flex items-center justify-between">
              <div>
                <div class="text-sm font-medium text-text-primary">{{ report.name }}</div>
                <div class="text-xs text-text-muted mt-xs">{{ report.description }}</div>
                <div class="flex items-center gap-sm mt-sm">
                  <span class="text-xs text-text-muted">By {{ report.createdBy }}</span>
                  <span class="text-xs text-text-muted">{{ report.visibility }}</span>
                  <span class="text-xs text-text-muted">{{ new Date(report.createdAt).toLocaleDateString() }}</span>
                </div>
              </div>
              <div class="flex items-center gap-sm">
                <button
                  class="theme-btn theme-btn-primary theme-btn-sm"
                  :disabled="executingReportId === report.reportId"
                  @click="handleExecute(report.reportId)"
                >
                  {{ executingReportId === report.reportId ? 'Running...' : 'Execute' }}
                </button>
                <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="confirmArchive(report.reportId)">
                  Archive
                </button>
              </div>
            </div>
            <div v-if="executionResults[report.reportId]" class="mt-sm p-sm bg-bg-base rounded border border-default">
              <div class="flex items-center gap-md">
                <StatusBadge :variant="statusVariant(executionResults[report.reportId].status)" :label="executionResults[report.reportId].status" />
                <span class="text-xs text-text-muted">{{ executionResults[report.reportId].rowCount }} rows · {{ executionResults[report.reportId].durationMs }}ms</span>
              </div>
            </div>
          </div>
        </div>
      </PageSection>
    </template>

    <ConfirmDialog
      v-if="showArchiveDialog"
      :open="showArchiveDialog"
      title="Archive Report"
      message="Are you sure you want to archive this report? It will no longer appear in the active list."
      @confirm="handleArchive"
      @cancel="showArchiveDialog = false"
    />
  </div>
</template>
