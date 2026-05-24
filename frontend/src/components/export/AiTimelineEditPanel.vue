<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { AiTimelineAPI, IncrementalRenderAPI } from '@/api'
import type { AiProposalDto } from '@/api/ai-timeline'
import type { RenderJob } from '@/types'
import { getTenantId } from '@/utils/tenant'
import { useExportUiStore } from '@/stores/exportUi'

const props = defineProps<{
  projectId: string
  timelineJson: string
  completedJobs: RenderJob[]
}>()

const editSessionId = defineModel<string>('editSessionId', {
  default: () => `sess-${Date.now()}`,
})

const emit = defineEmits<{
  applied: [timelineJson: string]
  proposalsUpdated: [timelineJson: string, proposals: AiProposalDto[]]
  error: [message: string]
}>()

const exportUi = useExportUiStore()

const instruction = ref('')
const intent = ref('')
const baseJobId = ref(exportUi.baseJobId || '')
const humanInTheLoop = ref(true)
const loading = ref(false)
const proposals = ref<AiProposalDto[]>([])
const lastResult = ref<{ provider: string; model: string; appliedPatch: boolean } | null>(null)

watch(
  () => exportUi.baseJobId,
  id => {
    if (id) baseJobId.value = id
  },
  { immediate: true }
)

const canRun = computed(
  () =>
    instruction.value.trim().length > 0 &&
    (baseJobId.value || props.timelineJson.trim().length > 0)
)

async function runEdit() {
  loading.value = true
  lastResult.value = null
  try {
    const tenantId = getTenantId()
    const body = {
      instruction: instruction.value.trim(),
      editSessionId: editSessionId.value?.trim() || undefined,
      intent: intent.value || undefined,
      baseJobId: baseJobId.value || undefined,
      baseTimelineJson: baseJobId.value ? undefined : props.timelineJson,
      humanInTheLoop: humanInTheLoop.value,
    }
    const res = await AiTimelineAPI.edit(tenantId, props.projectId, body)
    lastResult.value = {
      provider: res.provider,
      model: res.model,
      appliedPatch: res.appliedPatch,
    }
    proposals.value = res.proposals ?? []
    emit('applied', res.timelineJson)
    emit('proposalsUpdated', res.timelineJson, proposals.value)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : 'AI edit failed'
    emit('error', msg)
  } finally {
    loading.value = false
  }
}

async function loadBaseFromJob() {
  if (!baseJobId.value) return
  const tenantId = getTenantId()
  try {
    const { timelineJson } = await IncrementalRenderAPI.getJobTimeline(
      tenantId,
      props.projectId,
      baseJobId.value
    )
    if (timelineJson) {
      emit('applied', timelineJson)
    }
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Failed to load base timeline')
  }
}
</script>

<template>
  <div class="rounded-lg border border-border-subtle bg-surface-0/60 p-md space-y-sm">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium text-text-primary">AI 时间线编辑</span>
      <span class="text-xs text-text-tertiary font-mono">{{ editSessionId }}</span>
    </div>
    <p class="text-xs text-text-secondary">
      基于上一轮成片时间线用自然语言改素材、特效或结构；结果写入 Internal Timeline 1.0（经 LiteLLM / Stub）。
    </p>

    <label class="block text-xs text-text-secondary">基准作业（可选，留空则用当前编辑器时间线）</label>
    <select
      v-model="baseJobId"
      class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm text-text-primary"
      @change="loadBaseFromJob"
    >
      <option value="">— 当前时间线 —</option>
      <option
        v-for="j in completedJobs"
        :key="j.id"
        :value="j.id"
      >
        {{ j.id }} ({{ j.status }})
      </option>
    </select>

    <label class="block text-xs text-text-secondary">意图标签（审计）</label>
    <input
      v-model="intent"
      type="text"
      placeholder="e.g. replace_bgm, shorten_intro"
      class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm"
    />

    <label class="flex items-center gap-xs text-xs text-text-primary">
      <input
        v-model="humanInTheLoop"
        type="checkbox"
      >
      人工确认后再应用 Patch（写入 aiProposals）
    </label>

    <label class="block text-xs text-text-secondary">编辑指令</label>
    <textarea
      v-model="instruction"
      rows="3"
      placeholder="例如：将 BGM 音量降低 30%，片头增加 1 秒淡入"
      class="w-full rounded border border-border-default bg-surface-2 px-sm py-xs text-sm resize-y"
    />

    <button
      type="button"
      class="w-full rounded bg-violet-600 hover:bg-violet-500 disabled:opacity-50 px-md py-xs text-sm font-medium"
      :disabled="!canRun || loading"
      @click="runEdit"
    >
      {{ loading ? 'AI 处理中…' : humanInTheLoop ? '生成 AI 建议' : '应用 AI 编辑' }}
    </button>

    <p
      v-if="lastResult"
      class="text-xs text-text-tertiary"
    >
      {{ lastResult.provider }} / {{ lastResult.model }}
      <span v-if="lastResult.appliedPatch"> · JSON Patch</span>
    </p>
  </div>
</template>
