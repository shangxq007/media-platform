<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PromptAPI } from '@/api/prompt'
import type { PromptExecutionRun } from '@/types'

const executions = ref<PromptExecutionRun[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedExecution = ref<PromptExecutionRun | null>(null)

const statusColors: Record<string, string> = {
  PENDING: 'bg-surface-4',
  RUNNING: 'bg-blue-500',
  SUCCEEDED: 'bg-green-500',
  FAILED: 'bg-red-500',
  CANCELLED: 'bg-yellow-500',
  REQUIRE_REVIEW: 'bg-orange-500'
}

onMounted(loadExecutions)

async function loadExecutions() {
  loading.value = true
  try {
    executions.value = await PromptAPI.listExecutions()
  } catch (e: any) {
    error.value = e.message || 'Failed to load executions'
  } finally {
    loading.value = false
  }
}

function selectExecution(execution: PromptExecutionRun) {
  selectedExecution.value = execution
}
</script>

<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between p-3 border-b border-border-subtle">
      <h2 class="text-lg font-semibold text-white">Executions</h2>
      <button class="text-xs text-info hover:text-info" @click="loadExecutions">Refresh</button>
    </div>

    <div v-if="error" class="p-3 text-danger text-sm">{{ error }}</div>
    <div v-if="loading" class="p-3 text-text-secondary text-sm">Loading...</div>

    <div class="flex-1 flex overflow-hidden">
      <!-- List -->
      <div class="w-1/2 border-r border-border-subtle overflow-y-auto">
        <div v-if="!loading && executions.length === 0" class="p-3 text-text-tertiary text-sm">
          No executions found
        </div>
        <div v-for="exec in executions" :key="exec.executionId"
          class="p-3 border-b border-border-subtle hover:bg-surface-2/50 cursor-pointer"
          :class="selectedExecution?.executionId === exec.executionId ? 'bg-surface-2' : ''"
          @click="selectExecution(exec)">
          <div class="flex items-center justify-between">
            <span class="text-white text-sm font-mono">{{ exec.executionId?.slice(0, 12) }}</span>
            <span class="px-1.5 py-0.5 rounded text-[10px]"
              :class="statusColors[exec.status] || 'bg-surface-4'">
              {{ exec.status }}
            </span>
          </div>
          <div class="text-text-secondary text-xs mt-1">
            {{ exec.modelProvider }} / {{ exec.modelName }}
          </div>
          <div class="text-text-tertiary text-xs">
            Tokens: {{ exec.tokenEstimate }} | Cost: ${{ exec.costEstimate?.toFixed(4) }}
          </div>
        </div>
      </div>

      <!-- Detail -->
      <div class="w-1/2 overflow-y-auto p-3">
        <div v-if="!selectedExecution" class="text-text-tertiary text-sm">
          Select an execution to view details
        </div>
        <div v-else class="space-y-3">
          <div>
            <span class="text-xs text-text-secondary">Execution ID</span>
            <div class="text-white text-sm font-mono">{{ selectedExecution.executionId }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Template</span>
            <div class="text-white text-sm">{{ selectedExecution.templateId }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Version</span>
            <div class="text-white text-sm">{{ selectedExecution.promptVersion }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Provider / Model</span>
            <div class="text-white text-sm">{{ selectedExecution.modelProvider }} / {{ selectedExecution.modelName }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Status</span>
            <div><span class="px-1.5 py-0.5 rounded text-[10px]"
              :class="statusColors[selectedExecution.status]">{{ selectedExecution.status }}</span></div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Risk Level</span>
            <div class="text-white text-sm">{{ selectedExecution.riskLevel }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Token Estimate</span>
            <div class="text-white text-sm">{{ selectedExecution.tokenEstimate }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Cost Estimate</span>
            <div class="text-white text-sm">${{ selectedExecution.costEstimate?.toFixed(4) }}</div>
          </div>
          <div>
            <span class="text-xs text-text-secondary">Started</span>
            <div class="text-white text-sm">{{ selectedExecution.startedAt }}</div>
          </div>
          <div v-if="selectedExecution.finishedAt">
            <span class="text-xs text-text-secondary">Finished</span>
            <div class="text-white text-sm">{{ selectedExecution.finishedAt }}</div>
          </div>
          <div v-if="selectedExecution.errorCode">
            <span class="text-xs text-text-secondary">Error</span>
            <div class="text-danger text-sm">{{ selectedExecution.errorCode }}: {{ selectedExecution.errorDetailsJson }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
