<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useTimelineStore } from '@/stores/timeline'
import { buildHighlightLabels } from '@/utils/timelinePatchHighlight'

const timelineStore = useTimelineStore()

const items = computed(() =>
  buildHighlightLabels(
    timelineStore.patchHighlightClipIds,
    timelineStore.state.tracks,
    timelineStore.clips
  )
)

const currentLabel = computed(() => {
  const idx = timelineStore.patchHighlightIndex
  return items.value[idx]?.label ?? '—'
})

function focusAt(index: number) {
  timelineStore.focusHighlightAtIndex(index)
}

function onKeydown(e: KeyboardEvent) {
  if (items.value.length < 2) {
    return
  }
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
    return
  }
  if (e.key === '[' || e.key === 'ArrowLeft') {
    e.preventDefault()
    timelineStore.prevPatchHighlight()
  } else if (e.key === ']' || e.key === 'ArrowRight') {
    e.preventDefault()
    timelineStore.nextPatchHighlight()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div
    v-if="items.length > 0"
    class="flex flex-wrap items-center gap-2 rounded-lg border border-amber-600/40 bg-amber-950/30 px-2 py-1.5 text-xs"
  >
    <span class="text-amber-200/90 shrink-0" title="快捷键 [ / ] 或 ← / →">高亮片段</span>
    <button
      type="button"
      class="theme-btn theme-btn-ghost theme-btn-sm px-1.5 min-w-0"
      title="上一个"
      :disabled="items.length < 2"
      @click="timelineStore.prevPatchHighlight()"
    >
      ‹
    </button>
    <button
      type="button"
      class="theme-btn theme-btn-ghost theme-btn-sm flex-1 min-w-0 truncate text-left font-normal"
      :title="currentLabel"
      @click="timelineStore.focusTrackClip(items[timelineStore.patchHighlightIndex]?.trackClipId ?? '')"
    >
      {{ timelineStore.patchHighlightIndex + 1 }}/{{ items.length }} · {{ currentLabel }}
    </button>
    <button
      type="button"
      class="theme-btn theme-btn-ghost theme-btn-sm px-1.5 min-w-0"
      title="下一个"
      :disabled="items.length < 2"
      @click="timelineStore.nextPatchHighlight()"
    >
      ›
    </button>
    <ul
      v-if="items.length <= 8"
      class="w-full flex flex-wrap gap-1 mt-1"
    >
      <li
        v-for="(item, i) in items"
        :key="item.trackClipId"
      >
        <button
          type="button"
          class="px-1.5 py-0.5 rounded text-[10px] border transition-colors"
          :class="i === timelineStore.patchHighlightIndex
            ? 'border-amber-400 bg-amber-900/50 text-amber-100'
            : 'border-default/50 text-text-muted hover:border-amber-600/50'"
          @click="focusAt(i)"
        >
          {{ item.label }}
        </button>
      </li>
    </ul>
  </div>
</template>
