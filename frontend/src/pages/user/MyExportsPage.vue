<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useExportUiStore } from '@/stores/exportUi'
import type { RenderJobDetailed } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { MeEntitlementAPI } from '@/api/me'
import { formatApiError } from '@/utils/apiError'
import { getTenantId } from '@/utils/tenant'
import DeliveryStatusPanel from '@/components/export/DeliveryStatusPanel.vue'

const router = useRouter()
const exportUi = useExportUiStore()

const loading = ref(true)
const error = ref<string | null>(null)
const exports = ref<RenderJobDetailed[]>([])
const currentPage = ref(1)
const pageSize = 10

onMounted(loadExports)

async function loadExports() {
  loading.value = true
  error.value = null
  try {
    const result = await MeEntitlementAPI.getMyExports(currentPage.value - 1, pageSize)
    exports.value = result.exports ?? []
  } catch (e: unknown) {
    error.value = formatApiError(e, 'Failed to load exports')
  } finally {
    loading.value = false
  }
}

const paginatedExports = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return exports.value.slice(start, start + pageSize)
})

const totalPages = computed(() => Math.max(1, Math.ceil(exports.value.length / pageSize)))

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'completed': return 'success'
    case 'running': case 'queued': return 'warning'
    case 'failed': case 'cancelled': return 'danger'
    default: return 'neutral'
  }
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function openIncrementalEdit(projectId: string, jobId?: string) {
  exportUi.requestIncrementalEdit(projectId, jobId)
  router.push({
    path: '/',
    query: {
      export: 'incremental',
      projectId,
      ...(jobId ? { baseJobId: jobId } : {}),
    },
  })
}

function formatDuration(seconds: number): string {
  if (!seconds || seconds === 0) return '—'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Exports" subtitle="View and manage your export and render jobs">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/')">New Export</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadExports">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading exports..." />
    <ErrorState v-else-if="error" title="Unable to load exports" :description="error" @retry="loadExports" />

    <template v-else>
      <EmptyState v-if="exports.length === 0" icon="📤" title="No exports yet" description="Export a project from the editor to see it here.">
        <template #action>
          <button class="theme-btn theme-btn-primary" @click="router.push('/')">Open Editor</button>
        </template>
      </EmptyState>

      <template v-else>
        <PageSection title="Export Jobs">
          <div class="space-y-sm">
            <div v-for="exp in paginatedExports" :key="exp.id"
              class="c-card">
              <div class="c-card-body">
                <div class="flex items-center justify-between mb-sm">
                  <div class="min-w-0 flex-1">
                    <div class="text-sm font-medium text-text-primary">
                      {{ exp.format?.toUpperCase() || 'Export' }} · {{ exp.resolution || '—' }} · {{ exp.profile || exp.preset || 'default' }}
                    </div>
                    <div class="text-xs text-text-muted mt-xs">
                      Created: {{ exp.createdAt }}
                      <span v-if="exp.completedAt"> · Completed: {{ exp.completedAt }}</span>
                    </div>
                  </div>
                  <StatusBadge :variant="statusVariant(exp.status)" :label="exp.status" />
                </div>

                <!-- Progress bar for running jobs -->
                <div v-if="exp.status === 'running' || exp.status === 'queued'" class="mb-sm">
                  <div class="w-full bg-bg-surface rounded-full h-2">
                    <div class="h-2 rounded-full bg-warning-500 transition-all"
                      :style="{ width: (exp.progress || 0) + '%' }" />
                  </div>
                  <div class="text-xs text-text-muted mt-xs">{{ exp.progress || 0 }}% complete</div>
                </div>

                <!-- Artifact details -->
                <div v-if="exp.artifact" class="flex items-center gap-md text-xs text-text-muted">
                  <span v-if="exp.artifact.fileSize">Size: {{ formatFileSize(exp.artifact.fileSize) }}</span>
                  <span v-if="exp.artifact.duration">Duration: {{ formatDuration(exp.artifact.duration) }}</span>
                  <span v-if="exp.artifact.width && exp.artifact.height">{{ exp.artifact.width }}x{{ exp.artifact.height }}</span>
                  <span>Provider: {{ exp.artifact.provider }}</span>
                </div>

                <!-- Error message -->
                <div v-if="exp.status === 'failed' && exp.errorMessage" class="mt-sm p-sm bg-danger-500/10 rounded text-xs text-danger-500">
                  {{ exp.errorMessage }}
                </div>

                <DeliveryStatusPanel
                  v-if="exp.status === 'completed' && exp.projectId"
                  :tenant-id="getTenantId()"
                  :project-id="exp.projectId"
                  :job-id="exp.id"
                  compact
                  class="mt-sm"
                />

                <!-- Actions -->
                <div class="flex items-center gap-sm mt-sm pt-sm border-t border-default flex-wrap">
                  <button
                    v-if="exp.projectId"
                    type="button"
                    class="theme-btn theme-btn-primary theme-btn-sm"
                    @click="openIncrementalEdit(exp.projectId, exp.status === 'completed' ? exp.id : undefined)"
                  >
                    {{ exp.status === 'completed' ? '增量改稿' : '打开编辑器导出' }}
                  </button>
                  <button v-if="exp.status === 'failed'" type="button" class="theme-btn theme-btn-secondary theme-btn-sm">
                    Retry
                  </button>
                  <button v-if="exp.status === 'running' || exp.status === 'queued'" type="button" class="theme-btn theme-btn-ghost theme-btn-sm">
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="totalPages > 1" class="flex items-center justify-between px-md py-sm border-t border-default bg-bg-surface">
            <div class="text-xs text-text-muted">
              Showing {{ (currentPage - 1) * pageSize + 1 }}–{{ Math.min(currentPage * pageSize, exports.length) }} of {{ exports.length }}
            </div>
            <div class="flex items-center gap-xs">
              <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage <= 1" @click="currentPage -= 1">←</button>
              <span class="text-xs text-text-secondary px-sm">{{ currentPage }} / {{ totalPages }}</span>
              <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage >= totalPages" @click="currentPage += 1">→</button>
            </div>
          </div>
        </PageSection>
      </template>
    </template>
  </div>
</template>
