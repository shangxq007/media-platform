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
  <div class="p-2 rounded bg-gray-800/50 border border-gray-700 space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-xs text-gray-400 font-medium">Render Job</span>
      <StatusBadge :variant="statusVariant" :label="status" size="sm" dot />
    </div>

    <div v-if="jobId" class="flex items-center gap-1">
      <code class="text-[10px] text-gray-300 font-mono bg-gray-900/50 px-1.5 py-0.5 rounded flex-1 truncate">
        {{ jobId }}
      </code>
      <button
        class="text-[10px] text-blue-400 hover:text-blue-300 px-1"
        title="Copy job ID"
        @click="copyJobId"
      >
        📋
      </button>
    </div>

    <div v-if="isRunningOrQueued" class="space-y-1">
      <div class="w-full bg-gray-700 rounded-full h-1.5">
        <div
          class="h-1.5 rounded-full transition-all duration-300"
          :class="status === 'running' ? 'bg-info-500' : 'bg-warning-500'"
          :style="{ width: `${progress}%` }"
        />
      </div>
      <div class="flex justify-between text-[10px] text-gray-500">
        <span>{{ status === 'running' ? 'Rendering...' : 'In queue' }}</span>
        <span>{{ progress }}%</span>
      </div>
    </div>

    <div v-if="isFailed" class="p-1.5 rounded bg-red-900/20 border border-red-800/50">
      <div class="text-[10px] text-red-400 font-medium">Render Failed</div>
      <div v-if="errorCode" class="text-[10px] text-red-300/80 font-mono mt-0.5">{{ errorCode }}</div>
      <div v-if="error" class="text-[10px] text-red-300/70 mt-0.5">{{ error }}</div>
    </div>

    <div v-if="diagnosticInfo" class="flex items-center gap-1">
      <button
        class="text-[10px] text-gray-400 hover:text-gray-300 underline"
        @click="copyDiagnostic"
      >
        {{ copied ? 'Copied!' : 'Copy diagnostic info' }}
      </button>
    </div>

    <div class="flex gap-1.5 pt-1 border-t border-gray-700">
      <button
        v-if="isFailed"
        class="flex-1 px-2 py-1 text-[10px] bg-blue-600/20 text-blue-400 rounded hover:bg-blue-600/30"
        @click="$emit('retry')"
      >
        🔄 Retry
      </button>
      <button
        v-if="isRunningOrQueued"
        class="flex-1 px-2 py-1 text-[10px] bg-red-600/20 text-red-400 rounded hover:bg-red-600/30"
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
