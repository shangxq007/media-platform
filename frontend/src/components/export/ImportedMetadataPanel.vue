<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ImportMetadataAPI } from '@/api/import-metadata'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()

const loading = ref(false)
const error = ref<string | null>(null)
const summary = ref<ReturnType<typeof ImportMetadataAPI.getSummary> extends Promise<infer T> ? T : never>(null)
const detail = ref<ReturnType<typeof ImportMetadataAPI.getDetail> extends Promise<infer T> ? T : null>(null)
const showDetail = ref(false)
const expandedSections = ref<Set<string>>(new Set())

const hasMetadata = computed(() => summary.value !== null)

const projectId = computed(() => projectStore.currentProject?.id ?? '')
const tenantId = computed(() => projectStore.currentTenant)

async function fetchSummary() {
  if (!projectId.value || !tenantId.value) {
    summary.value = null
    return
  }

  loading.value = true
  error.value = null
  try {
    summary.value = await ImportMetadataAPI.getSummary(tenantId.value, projectId.value)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load imported metadata'
    summary.value = null
  } finally {
    loading.value = false
  }
}

async function fetchDetail() {
  if (!projectId.value || !tenantId.value) {
    detail.value = null
    return
  }

  loading.value = true
  error.value = null
  try {
    detail.value = await ImportMetadataAPI.getDetail(tenantId.value, projectId.value)
    showDetail.value = true
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load metadata detail'
    detail.value = null
  } finally {
    loading.value = false
  }
}

function toggleSection(section: string) {
  if (expandedSections.value.has(section)) {
    expandedSections.value.delete(section)
  } else {
    expandedSections.value.add(section)
  }
}

function isSensitiveKey(key: string): boolean {
  const lower = key.toLowerCase()
  return ['downloadurl', 'storageuri', 'storageref', 'bucket', 'key', 'signedurl', 'url'].includes(lower)
}

function sanitizeForDisplay(obj: unknown): unknown {
  if (obj === null || obj === undefined) return obj
  if (typeof obj === 'string') {
    if (obj.startsWith('http://') || obj.startsWith('https://')) return '[URL removed]'
    return obj
  }
  if (typeof obj !== 'object') return obj
  if (Array.isArray(obj)) return obj.map(sanitizeForDisplay)
  const result: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
    if (isSensitiveKey(k)) continue
    result[k] = sanitizeForDisplay(v)
  }
  return result
}

watch([projectId, tenantId], () => {
  summary.value = null
  detail.value = null
  showDetail.value = false
  expandedSections.value.clear()
  fetchSummary()
}, { immediate: true })

onMounted(() => {
  if (projectId.value && tenantId.value && !summary.value && !loading.value) {
    fetchSummary()
  }
})
</script>

