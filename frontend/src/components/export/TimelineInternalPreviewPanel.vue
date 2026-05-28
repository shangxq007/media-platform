<script setup lang="ts">
import { ref } from 'vue'
import { AiTimelineAPI, type TimelineInternalPreviewResponse } from '@/api/ai-timeline'
import { useProjectStore } from '@/stores/project'

const props = defineProps<{
  projectId: string
  timelineJson: string
}>()

const emit = defineEmits<{
  previewed: [internalJson: string]
  error: [message: string]
}>()

const loading = ref(false)
const preview = ref<TimelineInternalPreviewResponse | null>(null)
const showDiff = ref(false)

async function runPreview() {
  loading.value = true
  preview.value = null
  try {
    const res = await AiTimelineAPI.previewInternal(
      useProjectStore().currentTenant,
      props.projectId,
      props.timelineJson
    )
    preview.value = res
    emit('previewed', res.internalTimelineJson)
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Preview failed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="rounded-lg border border-border-subtle/80 bg-surface-0/40 p-sm space-y-sm">
    <div class="flex items-center justify-between gap-sm">
      <span class="text-xs font-medium text-text-primary">Internal Timeline 1.0 预览</span>
      <button
        type="button"
        class="rounded border border-border-default px-sm py-0.5 text-xs hover:bg-surface-2 disabled:opacity-50"
        :disabled="!timelineJson.trim() || loading"
        @click="runPreview"
      >
        {{ loading ? '转换中…' : '预览转换' }}
      </button>
    </div>
    <p class="text-[10px] text-text-tertiary">
      将编辑器 schema 2.0 规范化为服务端增量渲染使用的 Internal 1.0 JSON。
    </p>

    <div
      v-if="preview"
      class="text-xs text-text-secondary space-y-1"
    >
      <div>
        源: <span class="font-mono text-text-primary">{{ preview.sourceSchema }}</span>
        <span v-if="preview.alreadyInternal" class="text-emerald-500 ml-1">已是 1.0</span>
      </div>
      <div class="grid grid-cols-2 gap-1 font-mono text-[10px]">
        <span>轨/层 {{ preview.sourceTrackOrLayerCount }} → {{ preview.internalTrackOrLayerCount }}</span>
        <span>片段 {{ preview.sourceClipCount }} → {{ preview.internalClipCount }}</span>
        <span>revision {{ preview.targetRevision }}</span>
        <span>Δ {{ preview.jsonByteDelta >= 0 ? '+' : '' }}{{ preview.jsonByteDelta }} B</span>
      </div>
      <button
        type="button"
        class="text-violet-400 hover:underline text-[10px]"
        @click="showDiff = !showDiff"
      >
        {{ showDiff ? '收起 JSON' : '展开 JSON 片段' }}
      </button>
      <pre
        v-if="showDiff"
        class="max-h-28 overflow-auto text-[10px] text-text-tertiary bg-black/30 p-xs rounded"
      >{{ preview.internalTimelineJson.slice(0, 1200) }}{{ preview.internalTimelineJson.length > 1200 ? '…' : '' }}</pre>
    </div>
  </div>
</template>
