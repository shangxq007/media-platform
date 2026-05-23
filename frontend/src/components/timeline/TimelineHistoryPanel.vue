<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  TimelineRevisionAPI,
  type TimelineEditSessionItem,
  type TimelinePatchPreviewResult,
  type TimelineRevisionAuthorFacet,
  type TimelineRevisionListItem,
} from '@/api/timelineRevision'
import TimelineRevisionCompareDialog from './TimelineRevisionCompareDialog.vue'
import TimelinePatchStepsDialog from './TimelinePatchStepsDialog.vue'
import { useTimelineSyncMetaStore } from '@/stores/timelineSyncMeta'
import { useTimelineStore } from '@/stores/timeline'
import { applyPatchPathHighlights, loadRevisionInternalJson } from '@/utils/timelinePatchHighlight'

const props = defineProps<{
  projectId: string | null | undefined
}>()

const metaStore = useTimelineSyncMetaStore()
const timelineStore = useTimelineStore()

const emit = defineEmits<{
  restored: [payload: { editorTimelineJson: string }]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const revisions = ref<TimelineRevisionListItem[]>([])
const editSessions = ref<TimelineEditSessionItem[]>([])
const selectedSessionId = ref<string>('')
const selectedSource = ref<string>('')
const selectedAuthorId = ref<string>('')
const onlyMine = ref(false)
const facetSources = ref<string[]>([])
const facetAuthors = ref<TimelineRevisionAuthorFacet[]>([])
const editingNoteId = ref<string | null>(null)
const noteDraft = ref('')
const labelsDraft = ref('')
const savingNoteId = ref<string | null>(null)

const KNOWN_SOURCES = [
  'sync',
  'ai-sync',
  'push',
  'snapshot',
  'ai-adopt',
  'rollback',
  'conflict-keep-local',
  'conflict-merge',
  'backfill',
] as const

const sourceOptions = computed(() => {
  const merged = new Set<string>([...facetSources.value, ...KNOWN_SOURCES])
  return [...merged].sort()
})

function currentUserId(): string {
  return localStorage.getItem('user_id') || 'user-1'
}

function resolveAuthorFilter(): string | undefined {
  if (onlyMine.value) {
    return currentUserId()
  }
  return selectedAuthorId.value || undefined
}

function parseLabelsInput(raw: string): string[] {
  return raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
}
const restoringId = ref<string | null>(null)
const compareFrom = ref<TimelineRevisionListItem | null>(null)
const compareTo = ref<TimelineRevisionListItem | null>(null)
const compareOpen = ref(false)
const patchPreview = ref<TimelinePatchPreviewResult | null>(null)
const patchPreviewLoading = ref(false)
const patchStepsOpen = ref(false)
const patchStepsRevisionId = ref<string | null>(null)
const patchStepsRevisionNumber = ref<number | undefined>(undefined)

function formatSummary(item: TimelineRevisionListItem): string {
  const s = item.changeSummary
  if (!s?.supported) {
    return '—'
  }
  const parts: string[] = []
  if (s.clipsAdded + s.clipsRemoved + s.clipsModified > 0) {
    parts.push(`clips +${s.clipsAdded}/-${s.clipsRemoved}/~${s.clipsModified}`)
  }
  if (s.tracksAdded + s.tracksRemoved + s.tracksModified > 0) {
    parts.push(`tracks +${s.tracksAdded}/-${s.tracksRemoved}/~${s.tracksModified}`)
  }
  if (item.patchOpCount > 0) {
    parts.push(`${item.patchOpCount} patch ops`)
  }
  return parts.length ? parts.join(' · ') : '无结构变更'
}

async function loadHistory() {
  if (!props.projectId) {
    revisions.value = []
    editSessions.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const [revs, sessions, facets] = await Promise.all([
      TimelineRevisionAPI.list(props.projectId, {
        limit: 40,
        editSessionId: selectedSessionId.value || undefined,
        source: selectedSource.value || undefined,
        authorUserId: resolveAuthorFilter(),
      }),
      TimelineRevisionAPI.listEditSessions(props.projectId, 20),
      TimelineRevisionAPI.facets(props.projectId),
    ])
    facetSources.value = facets?.sources ?? []
    facetAuthors.value = facets?.authors ?? []
    revisions.value = revs
    editSessions.value = sessions
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载修订历史失败'
    revisions.value = []
  } finally {
    loading.value = false
  }
}

async function restore(revisionId: string) {
  if (!props.projectId) {
    return
  }
  restoringId.value = revisionId
  error.value = null
  try {
    const result = await TimelineRevisionAPI.restore(props.projectId, revisionId)
    emit('restored', { editorTimelineJson: result.editorTimelineJson })
    await loadHistory()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Restore failed'
  } finally {
    restoringId.value = null
  }
}

function startCompare(item: TimelineRevisionListItem) {
  if (!compareFrom.value) {
    compareFrom.value = item
    return
  }
  compareTo.value = item
  compareOpen.value = true
}

function clearCompareSelection() {
  compareFrom.value = null
  compareTo.value = null
}

function isRevisionHighlighted(revisionId: string): boolean {
  return metaStore.highlightedRevisionIds.includes(revisionId)
}

function startEditNote(item: TimelineRevisionListItem) {
  editingNoteId.value = item.id
  noteDraft.value = item.message ?? ''
  labelsDraft.value = (item.labels ?? []).join(', ')
}

function cancelEditNote() {
  editingNoteId.value = null
  noteDraft.value = ''
  labelsDraft.value = ''
}

function exportHistoryJson() {
  if (!props.projectId || revisions.value.length === 0) {
    return
  }
  const payload = {
    projectId: props.projectId,
    exportedAt: new Date().toISOString(),
    filters: {
      source: selectedSource.value || null,
      authorUserId: resolveAuthorFilter() ?? null,
      editSessionId: selectedSessionId.value || null,
    },
    revisions: revisions.value,
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `timeline-history-${props.projectId}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function saveNote(revisionId: string) {
  if (!props.projectId) {
    return
  }
  savingNoteId.value = revisionId
  error.value = null
  try {
    const updated = await TimelineRevisionAPI.updateAnnotation(
      props.projectId,
      revisionId,
      noteDraft.value,
      parseLabelsInput(labelsDraft.value)
    )
    const idx = revisions.value.findIndex((r) => r.id === revisionId)
    if (idx >= 0) {
      revisions.value[idx] = updated
    }
    cancelEditNote()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '保存备注失败'
  } finally {
    savingNoteId.value = null
  }
}

function openPatchSteps(item: TimelineRevisionListItem) {
  patchStepsRevisionId.value = item.id
  patchStepsRevisionNumber.value = item.revisionNumber
  patchStepsOpen.value = true
}

async function previewPatch(revisionId: string) {
  if (!props.projectId) {
    return
  }
  patchPreviewLoading.value = true
  patchPreview.value = null
  error.value = null
  try {
    patchPreview.value = await TimelineRevisionAPI.patchPreview(props.projectId, revisionId)
    if (patchPreview.value?.patchPaths?.length) {
      applyPatchPathHighlights(patchPreview.value.patchPaths, [], timelineStore, null)
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Patch preview failed'
  } finally {
    patchPreviewLoading.value = false
  }
}

watch([selectedSessionId, selectedSource, selectedAuthorId, onlyMine], () => { void loadHistory() })
watch(onlyMine, (mine) => {
  if (mine) {
    selectedAuthorId.value = ''
  }
})
watch(() => props.projectId, () => { void loadHistory() }, { immediate: true })
</script>

<template>
  <div class="flex flex-col gap-3 text-sm">
    <div class="flex items-center justify-between">
      <h3 class="font-semibold text-text-primary">修订历史</h3>
      <div class="flex gap-1">
        <button
          type="button"
          class="theme-btn theme-btn-ghost theme-btn-sm"
          title="导出当前列表为 JSON"
          :disabled="!projectId || revisions.length === 0"
          @click="exportHistoryJson"
        >
          导出
        </button>
        <button
          type="button"
          class="theme-btn theme-btn-ghost theme-btn-sm"
          :disabled="loading || !projectId"
          @click="loadHistory"
        >
          刷新
        </button>
      </div>
    </div>

    <div v-if="projectId" class="flex flex-col gap-2">
      <div class="flex flex-col gap-1">
        <label class="text-[10px] text-text-muted uppercase tracking-wide">来源</label>
        <select v-model="selectedSource" class="theme-input text-xs">
          <option value="">全部来源</option>
          <option v-for="s in sourceOptions" :key="s" :value="s">
            {{ s }}
          </option>
        </select>
      </div>
      <div v-if="facetAuthors.length" class="flex flex-col gap-1">
        <label class="text-[10px] text-text-muted uppercase tracking-wide">作者</label>
        <select
          v-model="selectedAuthorId"
          class="theme-input text-xs"
          :disabled="onlyMine"
        >
          <option value="">全部作者</option>
          <option
            v-for="a in facetAuthors"
            :key="a.authorUserId"
            :value="a.authorUserId"
          >
            {{ a.authorUserId }} ({{ a.revisionCount }})
          </option>
        </select>
      </div>
      <label class="flex items-center gap-2 text-xs text-text-secondary cursor-pointer">
        <input v-model="onlyMine" type="checkbox" class="rounded border-default" />
        仅我的修订
      </label>
      <div v-if="editSessions.length" class="flex flex-col gap-1">
        <label class="text-[10px] text-text-muted uppercase tracking-wide">AI 改稿会话</label>
        <select
          v-model="selectedSessionId"
          class="theme-input text-xs"
        >
          <option value="">全部修订</option>
          <option
            v-for="s in editSessions"
            :key="s.editSessionId"
            :value="s.editSessionId"
          >
            {{ s.editSessionId }} ({{ s.revisionCount }})
          </option>
        </select>
      </div>
    </div>

    <p
      v-if="metaStore.highlightedRevisionIds.length"
      class="text-[10px] text-amber-400/90"
    >
      琥珀色边框 = 与当前同步冲突相关的修订（基准 / HEAD）
    </p>

    <p
      v-if="compareFrom && !compareOpen"
      class="text-xs text-primary-400"
    >
      已选 #{{ compareFrom.revisionNumber }}，再点另一条进行对比。
      <button type="button" class="underline ml-1" @click="clearCompareSelection">清除</button>
    </p>

    <p v-if="!projectId" class="text-text-muted text-xs">打开项目后可查看修订历史。</p>
    <p v-else-if="error" class="text-danger-500 text-xs">{{ error }}</p>
    <p v-else-if="loading" class="text-text-muted text-xs">加载中…</p>
    <ul v-else-if="revisions.length === 0" class="text-text-muted text-xs">尚无修订，保存时间线后将创建首条记录。</ul>
    <ul v-else class="space-y-2 max-h-80 overflow-y-auto theme-scrollbar">
      <li
        v-for="item in revisions"
        :key="item.id"
        class="border border-default rounded-lg p-2 bg-bg-base/50"
        :class="{
          'ring-1 ring-primary-500': compareFrom?.id === item.id,
          'ring-2 ring-amber-500/80 bg-amber-950/20': isRevisionHighlighted(item.id),
        }"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0 flex-1">
            <div class="font-mono text-xs text-primary-400">
              #{{ item.revisionNumber }}
              <span class="text-text-muted ml-1">{{ item.source }}</span>
            </div>
            <div v-if="item.editSessionId" class="text-[10px] text-text-muted truncate">
              session: {{ item.editSessionId }}
            </div>
            <div v-if="item.authorUserId" class="text-[10px] text-text-muted">
              {{ item.authorUserId }}
            </div>
            <div
              v-if="editingNoteId === item.id"
              class="mt-1 flex flex-col gap-1"
            >
              <input
                v-model="noteDraft"
                type="text"
                maxlength="512"
                class="theme-input text-xs"
                placeholder="修订备注（最多 512 字）"
                @keyup.enter="saveNote(item.id)"
              />
              <input
                v-model="labelsDraft"
                type="text"
                class="theme-input text-xs"
                placeholder="标签，逗号分隔（最多 8 个）"
                @keyup.enter="saveNote(item.id)"
              />
              <div class="flex gap-1">
                <button
                  type="button"
                  class="theme-btn theme-btn-primary theme-btn-sm"
                  :disabled="savingNoteId === item.id"
                  @click="saveNote(item.id)"
                >
                  {{ savingNoteId === item.id ? '…' : '保存' }}
                </button>
                <button
                  type="button"
                  class="theme-btn theme-btn-ghost theme-btn-sm"
                  @click="cancelEditNote"
                >
                  取消
                </button>
              </div>
            </div>
            <ul
              v-if="item.labels?.length && editingNoteId !== item.id"
              class="flex flex-wrap gap-1 mt-0.5"
            >
              <li
                v-for="label in item.labels"
                :key="label"
                class="text-[10px] px-1.5 py-0.5 rounded border border-primary-500/40 text-primary-300"
              >
                {{ label }}
              </li>
            </ul>
            <div
              v-if="editingNoteId !== item.id"
              class="text-xs text-text-secondary truncate flex items-center gap-1"
            >
              <span class="truncate">{{ item.message || '无备注' }}</span>
              <button
                type="button"
                class="text-[10px] text-primary-400 shrink-0 underline"
                @click="startEditNote(item)"
              >
                编辑
              </button>
            </div>
            <div class="text-[10px] text-text-muted mt-0.5">{{ formatSummary(item) }}</div>
            <div class="text-[10px] text-text-muted">{{ item.createdAt }}</div>
          </div>
          <div class="flex flex-col gap-1 flex-shrink-0">
            <button
              v-if="item.patchOpCount > 0"
              type="button"
              class="theme-btn theme-btn-ghost theme-btn-sm"
              title="一次性预览全部 patch"
              :disabled="patchPreviewLoading"
              @click="previewPatch(item.id)"
            >
              预览
            </button>
            <button
              v-if="item.patchOpCount > 0"
              type="button"
              class="theme-btn theme-btn-ghost theme-btn-sm"
              title="分步预览 patch"
              @click="openPatchSteps(item)"
            >
              分步
            </button>
            <button
              type="button"
              class="theme-btn theme-btn-ghost theme-btn-sm"
              title="与另一条修订对比"
              @click="startCompare(item)"
            >
              对比
            </button>
            <button
              type="button"
              class="theme-btn theme-btn-secondary theme-btn-sm"
              :disabled="restoringId !== null"
              @click="restore(item.id)"
            >
              {{ restoringId === item.id ? '…' : '恢复' }}
            </button>
          </div>
        </div>
      </li>
    </ul>

    <TimelineHighlightNavigator v-if="timelineStore.patchHighlightClipIds.length" class="mb-2" />

    <div
      v-if="patchPreview"
      class="rounded-lg border border-default p-2 text-xs space-y-1 bg-bg-base/40"
    >
      <div class="flex justify-between items-center">
        <span class="font-medium text-text-secondary">Patch 预览 #{{ patchPreview.revisionId.slice(0, 12) }}</span>
        <button type="button" class="text-text-muted hover:text-text-primary" @click="patchPreview = null">×</button>
      </div>
      <p v-if="!patchPreview.hasPatchOps" class="text-text-muted">无存储的 patch 操作</p>
      <template v-else>
        <p :class="patchPreview.success ? 'text-success-500' : 'text-danger-500'">
          {{ patchPreview.success ? 'Dry-run 成功' : 'Dry-run 失败' }}
          <span v-if="patchPreview.contentHashAfter === patchPreview.revisionContentHash" class="text-text-muted">
            · 哈希与修订一致
          </span>
        </p>
        <ul v-if="patchPreview.errors.length" class="text-danger-500">
          <li v-for="(err, i) in patchPreview.errors" :key="i">{{ err }}</li>
        </ul>
        <ul class="font-mono text-[10px] max-h-24 overflow-y-auto theme-scrollbar">
          <li v-for="(p, i) in patchPreview.patchPaths" :key="i">
            <span class="text-primary-400">{{ p.op }}</span> {{ p.path }}
          </li>
        </ul>
      </template>
    </div>

    <TimelineRevisionCompareDialog
      :open="compareOpen"
      :project-id="projectId"
      :from-revision="compareFrom"
      :to-revision="compareTo"
      @close="compareOpen = false; clearCompareSelection()"
    />

    <TimelinePatchStepsDialog
      :open="patchStepsOpen"
      :project-id="projectId"
      :revision-id="patchStepsRevisionId"
      :revision-number="patchStepsRevisionNumber"
      @close="patchStepsOpen = false"
    />
  </div>
</template>
