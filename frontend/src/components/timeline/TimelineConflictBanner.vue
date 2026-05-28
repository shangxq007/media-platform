<script setup lang="ts">
import type { PendingTimelineConflict } from '@/utils/timelineConflictMerge'
import TimelineHighlightNavigator from './TimelineHighlightNavigator.vue'

defineProps<{
  conflict: PendingTimelineConflict | null
}>()
</script>

<template>
  <div
    v-if="conflict"
    class="flex flex-col gap-2 px-4 py-2 bg-amber-950/40 border-b border-amber-600/40 text-xs"
    role="status"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <span class="text-amber-200/90">
        同步冲突：服务端 HEAD #{{ conflict.headRevisionNumber ?? conflict.serverRevision }}
        <template v-if="conflict.baselineRevisionNumber != null">
          · 基准 #{{ conflict.baselineRevisionNumber }}
        </template>
      </span>
    </div>
    <TimelineHighlightNavigator />
  </div>
</template>
