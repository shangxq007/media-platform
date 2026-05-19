<script setup lang="ts">
import { ref, computed } from 'vue'
import { NlqAPI } from '@/api/analytics'
import type { NlqPreviewResponse, NlqExecuteResponse, DatasetInfo } from '@/api/analytics'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'

const question = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const preview = ref<NlqPreviewResponse | null>(null)
const result = ref<NlqExecuteResponse | null>(null)
const datasets = ref<DatasetInfo[]>([])
const showConfirmDialog = ref(false)
const sqlExplanation = ref<string | null>(null)

const canExecute = computed(() => {
  return preview.value != null && preview.value.safety.safe && !loading.value
})

const riskVariant = (level: string): 'success' | 'warning' | 'danger' | 'neutral' => {
  switch (level) {
    case 'LOW': return 'success'
    case 'MEDIUM': return 'warning'
    case 'HIGH': case 'CRITICAL': return 'danger'
    default: return 'neutral'
  }
}

async function loadDatasets() {
  try {
    const response = await NlqAPI.listDatasets()
    datasets.value = response.datasets
  } catch {
    // datasets load failure is non-critical
  }
}

async function handlePreview() {
  if (!question.value.trim()) return
  loading.value = true
  error.value = null
  result.value = null
  sqlExplanation.value = null
  try {
    preview.value = await NlqAPI.preview(question.value.trim())
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to preview query'
    preview.value = null
  } finally {
    loading.value = false
  }
}

async function handleExecute() {
  if (!preview.value) return

  if (preview.value.requiresConfirmation) {
    showConfirmDialog.value = true
    return
  }

  await executeQuery()
}

async function executeQuery() {
  if (!preview.value) return
  loading.value = true
  error.value = null
  showConfirmDialog.value = false
  try {
    result.value = await NlqAPI.execute(
      preview.value.sqlDraft, question.value.trim(), undefined, undefined, 100, true)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to execute query'
    result.value = null
  } finally {
    loading.value = false
  }
}

async function handleExplain() {
  if (!question.value.trim()) return
  loading.value = true
  error.value = null
  try {
    const response = await NlqAPI.explain(question.value.trim())
    sqlExplanation.value = (response as { explanation: string }).explanation || 'No explanation available.'
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to explain query'
  } finally {
    loading.value = false
  }
}

function reset() {
  question.value = ''
  preview.value = null
  result.value = null
  error.value = null
  sqlExplanation.value = null
}

function variantFromStatus(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status?.toLowerCase()) {
    case 'completed': case 'success': case 'allowed': return 'success'
    case 'failed': case 'denied': case 'unsafe': return 'danger'
    case 'pending': case 'running': return 'warning'
    default: return 'neutral'
  }
}

