<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { IncrementalRenderAPI } from '@/api'
import type { IncrementalRenderPlanResponse } from '@/api/render-incremental'
import type { RenderJob } from '@/types'
import { useProjectStore } from '@/stores/project'
import { useExportUiStore } from '@/stores/exportUi'

const props = defineProps<{
  projectId: string
  timelineJson: string
  profile: string
  completedJobs: RenderJob[]
  editSessionId?: string
}>()

const emit = defineEmits<{
  submitted: [jobId: string]
  error: [message: string]
}>()

const exportUi = useExportUiStore()
const baseJobId = ref(exportUi.baseJobId || '')
const useAiInstruction = ref(false)
const aiEditInstruction = ref('')
const editSessionId = computed(() => props.editSessionId ?? '')
const targetSegments = ref('')
const plan = ref<IncrementalRenderPlanResponse | null>(null)
const loadingPlan = ref(false)
const submitting = ref(false)

const canPreview = computed(() => props.timelineJson.trim().length > 0)
const canSubmit = computed(() => canPreview.value && !submitting.value)

watch(
  () => exportUi.baseJobId,
  id => {
    if (id) baseJobId.value = id
  },
  { immediate: true }
)

watch(
  () => props.completedJobs,
  jobs => {
    if (baseJobId.value) return
    const last = jobs.find(j => j.status === 'COMPLETED')
    if (last) {
      baseJobId.value = last.id
    }
  },
  { immediate: true }
)

async function previewPlan() {
  loadingPlan.value = true
  plan.value = null
  try {
    const projectStore = useProjectStore()
    const tenantId = projectStore.currentTenant
    let oldJson: string | undefined
    if (baseJobId.value) {
      const loaded = await IncrementalRenderAPI.getJobTimeline(
        tenantId,
        props.projectId,
        baseJobId.value
      )
      oldJson = loaded.timelineJson || undefined
    }
    plan.value = await IncrementalRenderAPI.previewPlan(tenantId, props.projectId, {
      newTimelineJson: props.timelineJson,
      oldTimelineJson: oldJson,
      profile: props.profile,
      baseJobId: baseJobId.value || undefined,
    })
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Plan preview failed')
  } finally {
    loadingPlan.value = false
  }
}

async function submitIncremental() {
  submitting.value = true
  try {
    const projectStore = useProjectStore()
    const tenantId = projectStore.currentTenant
    const segmentIds = targetSegments.value
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
    const { jobId } = await IncrementalRenderAPI.submitJob(tenantId, props.projectId, {
      tenantId,
      projectId: props.projectId,
      prompt: useAiInstruction.value ? undefined : props.timelineJson,
      profile: props.profile,
      baseJobId: baseJobId.value || undefined,
      targetSegmentIds: segmentIds.length ? segmentIds : undefined,
      editSessionId: editSessionId.value.trim() || undefined,
      aiEditIntent: useAiInstruction.value ? 'natural_language_edit' : undefined,
      aiEditInstruction: useAiInstruction.value ? aiEditInstruction.value : undefined,
    })
    emit('submitted', jobId)
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Submit failed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-lg border border-border-subtle bg-surface-0/60 p-md space-y-sm">
    <span class="text-sm font-medium text-text-primary">增量渲染</span>
    <p class="text-xs text-text-secondary">
      对比基准作业做语义 Diff，复用未脏段缓存；推荐配合 Internal Timeline 1.0 JSON 提交。
    </p>

    <label class="block text-xs text-text-secondary">基准作业 ID</label>
    <select
      v-model="baseJobId"
      class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm"
    >
      <option value="">全量（无基准）</option>
      <option
        v-for="j in completedJobs"
        :key="j.id"
        :value="j.id"
      >
        {{ j.id }}
      </option>
    </select>

    <label class="block text-xs text-text-secondary">仅渲染段（可选，逗号分隔 seg_0,seg_1）</label>
    <input
      v-model="targetSegments"
      type="text"
      class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm font-mono"
    />

    <label class="flex items-center gap-xs text-xs text-text-primary">
      <input
        v-model="useAiInstruction"
        type="checkbox"
      >
      提交时用自然语言改基准时间线（需基准作业 + aiEditInstruction）
    </label>
    <template v-if="useAiInstruction">
      <p
        v-if="editSessionId"
        class="text-xs text-text-tertiary font-mono"
      >
        会话: {{ editSessionId }}
      </p>
      <textarea
        v-model="aiEditInstruction"
        rows="2"
        placeholder="自然语言改稿指令"
        class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm"
      />
    </template>

    <div class="flex gap-xs">
      <button
        type="button"
        class="flex-1 rounded border border-border-default hover:bg-surface-2 disabled:opacity-50 py-xs text-xs"
        :disabled="!canPreview || loadingPlan"
        @click="previewPlan"
      >
        {{ loadingPlan ? '分析中…' : '预览增量计划' }}
      </button>
      <button
        type="button"
        class="flex-1 rounded bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 py-xs text-xs font-medium"
        :disabled="!canSubmit"
        @click="submitIncremental"
      >
        {{ submitting ? '提交中…' : '增量导出' }}
      </button>
    </div>

    <div
      v-if="plan"
      class="text-xs text-text-tertiary space-y-1 max-h-32 overflow-y-auto font-mono"
    >
      <div>mode={{ plan.mode }} changes={{ plan.changeCount }} reuse={{ plan.reuseTaskIds.length }} execute={{ plan.executeTaskIds.length }}</div>
      <div
        v-for="t in plan.tasks.slice(0, 6)"
        :key="t.taskId"
      >
        {{ t.taskId }} {{ t.type }} {{ t.backend }}
      </div>
    </div>
  </div>
</template>
