<script setup lang="ts">
import { ref, watch } from 'vue'
import { TimelineRevisionAPI, type TimelinePatchStepsResult } from '@/api/timelineRevision'
import { useTimelineStore } from '@/stores/timeline'
import { applyPatchPathHighlights, loadRevisionInternalJson } from '@/utils/timelinePatchHighlight'

const timelineStore = useTimelineStore()

const props = defineProps<{
  open: boolean
  projectId: string | null | undefined
  revisionId: string | null | undefined
  revisionNumber?: number
}>()

const emit = defineEmits<{
  close: []
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const stepsResult = ref<TimelinePatchStepsResult | null>(null)

async function load() {
  if (!props.projectId || !props.revisionId) {
    stepsResult.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    const internalJson =
      props.projectId && props.revisionId
        ? await loadRevisionInternalJson(props.projectId, props.revisionId)
        : null
    stepsResult.value = await TimelineRevisionAPI.patchSteps(props.projectId!, props.revisionId!)
    if (stepsResult.value?.steps?.length) {
      applyPatchPathHighlights(
        stepsResult.value.steps.map(s => ({ op: s.op, path: s.path })),
        [],
        timelineStore,
        internalJson
      )
      timelineStore.focusFirstPatchHighlightClip()
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '分步预览失败'
    stepsResult.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.revisionId] as const,
  ([open]) => {
    if (open) {
      void load()
    } else {
      timelineStore.clearPatchHighlightClipIds()
    }
  }
)
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4"
    @click.self="emit('close')"
  >
    <div
      class="w-full max-w-lg max-h-[80vh] flex flex-col rounded-xl border border-default bg-bg-elevated shadow-xl"
      role="dialog"
    >
      <header class="flex items-center justify-between border-b border-default px-4 py-3">
        <h2 class="text-sm font-semibold text-text-primary">
          Patch 分步预览
          <span v-if="revisionNumber != null" class="text-text-muted font-normal">
            #{{ revisionNumber }}
          </span>
        </h2>
        <button type="button" class="theme-btn theme-btn-ghost theme-btn-sm" @click="emit('close')">
          关闭
        </button>
      </header>
      <div class="flex-1 overflow-y-auto p-4 text-xs theme-scrollbar">
        <p v-if="loading" class="text-text-muted">加载中…</p>
        <p v-else-if="error" class="text-danger-500">{{ error }}</p>
        <p v-else-if="stepsResult && !stepsResult.hasPatchOps" class="text-text-muted">无存储的 patch</p>
        <template v-else-if="stepsResult">
          <p
            class="mb-3"
            :class="stepsResult.allStepsSucceeded ? 'text-success-500' : 'text-warning-500'"
          >
            {{ stepsResult.allStepsSucceeded ? '全部步骤成功' : '部分步骤失败' }}
            （共 {{ stepsResult.steps.length }} 步）
          </p>
          <ol class="space-y-2">
            <li
              v-for="step in stepsResult.steps"
              :key="step.stepIndex"
              class="border border-default rounded-lg p-2 font-mono"
              :class="step.success ? 'border-default' : 'border-danger-500/50'"
            >
              <div class="flex gap-2 items-start">
                <span class="text-text-muted shrink-0">{{ step.stepIndex + 1 }}.</span>
                <div class="min-w-0 flex-1">
                  <div>
                    <span class="text-primary-400">{{ step.op }}</span>
                    <span class="text-text-primary ml-1">{{ step.path }}</span>
                    <span
                      class="ml-2"
                      :class="step.success ? 'text-success-500' : 'text-danger-500'"
                    >{{ step.success ? 'OK' : 'FAIL' }}</span>
                  </div>
                  <ul v-if="step.errors.length" class="text-danger-500 mt-1 list-disc pl-4">
                    <li v-for="(err, i) in step.errors" :key="i">{{ err }}</li>
                  </ul>
                  <p
                    v-if="step.contentHashAfter"
                    class="text-[10px] text-text-muted mt-1 truncate"
                  >
                    hash: {{ step.contentHashAfter.slice(0, 16) }}…
                  </p>
                </div>
              </div>
            </li>
          </ol>
        </template>
      </div>
    </div>
  </div>
</template>