loadDatasets()
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Analytics Assistant" subtitle="Ask questions about your data in natural language">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="reset">Reset</button>
      </template>
    </PageHeader>

    <!-- Query Input -->
    <PageSection title="Ask a Question">
      <div class="flex gap-md">
        <input
          v-model="question"
          type="text"
          class="flex-1 px-md py-sm border border-default rounded text-sm bg-bg-base text-text-primary"
          placeholder="e.g. Show me render job failures over the last 30 days"
          @keyup.enter="handlePreview"
        />
        <button class="theme-btn theme-btn-primary theme-btn-sm" :disabled="!question.trim() || loading" @click="handlePreview">
          Preview
        </button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" :disabled="!question.trim() || loading" @click="handleExplain">
          Explain
        </button>
      </div>
    </PageSection>

    <LoadingState v-if="loading" message="Processing query..." />
    <ErrorState v-else-if="error" :description="error" @retry="handlePreview" />

    <template v-else>
      <!-- SQL Explanation -->
      <PageSection v-if="sqlExplanation" title="Explanation">
        <div class="p-md bg-bg-surface rounded border border-default text-sm text-text-secondary">
          {{ sqlExplanation }}
        </div>
      </PageSection>

      <!-- Preview Panel -->
      <PageSection v-if="preview" title="Query Preview">
        <div class="space-y-md">
          <div class="flex items-center gap-md flex-wrap">
            <StatusBadge :variant="riskVariant(preview.riskLevel)" :label="preview.riskLevel" />
            <StatusBadge :variant="variantFromStatus(preview.accessDecision)" :label="preview.accessDecision" />
            <span class="text-xs text-text-muted">Intent: {{ preview.intent }}</span>
            <span class="text-xs text-text-muted">Datasets: {{ preview.datasets.join(', ') }}</span>
          </div>

          <div class="p-md bg-bg-surface rounded border border-default">
            <pre class="text-xs text-text-secondary whitespace-pre-wrap font-mono">{{ preview.sqlDraft }}</pre>
          </div>

          <div v-if="preview.safety.violations.length" class="space-y-xs">
            <div class="text-xs font-medium text-text-primary">Safety Warnings:</div>
            <div v-for="v in preview.safety.violations" :key="v" class="text-xs text-warning-500">⚠ {{ v }}</div>
          </div>

          <div v-if="preview.chartSuggestions.length" class="flex flex-wrap gap-sm">
            <span class="text-xs text-text-muted">Suggested charts:</span>
            <span v-for="cs in preview.chartSuggestions" :key="cs" class="text-xs px-sm py-xs bg-bg-surface rounded border border-default">{{ cs }}</span>
          </div>

          <button
            class="theme-btn theme-btn-primary"
            :class="{ 'opacity-50 cursor-not-allowed': !canExecute }"
            :disabled="!canExecute"
            @click="handleExecute"
          >
            Execute Query
          </button>
        </div>
      </PageSection>

      <!-- Results Panel -->
      <PageSection v-if="result" title="Results">
        <div class="space-y-md">
          <div class="flex items-center gap-md flex-wrap">
            <span class="text-xs text-text-muted">{{ result.rowCount }} rows · {{ result.durationMs }}ms</span>
            <StatusBadge v-if="result.truncated" variant="warning" label="Truncated" />
          </div>

          <p v-if="result.summary" class="text-sm text-text-secondary">{{ result.summary }}</p>

          <div v-if="result.rows.length" class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-default">
                  <th v-for="col in result.columns" :key="col" class="text-left px-sm py-xs text-text-muted font-medium text-xs">{{ col }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in result.rows" :key="idx" class="border-b border-default hover:bg-bg-surface">
                  <td v-for="col in result.columns" :key="col" class="px-sm py-xs text-text-secondary text-xs">{{ row[col] ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="result.chartSuggestions.length" class="space-y-sm">
            <div class="text-xs font-medium text-text-primary">Chart Suggestions:</div>
            <div class="flex flex-wrap gap-sm">
              <div v-for="cs in result.chartSuggestions" :key="cs.chartType"
                class="p-sm bg-bg-surface rounded border border-default text-xs">
                <div class="font-medium text-text-primary">{{ cs.title }}</div>
                <div class="text-text-muted">{{ cs.reason }}</div>
              </div>
            </div>
          </div>
        </div>
      </PageSection>

      <!-- Available Datasets -->
      <PageSection title="Available Datasets">
        <div v-if="datasets.length" class="grid grid-cols-2 gap-md">
          <div v-for="ds in datasets" :key="ds.datasetKey" class="p-md bg-bg-surface rounded border border-default">
            <div class="text-sm font-medium text-text-primary">{{ ds.name }}</div>
            <div class="text-xs text-text-muted mt-xs">{{ ds.description }}</div>
            <div class="flex items-center gap-sm mt-sm">
              <StatusBadge :variant="ds.enabled ? 'success' : 'neutral'" :label="ds.enabled ? 'Enabled' : 'Disabled'" />
              <span class="text-xs text-text-muted">{{ ds.module }}</span>
              <span class="text-xs text-text-muted">Max: {{ ds.maxRows }} rows</span>
            </div>
          </div>
        </div>
        <EmptyState v-else title="No datasets available" description="Datasets will appear here once configured." />
      </PageSection>
    </template>

    <ConfirmDialog
      v-if="showConfirmDialog"
      :open="showConfirmDialog"
      title="Confirm Query Execution"
      message="This query has been flagged as high-risk. Are you sure you want to execute it?"
      @confirm="executeQuery"
      @cancel="showConfirmDialog = false"
    />
  </div>
</template>
