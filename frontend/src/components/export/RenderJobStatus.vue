<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { ref } from 'vue'

const props = defineProps<{
  jobId: string | null
  status: string
  progress: number
  error: string | null
  errorCode?: string
  diagnosticInfo?: string
}>()

const emit = defineEmits<{
  retry: []
  cancel: []
  copyDiagnostic: []
}>()

const showCancelConfirm = ref(false)
const copied = ref(false)

const statusVariant = computed(() => {
  switch (props.status) {
    case 'completed': return 'success'
    case 'failed': return 'danger'
    case 'running': return 'info'
    case 'queued': return 'warning'
    case 'cancelled': return 'neutral'
    default: return 'neutral'
  }
})

const isRunningOrQueued = computed(() =>
  props.status === 'queued' || props.status === 'running',
)

const isFailed = computed(() => props.status === 'failed')

function copyJobId() {
  if (props.jobId) {
    navigator.clipboard.writeText(props.jobId)
  }
}

function copyDiagnostic() {
  if (props.diagnosticInfo) {
    navigator.clipboard.writeText(props.diagnosticInfo)
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
  emit('copyDiagnostic')
}

function handleCancel() {
  showCancelConfirm.value = true
}

function confirmCancel() {
  showCancelConfirm.value = false
  emit('cancel')
}
</script>

<template>
  <div class="p-2 rounded bg-surface-2/50 border border-border-subtle space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-xs text-text-secondary font-medium">Render Job</span>
      <StatusBadge :variant="statusVariant" :label="status" size="sm" dot />
    </div>

    <div v-if="jobId" class="flex items-center gap-1">
      <code class="text-[10px] text-text-primary font-mono bg-surface-0/50 px-1.5 py-0.5 rounded flex-1 truncate">
        {{ jobId }}
      </code>
      <button
        class="text-[10px] text-info hover:text-info px-1"
        title="Copy job ID"
        @click="copyJobId"
      >
        📋
      </button>
    </div>

    <div v-if="isRunningOrQueued" class="space-y-1">
      <div class="w-full bg-surface-3 rounded-full h-1.5">
        <div
          class="h-1.5 rounded-full transition-all duration-300"
          :class="status === 'running' ? 'bg-info-500' : 'bg-warning-500'"
          :style="{ width: `${progress}%` }"
        />
      </div>
      <div class="flex justify-between text-[10px] text-text-tertiary">
        <span>{{ status === 'running' ? 'Rendering...' : 'In queue' }}</span>
        <span>{{ progress }}%</span>
      </div>
    </div>

    <div v-if="isFailed" class="p-1.5 rounded bg-danger-muted border border-red-800/50">
      <div class="text-[10px] text-danger font-medium">Render Failed</div>
      <div v-if="errorCode" class="text-[10px] text-danger/80 font-mono mt-0.5">{{ errorCode }}</div>
      <div v-if="error" class="text-[10px] text-danger/70 mt-0.5">{{ error }}</div>
    </div>

    <div v-if="diagnosticInfo" class="flex items-center gap-1">
      <button
        class="text-[10px] text-text-secondary hover:text-text-primary underline"
        @click="copyDiagnostic"
      >
        {{ copied ? 'Copied!' : 'Copy diagnostic info' }}
      </button>
    </div>

    <div class="flex gap-1.5 pt-1 border-t border-border-subtle">
      <button
        v-if="isFailed"
        class="flex-1 px-2 py-1 text-[10px] bg-info-muted text-info rounded hover:bg-blue-600/30"
        @click="$emit('retry')"
      >
        🔄 Retry
      </button>
      <button
        v-if="isRunningOrQueued"
        class="flex-1 px-2 py-1 text-[10px] bg-danger-muted text-danger rounded hover:bg-red-600/30"
        @click="handleCancel"
      >
        ✕ Cancel
      </button>
    </div>

    <ConfirmDialog
      :open="showCancelConfirm"
      title="Cancel Render Job"
      description="Are you sure you want to cancel this render job? This action cannot be undone."
      confirm-label="Cancel Job"
      variant="warning"
      @confirm="confirmCancel"
      @cancel="showCancelConfirm = false"
    />
  </div>
</template>