<template>
  <div v-if="loading" class="text-xs text-text-tertiary p-sm">
    加载导入元数据中…
  </div>

  <div v-else-if="error" class="text-xs text-danger p-sm">
    {{ error }}
  </div>

  <div v-else-if="!hasMetadata" class="text-xs text-text-tertiary p-sm">
    无导入元数据
  </div>

  <div v-else class="rounded-lg border border-border-subtle/80 bg-surface-0/40 p-sm space-y-sm">
    <div class="flex items-center gap-sm">
      <span class="text-xs font-medium text-text-primary">导入元数据</span>
      <span class="text-[10px] px-1.5 py-0.5 rounded bg-info-muted text-info">已导入</span>
    </div>

    <div class="grid grid-cols-2 gap-1 text-[10px] text-text-secondary font-mono">
      <span>importId: {{ summary?.importId }}</span>
      <span>schema: {{ summary?.schemaVersion }}</span>
      <span v-if="summary?.sourceProjectId">source: {{ summary.sourceProjectId }}</span>
      <span>created: {{ summary?.createdAt }}</span>
    </div>

    <div class="flex flex-wrap gap-1">
      <span
        v-if="summary?.timelinePresent"
        class="text-[10px] px-1 py-0.5 rounded bg-success-muted text-success"
      >
        timeline
      </span>
      <span
        v-if="summary?.renderPlanPresent"
        class="text-[10px] px-1 py-0.5 rounded bg-success-muted text-success"
      >
        render
      </span>
      <span
        v-if="summary?.spatialPlanPresent"
        class="text-[10px] px-1 py-0.5 rounded bg-success-muted text-success"
      >
        spatial
      </span>
      <span
        v-if="summary?.effectTaxonomyPresent"
        class="text-[10px] px-1 py-0.5 rounded bg-success-muted text-success"
      >
        effects
      </span>
      <span
        v-if="summary?.assetMappingPresent"
        class="text-[10px] px-1 py-0.5 rounded bg-warning-muted text-warning"
      >
        assets need upload
      </span>
    </div>

    <div v-if="summary?.assetsNeedUpload" class="text-[10px] text-warning">
      媒体资源尚未导入，需要上传或重新绑定。
    </div>

    <button
      v-if="!showDetail"
      type="button"
      class="text-xs text-primary hover:underline"
      @click="fetchDetail"
    >
      查看详细元数据
    </button>

    <div v-else class="space-y-2">
      <div v-if="detail?.timeline" class="border border-border-subtle rounded p-xs">
        <button
          type="button"
          class="text-[10px] font-medium text-text-primary flex items-center gap-1 w-full"
          @click="toggleSection('timeline')"
        >
          <span>{{ expandedSections.has('timeline') ? '▼' : '▶' }}</span>
          Timeline JSON
        </button>
        <pre
          v-if="expandedSections.has('timeline')"
          class="max-h-24 overflow-auto text-[10px] text-text-tertiary bg-black/30 p-xs rounded mt-1"
        >{{ JSON.stringify(sanitizeForDisplay(detail.timeline), null, 2) }}</pre>
      </div>

      <div v-if="detail?.renderPlan" class="border border-border-subtle rounded p-xs">
        <button
          type="button"
          class="text-[10px] font-medium text-text-primary flex items-center gap-1 w-full"
          @click="toggleSection('render')"
        >
          <span>{{ expandedSections.has('render') ? '▼' : '▶' }}</span>
          Render Plan
        </button>
        <pre
          v-if="expandedSections.has('render')"
          class="max-h-24 overflow-auto text-[10px] text-text-tertiary bg-black/30 p-xs rounded mt-1"
        >{{ JSON.stringify(sanitizeForDisplay(detail.renderPlan), null, 2) }}</pre>
      </div>

      <div v-if="detail?.spatialPlan" class="border border-border-subtle rounded p-xs">
        <button
          type="button"
          class="text-[10px] font-medium text-text-primary flex items-center gap-1 w-full"
          @click="toggleSection('spatial')"
        >
          <span>{{ expandedSections.has('spatial') ? '▼' : '▶' }}</span>
          Spatial Plan
        </button>
        <pre
          v-if="expandedSections.has('spatial')"
          class="max-h-24 overflow-auto text-[10px] text-text-tertiary bg-black/30 p-xs rounded mt-1"
        >{{ JSON.stringify(sanitizeForDisplay(detail.spatialPlan), null, 2) }}</pre>
      </div>

      <div v-if="detail?.assetMapping" class="border border-border-subtle rounded p-xs">
        <button
          type="button"
          class="text-[10px] font-medium text-text-primary flex items-center gap-1 w-full"
          @click="toggleSection('assets')"
        >
          <span>{{ expandedSections.has('assets') ? '▼' : '▶' }}</span>
          Asset Mapping
        </button>
        <div
          v-if="expandedSections.has('assets')"
          class="text-[10px] text-text-tertiary mt-1 space-y-0.5"
        >
          <div
            v-for="(entry, id) in detail.assetMapping"
            :key="id"
            class="font-mono"
          >
            {{ id }} → {{ entry.status }}
          </div>
        </div>
      </div>

      <button
        type="button"
        class="text-xs text-text-tertiary hover:text-text-secondary"
        @click="showDetail = false; detail = null"
      >
        收起详情
      </button>
    </div>
  </div>
</template>
