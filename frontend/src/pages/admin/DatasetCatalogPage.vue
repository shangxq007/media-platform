<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NlqAPI } from '@/api/analytics'
import type { DatasetInfo } from '@/api/analytics'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const datasets = ref<DatasetInfo[]>([])
const selectedDataset = ref<DatasetInfo | null>(null)
const detailLoading = ref(false)

onMounted(loadDatasets)

async function loadDatasets() {
  loading.value = true
  error.value = null
  try {
    const response = await NlqAPI.listDatasets()
    datasets.value = response.datasets
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load datasets'
  } finally {
    loading.value = false
  }
}

async function selectDataset(key: string) {
  detailLoading.value = true
  try {
    const response = await NlqAPI.getDataset(key)
    selectedDataset.value = response.dataset
  } catch {
    selectedDataset.value = datasets.value.find(d => d.datasetKey === key) || null
  } finally {
    detailLoading.value = false
  }
}

function sensitivityVariant(level: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (level?.toLowerCase()) {
    case 'low': return 'success'
    case 'medium': return 'warning'
    case 'high': return 'danger'
    default: return 'neutral'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Dataset Catalog" subtitle="Manage available datasets for natural language queries" />

    <LoadingState v-if="loading" message="Loading datasets..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadDatasets" />

    <template v-else>
      <EmptyState v-if="datasets.length === 0" title="No datasets configured" description="Datasets will appear once registered in the query catalog." />

      <div v-else class="grid grid-cols-3 gap-lg">
        <!-- Dataset List -->
        <div class="col-span-2">
          <PageSection title="Datasets" description="Click a dataset to view details">
            <div class="space-y-sm">
              <div v-for="ds in datasets" :key="ds.datasetKey"
                class="p-md rounded border cursor-pointer transition-colors"
                :class="selectedDataset?.datasetKey === ds.datasetKey ? 'border-primary-200 bg-primary-50' : 'border-default bg-bg-surface hover:border-primary-200'"
                @click="selectDataset(ds.datasetKey)">
                <div class="flex items-center justify-between">
                  <div>
                    <div class="text-sm font-medium text-text-primary">{{ ds.name }}</div>
                    <div class="text-xs text-text-muted mt-xs">{{ ds.datasetKey }}</div>
                  </div>
                  <div class="flex items-center gap-sm">
                    <StatusBadge :variant="ds.enabled ? 'success' : 'neutral'" :label="ds.enabled ? 'Enabled' : 'Disabled'" />
                    <StatusBadge :variant="sensitivityVariant(ds.sensitivityLevel)" :label="ds.sensitivityLevel" />
                  </div>
                </div>
              </div>
            </div>
          </PageSection>
        </div>

        <!-- Dataset Detail -->
        <div>
          <PageSection title="Dataset Details">
            <LoadingState v-if="detailLoading" message="Loading..." />
            <template v-else-if="selectedDataset">
              <div class="space-y-md">
                <div>
                  <div class="text-sm font-medium text-text-primary">{{ selectedDataset.name }}</div>
                  <div class="text-xs text-text-muted mt-xs">{{ selectedDataset.description }}</div>
                </div>
                <div class="space-y-sm text-xs">
                  <div class="flex justify-between">
                    <span class="text-text-muted">Key</span>
                    <span class="text-text-secondary font-mono">{{ selectedDataset.datasetKey }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">View</span>
                    <span class="text-text-secondary font-mono">{{ selectedDataset.viewName }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Module</span>
                    <span class="text-text-secondary">{{ selectedDataset.module }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Max Rows</span>
                    <span class="text-text-secondary">{{ selectedDataset.maxRows }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Lookback</span>
                    <span class="text-text-secondary">{{ selectedDataset.maxLookbackDays }} days</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Tenant Scoped</span>
                    <span class="text-text-secondary">{{ selectedDataset.tenantScoped ? 'Yes' : 'No' }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Workspace Scoped</span>
                    <span class="text-text-secondary">{{ selectedDataset.workspaceScoped ? 'Yes' : 'No' }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">User Scoped</span>
                    <span class="text-text-secondary">{{ selectedDataset.userScoped ? 'Yes' : 'No' }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-text-muted">Sensitivity</span>
                    <StatusBadge :variant="sensitivityVariant(selectedDataset.sensitivityLevel)" :label="selectedDataset.sensitivityLevel" />
                  </div>
                </div>
              </div>
            </template>
            <EmptyState v-else title="No dataset selected" description="Select a dataset to view its details." />
          </PageSection>
        </div>
      </div>
    </template>
  </div>
</template>
