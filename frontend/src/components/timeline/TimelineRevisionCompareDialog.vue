<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  TimelineRevisionAPI,
  type TimelineRevisionCompareResult,
  type TimelineRevisionListItem,
} from '@/api/timelineRevision'
import { useTimelineStore } from '@/stores/timeline'
import { applyPatchPathHighlights } from '@/utils/timelinePatchHighlight'

const timelineStore = useTimelineStore()

const props = defineProps<{
  open: boolean
  projectId: string | null | undefined
  fromRevision?: TimelineRevisionListItem | null
  toRevision?: TimelineRevisionListItem | null
  /** 仅传 ID 时用于冲突对比等场景 */
  fromRevisionId?: string | null
  toRevisionId?: string | null
  fromLabel?: string
  toLabel?: string
  /** HEAD / to-revision Internal JSON，用于解析 /tracks/0/clips/1 类路径 */
  internalTimelineJson?: string | null
}>()

const emit = defineEmits<{
  close: []
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const compare = ref<TimelineRevisionCompareResult | null>(null)

const titleSuffix = computed(() => {
  if (props.fromLabel && props.toLabel) {
    return `${props.fromLabel} → ${props.toLabel}`
  }
  if (props.fromRevision && props.toRevision) {
    return `#${props.fromRevision.revisionNumber} → #${props.toRevision.revisionNumber}`
  }
  return ''
})

const fromId = computed(
  () => props.fromRevision?.id ?? props.fromRevisionId ?? null
)
const toId = computed(() => props.toRevision?.id ?? props.toRevisionId ?? null)

async function loadCompare() {
  if (!props.projectId || !fromId.value || !toId.value) {
    compare.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    compare.value = await TimelineRevisionAPI.compare(
      props.projectId,
      fromId.value,
      toId.value
    )
    if (compare.value) {
      applyPatchPathHighlights(
        compare.value.patchPaths ?? [],
        compare.value.entityChanges ?? [],
        timelineStore,
        props.internalTimelineJson ?? null
      )
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '对比失败'
    compare.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, fromId.value, toId.value] as const,
  ([open]) => {
    if (open) {
      void loadCompare()
    } else {
      timelineStore.clearPatchHighlightClipIds()
    }
  }
)

function actionLabel(action: string): string {
  if (action === 'added') return '+'
  if (action === 'removed') return '−'
  return '~'
}

function exportCompareJson() {
  if (!compare.value) {
    return
  }
  const blob = new Blob([JSON.stringify(compare.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const fromNum = compare.value.fromRevision.revisionNumber
  const toNum = compare.value.toRevision.revisionNumber
  a.href = url
  a.download = `timeline-compare-${fromNum}-to-${toNum}.json`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4"
    @click.self="emit('close')"
  >
    <div
      class="w-full max-w-2xl max-h-[85vh] overflow-hidden flex flex-col rounded-xl border border-default bg-bg-elevated shadow-xl"
      role="dialog"
      aria-labelledby="revision-compare-title"
    >
      <header class="flex items-center justify-between border-b border-default px-4 py-3">
        <h2 id="revision-compare-title" class="text-sm font-semibold text-text-primary">
          修订对比
          <span v-if="titleSuffix" class="text-text-muted font-normal ml-1">{{ titleSuffix }}</span>
        </h2>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="theme-btn theme-btn-ghost theme-btn-sm"
            :disabled="!compare"
            @click="exportCompareJson"
          >
            导出 JSON
          </button>
          <button type="button" class="theme-btn theme-btn-ghost theme-btn-sm" @click="emit('close')">
            关闭
          </button>
        </div>
      </header>

      <div class="flex-1 overflow-y-auto p-4 text-sm theme-scrollbar">
        <p v-if="loading" class="text-text-muted text-xs">加载中…</p>
        <p v-else-if="error" class="text-danger-500 text-xs">{{ error }}</p>
        <template v-else-if="compare">
          <div class="grid grid-cols-2 gap-3 mb-4 text-xs">
            <div class="rounded-lg border border-default p-2">
              <div class="text-text-muted mb-1">
                {{ fromLabel || `From #${compare.fromRevision.revisionNumber}` }}
              </div>
              <div>{{ compare.fromRevision.source }}</div>
            </div>
            <div class="rounded-lg border border-default p-2">
              <div class="text-text-muted mb-1">
                {{ toLabel || `To #${compare.toRevision.revisionNumber}` }}
              </div>
              <div>{{ compare.toRevision.source }}</div>
              <div v-if="compare.patchOpCount" class="text-primary-400">
                {{ compare.patchOpCount }} 项 patch
              </div>
            </div>
          </div>

          <div
            v-if="compare.summary.supported"
            class="mb-3 text-xs text-text-secondary"
          >
            轨道 +{{ compare.summary.tracksAdded }}/−{{ compare.summary.tracksRemoved }}/~{{
              compare.summary.tracksModified
            }}
            · 片段 +{{ compare.summary.clipsAdded }}/−{{ compare.summary.clipsRemoved }}/~{{
              compare.summary.clipsModified
            }}
          </div>

          <div
            v-if="compare.patchPaths?.length"
            class="mb-4"
          >
            <h4 class="text-xs font-medium text-text-secondary mb-2">RFC6902 路径</h4>
            <ul class="space-y-0.5 font-mono text-[11px] max-h-32 overflow-y-auto theme-scrollbar">
              <li
                v-for="(p, i) in compare.patchPaths"
                :key="`${p.op}-${p.path}-${i}`"
              >
                <span class="text-primary-400">{{ p.op }}</span>
                <span class="text-text-muted ml-1">{{ p.path }}</span>
              </li>
            </ul>
          </div>

          <h4
            v-if="compare.entityChanges.length"
            class="text-xs font-medium text-text-secondary mb-2"
          >
            实体变更
          </h4>
          <ul v-if="compare.entityChanges.length" class="space-y-1 font-mono text-[11px]">
            <li
              v-for="(c, i) in compare.entityChanges"
              :key="`${c.kind}-${c.entityId}-${i}`"
              class="flex gap-2 border-b border-default/50 py-1"
            >
              <span
                class="w-4 shrink-0"
                :class="{
                  'text-success-500': c.action === 'added',
                  'text-danger-500': c.action === 'removed',
                  'text-warning-500': c.action === 'modified',
                }"
              >{{ actionLabel(c.action) }}</span>
              <span class="text-text-muted w-12 shrink-0">{{ c.kind }}</span>
              <span class="truncate text-text-primary">{{ c.entityId }}</span>
            </li>
          </ul>
          <p v-else class="text-text-muted text-xs">无实体级结构变更</p>
        </template>
      </div>
    </div>
  </div>
</template>
