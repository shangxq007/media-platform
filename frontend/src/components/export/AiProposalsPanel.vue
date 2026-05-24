<script setup lang="ts">
import { ref, computed } from 'vue'
import { AiTimelineAPI, type AiProposalDto } from '@/api/ai-timeline'
import { getTenantId } from '@/utils/tenant'

const props = defineProps<{
  projectId: string
  timelineJson: string
  proposals: AiProposalDto[]
  editSessionId?: string
}>()

const emit = defineEmits<{
  updated: [timelineJson: string, proposals: AiProposalDto[]]
  error: [message: string]
}>()

const workingId = ref<string | null>(null)

const pending = computed(() =>
  props.proposals.filter(p => p.status === 'PENDING')
)

async function adopt(id: string) {
  workingId.value = id
  try {
    const res = await AiTimelineAPI.adoptProposal(
      getTenantId(),
      props.projectId,
      id,
      props.timelineJson,
      {
        editSessionId: props.editSessionId?.trim() || undefined,
        persistRevision: true,
      }
    )
    emit('updated', res.timelineJson, res.proposals ?? [])
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Adopt failed')
  } finally {
    workingId.value = null
  }
}

async function reject(id: string) {
  workingId.value = id
  try {
    const res = await AiTimelineAPI.rejectProposal(
      getTenantId(),
      props.projectId,
      id,
      props.timelineJson
    )
    emit('updated', res.timelineJson, res.proposals ?? [])
  } catch (e: unknown) {
    emit('error', e instanceof Error ? e.message : 'Reject failed')
  } finally {
    workingId.value = null
  }
}
</script>

<template>
  <div
    v-if="proposals.length"
    class="rounded-lg border border-amber-800/50 bg-amber-950/20 p-sm space-y-sm"
  >
    <span class="text-xs font-medium text-amber-200">AI 建议（人工确认）</span>
    <div
      v-for="p in proposals"
      :key="p.id"
      class="flex flex-col gap-xs border border-border-subtle/60 rounded p-xs text-xs"
    >
      <div class="flex justify-between items-start gap-sm">
        <div class="min-w-0">
          <span class="font-mono text-text-primary">{{ p.id }}</span>
          <span
            class="ml-1 px-1 rounded text-[10px]"
            :class="p.status === 'PENDING' ? 'bg-amber-900 text-amber-300' : p.status === 'ACCEPTED' ? 'bg-emerald-900 text-emerald-300' : 'bg-surface-2 text-text-secondary'"
          >{{ p.status }}</span>
          <p class="text-text-secondary mt-0.5 truncate">{{ p.summary || '（无摘要）' }}</p>
          <p class="text-[10px] text-text-tertiary">{{ p.operationCount }} 项 Patch · {{ p.createdAt }}</p>
        </div>
        <div
          v-if="p.status === 'PENDING'"
          class="flex gap-1 shrink-0"
        >
          <button
            type="button"
            class="rounded bg-emerald-700 hover:bg-emerald-600 px-2 py-0.5 text-[10px] disabled:opacity-50"
            :disabled="workingId !== null"
            @click="adopt(p.id)"
          >
            采纳
          </button>
          <button
            type="button"
            class="rounded border border-border-default px-2 py-0.5 text-[10px] disabled:opacity-50"
            :disabled="workingId !== null"
            @click="reject(p.id)"
          >
            拒绝
          </button>
        </div>
      </div>
    </div>
    <p
      v-if="pending.length === 0 && proposals.length"
      class="text-[10px] text-text-tertiary"
    >
      无待处理建议
    </p>
  </div>
</template>
