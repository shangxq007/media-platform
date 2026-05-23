<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ConflictResolution, PendingTimelineConflict } from '@/utils/timelineConflictMerge'
import TimelineRevisionCompareDialog from './TimelineRevisionCompareDialog.vue'
import { useTimelineStore } from '@/stores/timeline'

const timelineStore = useTimelineStore()

const props = defineProps<{
  open: boolean
  conflict: PendingTimelineConflict | null
  projectId?: string | null
}>()

const emit = defineEmits<{
  resolve: [strategy: ConflictResolution]
  dismiss: []
}>()

const AUTO_COMPARE_KEY = 'timeline-conflict-auto-compare'

const compareOpen = ref(false)
const skipAutoCompare = ref(localStorage.getItem(AUTO_COMPARE_KEY) === 'false')

const canCompareBaselineToHead = computed(() => {
  const c = props.conflict
  return !!(
    props.projectId &&
    c?.baselineRevisionId &&
    c?.headRevisionId &&
    c.baselineRevisionId !== c.headRevisionId
  )
})

watch(
  () => props.open && canCompareBaselineToHead.value && !skipAutoCompare.value,
  (shouldOpen) => {
    if (shouldOpen) {
      compareOpen.value = true
    }
  }
)

function toggleAutoCompare() {
  skipAutoCompare.value = !skipAutoCompare.value
  localStorage.setItem(AUTO_COMPARE_KEY, skipAutoCompare.value ? 'false' : 'true')
}

const compareFromLabel = computed(() => {
  const n = props.conflict?.baselineRevisionNumber
  return n != null ? `基准 #${n}` : '上次同步'
})

const compareToLabel = computed(() => {
  const n = props.conflict?.headRevisionNumber ?? props.conflict?.serverRevision
  return n != null ? `HEAD #${n}` : '服务端 HEAD'
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open && conflict" class="c-dialog-overlay z-50" @click.self="emit('dismiss')">
        <div class="c-dialog max-w-lg">
          <div class="c-dialog-header">
            <h3 class="text-lg font-semibold text-text-primary">时间线同步冲突</h3>
          </div>
          <div class="c-dialog-body space-y-3">
            <p class="text-sm text-text-secondary">
              本地编辑与服务端 HEAD 不一致。可先查看「基准 → HEAD」差异，再选择处理方式。
            </p>
            <dl class="text-xs text-text-muted grid grid-cols-2 gap-x-4 gap-y-1 font-mono">
              <dt>服务端 HEAD</dt>
              <dd>
                #{{ conflict.headRevisionNumber ?? conflict.serverRevision }}
                <span v-if="conflict.headRevisionId" class="text-[10px] block truncate">
                  {{ conflict.headRevisionId }}
                </span>
              </dd>
              <dt>上次同步基准</dt>
              <dd>
                <template v-if="conflict.baselineRevisionNumber != null">
                  #{{ conflict.baselineRevisionNumber }}
                </template>
                <template v-else>（无修订记录，仅内容哈希）</template>
              </dd>
              <dt>本地 轨道 / 片段</dt>
              <dd>{{ conflict.localTrackCount }} / {{ conflict.localClipCount }}</dd>
              <dt>服务端 轨道 / 片段</dt>
              <dd>{{ conflict.serverTrackCount }} / {{ conflict.serverClipCount }}</dd>
            </dl>
            <div class="flex flex-col gap-2">
              <button
                v-if="canCompareBaselineToHead"
                type="button"
                class="theme-btn theme-btn-ghost theme-btn-sm w-full"
                @click="compareOpen = true"
              >
                对比基准与 HEAD（服务端自上次同步以来的变更）
              </button>
              <label
                v-if="canCompareBaselineToHead"
                class="flex items-center gap-2 text-[10px] text-text-muted cursor-pointer"
              >
                <input
                  type="checkbox"
                  class="rounded border-default"
                  :checked="!skipAutoCompare"
                  @change="toggleAutoCompare"
                />
                冲突时自动打开对比
              </label>
              <button
                v-if="timelineStore.patchHighlightClipIds.length"
                type="button"
                class="theme-btn theme-btn-secondary theme-btn-sm w-full"
                @click="timelineStore.focusFirstPatchHighlightClip()"
              >
                定位首个高亮片段（{{ timelineStore.patchHighlightClipIds.length }}）
              </button>
            </div>
          </div>
          <div class="c-dialog-footer flex-wrap gap-2">
            <button type="button" class="theme-btn theme-btn-secondary" @click="emit('dismiss')">
              稍后处理
            </button>
            <button type="button" class="theme-btn theme-btn-ghost" @click="emit('resolve', 'keep-local')">
              保留本地
            </button>
            <button type="button" class="theme-btn theme-btn-secondary" @click="emit('resolve', 'use-server')">
              使用服务端
            </button>
            <button type="button" class="theme-btn theme-btn-primary" @click="emit('resolve', 'merge')">
              智能合并
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <TimelineRevisionCompareDialog
    :open="compareOpen"
    :project-id="projectId"
    :from-revision-id="conflict?.baselineRevisionId"
    :to-revision-id="conflict?.headRevisionId"
    :from-label="compareFromLabel"
    :to-label="compareToLabel"
    :internal-timeline-json="conflict?.serverInternalTimelineJson"
    @close="compareOpen = false"
  />
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-fast);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
